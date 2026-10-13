package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.ProductionOrder;
import com.ruoyi.system.domain.SalesOrder;
import com.ruoyi.system.domain.SalesOrderItem;
import com.ruoyi.system.domain.SalesOrderProductionCandidate;

/**
 * 销售订单服务。
 *
 * <p><b>履约状态机</b>（{@code orderstate}，与支付状态分开两列）：</p>
 * <pre>
 * 草稿 ──提交──→ 待审核 ──审核通过──┬── 货都有着落 ──→ 已备货 ──┐
 *   ↑              │                  └── 还差货 ──────→ 备货中 ──┤（货补上后回已备货）
 *   │              └──驳回（填原因）──→ 已驳回 ──改单──→ 重新提交 │
 *   │                                                              ├──首次发货──→ 发货中
 *   └──────撤审（未发货 + 无在途批次 + 无在制工单）────────────────┤
 *                                                                  └──发完──────→ 已完成
 * 终止两条：
 *   取消（一件未发）──→ 已取消
 *   关闭（发过货但剩下不发了，填原因）──→ 已关闭
 * 删除：仅草稿
 * </pre>
 *
 * <p><b>「有没有着落」分两路判</b>，见 {@code SalesOrderServiceImpl.isAwaitingSupply}：</p>
 * <ul>
 *   <li><b>定制件</b>——只认为它专门做出来的<b>专属批次</b>，通用库存有多少都不算数；</li>
 *   <li><b>普通件</b>——查该商品全部仓库的可用库存合计，够本行未发量才算有着落。</li>
 * </ul>
 * <p>只要有一条明细没着落，整单就是备货中。</p>
 *
 * <p><b>整条链一件货都不占。</b>所以「已备货」只是审核那一刻的快照，不是承诺——
 * 查到够也留不住，那批货随时可能被别的单先发走。库存到发货那一刻才由出库单锁定扣减，
 * 谁先发货谁拿到。定制件例外：它由生产模块在完工入库时把批次绑到订单明细上，
 * 别的订单挑不到。</p>
 *
 * <p><b>已取消与已关闭的分界是「有没有发出去过货」</b>：一件未发用取消，
 * 发过就只能关闭。已发的部分是既成事实，要退得走销售退货
 * （{@link ISalesReturnService}），关闭只终止剩余部分、不改已发数量。</p>
 *
 * <p><b>支付状态</b>（{@code paystatus}）与<b>回款状态</b>（{@code receivestatus}）
 * 同源，都由 paidamount 与 totalamount 派生。留两列是因为订单设计文档要前者与采购对齐，
 * V2.0 §5.4 流程2 要求财务回款回写后者。同源派生避免双写漂移。</p>
 */
public interface ISalesOrderService
{
    List<SalesOrder> selectOrderList(SalesOrder query, String tableSuffix);

    /**
     * 可发货订单，供发货批次页建单时选单。
     *
     * <p>只列还有量能排的：处于已备货/备货中/发货中，且至少有一条明细
     * {@code qty - shippedqty - 已排进未发货批次的量 > 0}。已发完的订单留在下拉里
     * 只会让业务员看到一排 0。</p>
     *
     * <p><b>只查指定月份</b>：订单按 createtime 分月表，跨全部月份扫一遍代价太高。
     * 界面上给月份选择器，默认当月。</p>
     */
    List<SalesOrder> selectShippableOrders(String shopCode, String tableSuffix);

    /**
     * 可回款订单，供财务认领回款流水时选单（V2.0 §5.4 流程2）。
     *
     * <p>列的是还没收满的：已收金额 &lt; 订单总额，且不是草稿或已取消。
     * 已收满的留在下拉里，业务员点进去只会把订单绑成超额收款。</p>
     *
     * <p>订单按 createtime 分月表。给了 tableSuffix 就只查那个月，<b>不给表示全部月份</b>——
     * 回款常常跨月（8 月下单 9 月到账），默认按当月过滤会让业务员在下拉里找不到欠款单。</p>
     */
    List<SalesOrder> selectReceivableOrders(String shopCode, String tableSuffix);

    /**
     * 可退款订单，供财务建销售退款流水时选单。
     *
     * <p>与 {@link #selectReceivableOrders} 是<b>相反</b>的集合：那边列还没收满的，
     * 这边列已经收过钱的（已收金额 &gt; 0）。已取消的订单也在内——取消后退款是常态。
     * tableSuffix 的语义同上：给了只查那个月，不给查全部月份。</p>
     */
    List<SalesOrder> selectRefundableOrders(String shopCode, String tableSuffix);

    /**
     * 可排产的订单明细，供生产按订单建工单时选单（V2.0 §4.4 工单来源 order）。
     *
     * <p>列的是<b>还没发完</b>的明细（{@code qty > 已发}），不分备货来源。挑哪些行做是
     * 计划员在选单界面上的决定，系统只负责把「还欠客户货」的行摆出来。</p>
     *
     * <p>不按 stock_source 筛：订单页已经不展示、也不再能登记备货来源，那个字段现在只由
     * 建单时的「专门生产」推出来，拿它当门槛等于只有定制件排得了产。</p>
     *
     * <p>也不看「库存里有没有现货」：订单不占库存，现货随时可能被别的单发走，
     * 拿它当排产门槛只会漏排。</p>
     *
     * <p>订单按 createtime 分月表。给了 tableSuffix 就只查那个月，<b>不给表示全部月份</b>——
     * 上月下的单这月才排产是常态，默认当月会让计划员在选单界面里找不到要做的活。</p>
     *
     * @param goodsCode 传了就只看这个商品，用于「这个产出还欠多少」
     */
    List<SalesOrderProductionCandidate> selectProductionCandidates(String shopCode, String tableSuffix, String goodsCode);

    /**
     * 这张销售订单关联的全部生产工单，不分状态，按建单时间倒序。供订单详情页追溯。
     *
     * <p>按工单的 {@code orderallocation}/{@code sourceordercodes} 反查，不按订单明细上的
     * {@code sourcecode}——那个字段是单值的：一条明细先后被两张工单做只留最后一张，
     * 关闭工单时还会被主动清空，拿它追溯会把历史工单整段丢掉。</p>
     */
    List<ProductionOrder> selectProductionsByOrder(String shopCode, String orderCode);

    /**
     * 按明细代码取一条订单明细并<b>锁行</b>，缺口与未发量都现算。
     *
     * <p>生产建工单校验分配量时用（上限是未发量）。加锁是必需的：两张工单并发分配同一条
     * 明细时，不锁就会各自读到同一个未发量、各自校验通过，合起来超分。</p>
     *
     * @return 明细不存在时返回 null
     */
    SalesOrderItem lockOrderItem(String shopCode, String tableSuffix, String detailCode);

    /** 按单号跨月查找。前端不带月份时用，命中后 tableSuffix 回填在返回对象上。 */
    SalesOrder findOrderAcrossMonths(String shopCode, String orderCode);

    SalesOrder selectOrderDetail(String shopCode, String orderCode, String tableSuffix);

    /** 建单。落在 createtime 所在月表，状态为草稿，不预占库存。 */
    SalesOrder createOrder(String shopCode, SalesOrder command, String username);

    /** 改单，仅草稿可改。整单重建明细。 */
    int updateDraft(String shopCode, SalesOrder command, String tableSuffix, String username);

    /**
     * 建单并直接提交审核：一步落「待审核」，中间不经过草稿。
     *
     * <p>校验与 {@link #createOrder} 一字不差，只是初始状态不同；任一校验不过整单不落库，
     * 不会留下半张草稿。提交不占库存。</p>
     */
    SalesOrder createAndSubmit(String shopCode, SalesOrder command, String username);

    /**
     * 改单并重新提交：草稿 / 已驳回 → 改内容 → 待审核，一个事务。
     *
     * <p>改单那一半与 {@link #updateDraft} 相同，提交那一半与 {@link #submitOrder} 相同，
     * 任一半失败整体回滚。</p>
     */
    int updateAndSubmit(String shopCode, SalesOrder command, String tableSuffix, String username);

    /**
     * 审核通过：待审核 → 已备货 / 备货中。<b>不占用任何库存。</b>
     *
     * <p>原先这一步会按仓库预占现货。取消掉是因为整单要配齐才发得出去：一张单只要缺一个
     * 要生产的件，其余已经占上的货就一直卡着——既发不掉，也不能给别的单用，而且没有任何
     * 流程在盯它的进度。现在库存到发货那一刻才由出库单锁定扣减，谁先发谁拿到。</p>
     *
     * <p>定制件不走「先来后到」：它由生产模块在完工入库时把批次绑到订单明细上
     * （见 {@code u_m_batchinfo.owner_orderdetailcode}），别的订单出库时挑不到。</p>
     *
     * @return 确认后的订单。每条明细的货都有着落就是已备货，有一条没着落就是备货中，
     *         判据见类注释。这只是审核那一刻的快照，不预占也留不住。
     */
    SalesOrder confirmOrder(String shopCode, String orderCode, String tableSuffix, String username);

    /**
     * 提交审核：草稿 / 已驳回 → 待审核。
     *
     * <p>整条链一件货都不占，提交自然也不占：库存到发货那一刻才由出库单锁定扣减。</p>
     */
    int submitOrder(String shopCode, String orderCode, String tableSuffix, String username);

    /**
     * 审核驳回：待审核 → 已驳回，原因必填。
     *
     * <p>已驳回的单可以改单后重新提交。驳回原因保留在订单上，不因重新提交而清空。</p>
     */
    int rejectOrder(String shopCode, String orderCode, String tableSuffix, String reason, String username);

    /**
     * 撤回：待审核 → 草稿。提交人自己把还没审的单收回来改。
     *
     * <p>与 {@link #rejectOrder} 的区别只在谁发起、退到哪：驳回是审核员打回，落「已驳回」
     * 并留原因；撤回是提交人自己收回，落「草稿」不留原因——单子还没被人看过，
     * 没有需要交代的事。</p>
     *
     * <p>待审核状态什么都没占，所以撤回只是改个状态，不用释放任何东西，
     * 也不会有在途批次或在制工单。</p>
     */
    int withdrawOrder(String shopCode, String orderCode, String tableSuffix, String username);

    /**
     * 撤审：已备货 / 备货中 → 草稿。
     *
     * <p>要求<b>一件都没发过</b>、没有在途批次、也没有按它开的在制工单：撤审后订单退回
     * 草稿允许改数量，已发量和批次/工单上记的明细号都会变成对不上的账。发过货又要终止的
     * 用 {@link #closeOrder}。</p>
     *
     * <p><b>备货中照样能撤审</b>：整条链一件货都不占，「货没凑齐」只是审核那一刻的快照，
     * 不构成终止订单的门槛。真正要拦的是已经派出去的活儿，那由在途批次和在制工单
     * 两道检查负责——没派出去过的（纯现货不够）本来就没什么要收拾的。</p>
     */
    int unconfirmOrder(String shopCode, String orderCode, String tableSuffix, String username);

    /**
     * 强制关闭：已备货 / 备货中 / 发货中 → 已关闭，原因必填。
     *
     * <p>用于「发了一部分，剩下的不发了」这类长尾终止。已发的部分是既成事实，
     * 要退得走销售退货；关闭只终止剩余部分，不改已发数量。</p>
     */
    int closeOrder(String shopCode, String orderCode, String tableSuffix, String reason, String username);

    /**
     * 取消订单。<b>一件都没发过</b>才允许取消；
     * 发过货又不想再发的用 {@link #closeOrder}。
     */
    int cancelOrder(String shopCode, String orderCode, String tableSuffix, String username);

    int deleteDraft(String shopCode, String orderCode, String tableSuffix);

    /**
     * 记下正在负责这条明细的生产工单，供工单完工时反查该发哪张订单。
     *
     * <p>只有生产模块调：建工单把明细放进分配时写上工单号，移出分配或工单关闭时传 null
     * 清掉。业务员没有手工入口——挂哪张工单是排产的结果，不是订单侧的决定。</p>
     *
     * @param productionCode 工单号；传 null 表示松开占用
     */
    int updateItemProductionOrder(String shopCode, String orderCode, String tableSuffix,
            String detailCode, String productionCode);

    /**
     * 生产完工认领：把刚入库的成品记到这条订单明细名下，并重算履约状态（V2.0 §6.3
     * 「工单状态通知订单模块，触发发货」）。
     *
     * <p>订单<b>不占库存</b>，所以这里也不占。定制件的专属关系落在批次上
     * （{@code u_m_batchinfo.owner_orderdetailcode}，由生产模块在完工入库时绑定），
     * 本方法只负责认领量的上限校验和状态刷新。</p>
     *
     * <p>上限是<b>未发量</b>：{@code qty − 已发}。已经发走的不再认领一遍，
     * 所以工单重复通知同一条明细也不会把订单认领超。</p>
     *
     * <p>认完调一次 {@link #refreshShipProgress}：定制件的专属批次备齐后，订单会从
     * 「备货中」回到「已确认」，业务员这才能踏实去建发货批次。</p>
     *
     * <p><b>订单侧的变故一律不抛，只少认</b>——订单不存在、还是草稿、已取消、明细被删、
     * 已经全部发完，都返回 0。调用方是生产完工，那边的库存动作已经发生了，
     * 为了下游的状态把一张完工的工单回滚掉是本末倒置：货照样在库存里，只是没人认领。</p>
     *
     * @param wantQty 本次最多认领多少，通常是该产出行的实际产出
     * @return 实际认领的数量，可能为 0
     */
    BigDecimal fulfillFromProduction(String shopCode, String orderCode, String tableSuffix,
            String detailCode, BigDecimal wantQty, String username);

    /**
     * 回款登记。金额在 SQL 内自增，支付与回款状态由本方法统一派生后一起落库。
     *
     * <p><b>只允许财务模块在销售回款流水终审通过时调用</b>（V2.0 §5.4 流程2），订单侧不对外暴露
     * 任何手工入口。已收金额只有这一个来源，与采购单的已付货款同一口径；一旦开了手工后门，
     * 订单会被填成「超额收款」却没有任何一笔对应流水，财务再想认领时该订单已被
     * {@link #selectReceivableOrders} 的「已收满」规则挡在下拉外面。</p>
     *
     * @param paidDelta 本次到账金额，退款传负数
     */
    int applyPayment(String shopCode, String orderCode, String tableSuffix, BigDecimal paidDelta, Date tradeTime);

    /**
     * 按明细重算发货比例与履约状态并落库。发货、取消发货批次后都要调一次。
     *
     * @return 重算后的履约状态
     */
    String refreshShipProgress(String shopCode, String orderCode, String tableSuffix);
}
