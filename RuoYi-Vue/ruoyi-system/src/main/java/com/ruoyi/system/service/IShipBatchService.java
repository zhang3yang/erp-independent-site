package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.ShipBatch;
import com.ruoyi.system.domain.vo.ShipBatchLogisticsInfo;

/**
 * 发货批次服务。
 *
 * <p><b>状态机</b>（括号里是该动作的责任岗位）：</p>
 * <pre>
 * 待确认 ──确认（业务员）──→ 已提交仓库 ──打印备货单（仓库）──→ 已打印备货单
 *                                                                      │
 *                                          上传照片 + 备货完成（仓库）  │
 *                                                                      ↓
 *   已打印快递单号 ←──打印快递单号（仓库）── 已录入快递单号 ←──录入快递单号（业务员）── 备货完成
 *         │
 *         └──完成（仓库）──→ 已完成      ← 库存只在这一格扣
 *
 * 中间那五格都能被仓库「驳回」，一律退回「待确认」。
 * 「待确认 / 已提交仓库 / 已打印备货单 / 备货完成」四格业务员还能「取消」。
 * </pre>
 *
 * <p><b>这条链上只有一个硬分界：点「完成」＝承运商把货取走。</b>在那之前货一直在仓库里
 * ——「已打印快递单号」也只表示面单贴上了、在等人上门，出库单还没开、库存一分没扣。
 * 扣减、回写订单已发量全部发生在「完成」那一步，所以那是唯一不可逆的一格。</p>
 *
 * <p><b>为什么中间要拆这么多格</b>：每一格换一次责任人，谁的活没干完一眼看得出来。
 * 仓库备完货要留照片，业务员看着照片才敢录快递单号；录单那一刻要卡客户的付款额度，
 * 而只有业务员知道该发多少；面单则必须由仓库打、贴在箱子上。把这几件事压成一步，
 * 出了岔子就只能靠人回忆是谁干的。</p>
 *
 * <p><b>驳回</b>是这条分界线的直接结果：装箱时装错货、面单贴错、客户临时改主意，
 * 全都发生在承运商来之前。这时候批次可能已经被推到「已打印快递单号」了，但货还在手上，
 * 拆箱重装就是了。所以中间五格都能驳回，一律退回「待确认」，交还给业务员改数量、
 * 改仓库、改计划日期，再重走一遍。</p>
 *
 * <p>取消到「备货完成」为止，订单侧办理。再往后要作废就先驳回再取消——两步而不是一步，
 * 是因为这两件事的责任方不同：驳回是仓库说「这单我发不了」，取消是业务员说「这单不发了」；
 * 而「已录入快递单号」之后单子上带的是业务员自己填的面单信息，仓库正按它打单。
 * 已完成的货已经出了门，撤不回来，只能走退货流程。</p>
 *
 * <p><b>完成这一步做三件事，全在同一个事务里</b>：</p>
 * <ol>
 *   <li>建销售出库单（CK）：锁定要出的库存行</li>
 *   <li>同事务审核出库单：扣物理库存、释放锁定、写批次与流水</li>
 *   <li>回写订单明细已发量，重算发货比例与履约状态</li>
 * </ol>
 *
 * <p>第 2 步为什么不留给库存模块单独审核：批次已经走了确认、打备货单、备货、录单、
 * 打面单五道人工关卡，再让出库单走一遍待审核是重复审核；而且 shippedqty 必须等审核过账
 * 才能加（对齐采购的 receivedqty 口径），拆成两步就得让订单模块去监听库存模块的审核事件。</p>
 */
public interface IShipBatchService
{
    List<ShipBatch> selectBatchList(ShipBatch query, String tableSuffix);

    /** 某张订单的全部发货批次，跨月表汇总——一张订单的批次可以落在不同月份。 */
    List<ShipBatch> selectBatchesByOrder(String shopCode, String orderCode);

    ShipBatch selectBatchDetail(String shopCode, String shipCode, String tableSuffix);

    /** 按批次号跨月查找。 */
    ShipBatch findBatchAcrossMonths(String shopCode, String shipCode);

    /**
     * 建发货批次。落在 shipdate 所在月表。
     *
     * <p>可排量 = 订单明细数量 - 已发量 - 其他未完成批次已排的量，超了直接拒——
     * 光看已发量会让同一批货被排进两张批次。</p>
     */
    ShipBatch createBatch(String shopCode, ShipBatch command, String username);

    int updatePending(String shopCode, ShipBatch command, String tableSuffix, String username);

    /** 确认并提交仓库：待确认 → 已提交仓库。提交之后订单侧只剩查看，改不了也取消不了。 */
    int confirmBatch(String shopCode, String shipCode, String tableSuffix, String username);

    /** 打印备货单：已提交仓库 → 已打印备货单。仓库拿到批次后的第一步，照着单子去拣货。 */
    int printPickList(String shopCode, String shipCode, String tableSuffix, String username);

    /**
     * 备货完成：已打印备货单 → 备货完成。<b>不动库存</b>，只落备货照片。
     *
     * <p>这一步是仓库和业务员的交接点：货拣好装箱了，库管员把备货现场拍下来。
     * 业务员在下一步（录快递单号）之前，能看到的唯一凭证就是这组照片。</p>
     *
     * <p><b>照片至少要有一张</b>，一张都没有就退回去重传。备货照片和产品照片业务上是
     * 同一件事——「货备成什么样」，所以不分类别、一个上传框收完；但一张都不传的话，
     * 这一步就成了空点一下按钮，业务员那边什么都看不到，这个环节也就白设了。</p>
     *
     * @param photos 逗号分隔的照片 URL，至少一张
     */
    int completePicking(String shopCode, String shipCode, String tableSuffix, String photos, String username);

    /**
     * 补传 / 替换备货照片，<b>不改状态</b>。只认「已打印备货单 / 备货完成」两格。
     *
     * <p>前者是还没点备货完成、照片先传着，后者是传漏了回来补。再往后业务员已经按着
     * 这组照片录了快递单号，照片就是凭证，不该还能动。</p>
     */
    int savePickPhotos(String shopCode, String shipCode, String tableSuffix, String photos, String username);

    /**
     * 「录入快递单号」弹窗要的数据：本批次各行的计划量与单价，以及这张订单的付款额度。
     *
     * <p>算已占额度要枚举发货批次月表，比普通详情贵，所以单开一个接口，只在开这个弹窗时调。</p>
     */
    ShipBatchLogisticsInfo loadLogisticsInfo(String shopCode, String shipCode, String tableSuffix);

    /**
     * 录入快递单号：备货完成 → 已录入快递单号。<b>不动库存</b>。
     *
     * <p>业务员这一步做三件事：填快递单号、填承运商、按仓库实际备好的货把每行的
     * 本次发货数量敲定下来。数量<b>只能往下调</b>——上限是建批次时排的计划量，
     * 往上加等于绕过建单时那道可排量校验；真要多发，作废重排或者另开一张批次。
     * 某一行填 0 表示这次不发它，该行从批次里删掉，它占的订单量随之放回去。</p>
     *
     * <p><b>这一步卡客户的付款额度</b>：按整单累计算，</p>
     * <pre>
     *   已完成批次的货值（＝Σ 订单明细已发量 × 单价）
     * + 其它已录入/已打印快递单号批次的货值
     * + 本批次这次要发的货值
     * ≤ 订单上客户已付的金额
     * </pre>
     * <p>超了直接拒，报错里把三个数都说清楚。为什么按整单累计而不是只看本批次：
     * 只看本批次的话，一张 30000 的单收了 20000，两张各 15000 的批次会各自通过，
     * 合计 30000 全发出去，客户实际只付了三分之二。</p>
     *
     * <p>「其它批次」只数已录入/已打印快递单号那两格，<b>不数还没录单的</b>：额度是谁先录
     * 谁先占，把还躺在「已提交仓库」的兄弟批次也算进来，第一个录单的人就会被自己
     * 排的其它批次挤爆。</p>
     *
     * <p><b>承运商与快递单号都必填</b>，来源订单是「上门提货」的除外——客户自己开车来仓库拉，
     * 本来就没有承运商和面单，硬要求只会逼人往框里敲「无」，而假数据比空值更糟。
     * 校验按<b>落库后的结果</b>判：建批次时已经填好的，弹窗原样带出来照样算数。</p>
     *
     * @param qtyByDetailCode 批次明细代码 → 本次发货数量；传 null 表示各行按原计划量发
     */
    int enterLogistics(String shopCode, String shipCode, String tableSuffix, String logisticsNo,
            String carrier, Map<String, BigDecimal> qtyByDetailCode, String username);

    /**
     * 打印快递单号：已录入快递单号 → 已打印快递单号。仓库把面单打出来贴在箱子上。
     * <b>仍然不动库存</b>，货还在仓库里等承运商。
     */
    int printExpressLabel(String shopCode, String shipCode, String tableSuffix, String username);

    /**
     * 驳回：中间五格（已提交仓库 / 已打印备货单 / 备货完成 / 已录入快递单号 /
     * 已打印快递单号）→ 待确认。货还没被承运商取走就都能驳。
     *
     * <p>退回「待确认」而不是停在原地，是为了让业务员能真的改东西——批次的修改入口
     * （{@link #updatePending}）只认「待确认」。驳回后这张批次从仓库发货管理的列表里
     * 消失，回到「订单管理 → 发货批次」等业务员处理。</p>
     *
     * <p>确认人、备货人、录单人、发货人和实际发货时间会被抹掉，<b>备货照片也一并清掉</b>
     * ——驳回的典型场面就是货备错了要重拣，那组照片拍的正是要被推翻的那一次备货。
     * 承运商与快递单号保留（面单多半还能用，要改在订单侧改）。批次占的订单量不变
     * ——「待确认」本来就算在已排量里，所以驳回不会让这批量凭空冒出来又被别人排走。</p>
     *
     * <p>与 {@link #confirmBatch} 等动作不同，<b>这里不校验订单状态</b>：驳回是把单子往回撤，
     * 任何情况下都该放得动，理由同 {@link #cancelBatch}。</p>
     *
     * @param reason 驳回原因，必填——业务员得知道为什么退回来才改得动
     */
    int rejectBatch(String shopCode, String shipCode, String tableSuffix, String reason, String username);

    /**
     * 完成：已打印快递单号 → 已完成。<b>库存在这一步才真正扣，对应现实中承运商把货取走那一刻。</b>
     * 开销售出库单并同事务审核过账，回写订单已发量与进度。
     *
     * <p>序列号管理的商品必须在这一步指定序列号，个别计价的批次管理商品必须指定出库批次——
     * 这两件事是销售出库单过账的硬要求（见 OutboundServiceImpl.validateTrackingData）。
     * 批次单据本身不存这些信息：建批次时货还没备好，发哪几件要到真正出库那一刻才定得下来，
     * 与生产完工录产出序列号是同一个口径。</p>
     *
     * @param serials    序列号，key 为发货批次明细代码；没有序列号商品时传 null
     * @param batchCodes 出库批次号，key 同上；只有「批次管理 + 个别计价」的商品要传
     * @return 生成的销售出库单号
     */
    String completeBatch(String shopCode, String shipCode, String tableSuffix,
            Map<String, List<String>> serials, Map<String, String> batchCodes, String username);

    /**
     * 取消批次，订单侧办理，到「备货完成」为止。再往后的先 {@link #rejectBatch 驳回} 再取消；
     * 已完成的货已经出门，撤不回来，只能走退货流程。
     */
    int cancelBatch(String shopCode, String shipCode, String tableSuffix, String username);
}
