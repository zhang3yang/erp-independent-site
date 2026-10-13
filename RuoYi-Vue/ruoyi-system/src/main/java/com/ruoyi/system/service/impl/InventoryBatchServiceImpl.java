package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.enums.StockState;
import com.ruoyi.system.domain.InventoryBatch;
import com.ruoyi.system.domain.InventoryBatchAllocation;
import com.ruoyi.system.domain.InventoryGoodsPosition;
import com.ruoyi.system.mapper.InventoryBatchMapper;
import com.ruoyi.system.mapper.InventoryGoodsPositionMapper;
import com.ruoyi.system.service.IInventoryBatchService;
import com.ruoyi.system.utils.InventoryCostMath;

/**
 * 批次信息Service业务层处理
 */
@Service
public class InventoryBatchServiceImpl implements IInventoryBatchService
{
    @Autowired
    private InventoryBatchMapper batchMapper;

    @Autowired
    private InventoryMasterDataCodeGenerator codeGenerator;

    /** 建批次时用来取「这个商品一般放哪儿」，作为新批次货位的默认值 */
    @Autowired
    private InventoryGoodsPositionMapper goodsPositionMapper;

    @Autowired
    private StaffNameResolver staffNameResolver;

    /**
     * 查询批次信息
     */
    @Override
    public InventoryBatch selectBatchByKey(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state)
    {
        InventoryBatch batch = batchMapper.selectBatchByKey(shopCode, batchCode, goodsCode, wareCode, state);
        if (batch != null)
        {
            batch.setCreateUserName(staffNameResolver.nickNameOrLogin(batch.getCreateUser()));
        }
        return batch;
    }

    /**
     * 查询批次列表
     */
    @Override
    public List<InventoryBatch> selectBatchList(InventoryBatch batch)
    {
        return batchMapper.selectBatchList(batch);
    }

    /**
     * 查询可用批次列表
     */
    @Override
    public List<InventoryBatch> selectAvailableBatchList(InventoryBatch batch)
    {
        return batchMapper.selectAvailableBatchList(batch);
    }

    /**
     * 新增批次信息
     */
    @Override
    public int insertBatch(InventoryBatch batch)
    {
        if (batch.getBatchCode() == null || batch.getBatchCode().trim().isEmpty())
        {
            batch.setBatchCode(generateBatchCode(batch.getShopCode()));
        }

        // 校验批次是否已存在
        InventoryBatch existing = batchMapper.selectBatchByKey(
                batch.getShopCode(), batch.getBatchCode(), batch.getGoodsCode(),
                batch.getWareCode(), batch.getStockState());
        if (existing != null)
        {
            if (batch.getStockState() != null && existing.getStockState() != null
                    && !batch.getStockState().equals(existing.getStockState()))
            {
                throw new ServiceException("批次'" + batch.getBatchCode() + "'在仓库'"
                        + batch.getWareCode() + "'已属于库存状态'" + existing.getStockState().getLabel() + "'");
            }
            BigDecimal qty = batch.getInitQty() == null ? BigDecimal.ZERO : batch.getInitQty();
            existing.setInitQty((existing.getInitQty() == null ? BigDecimal.ZERO : existing.getInitQty()).add(qty));
            existing.setRemainQty((existing.getRemainQty() == null ? BigDecimal.ZERO : existing.getRemainQty()).add(qty));
            existing.setStatus("active");
            return batchMapper.addBatchQty(batch);
        }
        // 设置默认值
        if (batch.getInitQty() == null)
        {
            batch.setInitQty(BigDecimal.ZERO);
        }
        if (batch.getRemainQty() == null)
        {
            batch.setRemainQty(batch.getInitQty());
        }
        if (batch.getStatus() == null || batch.getStatus().isEmpty())
        {
            batch.setStatus("active");
        }
        applyDefaultPosition(batch);

        return batchMapper.insertBatch(batch);
    }

    /**
     * 新批次的货位默认取商品主档上登记的位置。
     *
     * <p>两个粒度都留着：{@code u_m_goods_position} 是<b>商品维度</b>的「这个商品一般放哪儿」，
     * 一个商品一条；批次上的是<b>批次维度</b>的「这一批实际放在哪儿」。先进先出拣货要按批次
     * 找货，只知道商品放哪个区不够用。默认值让库管员绝大多数时候不用手填，
     * 真挪了货再改那一条批次即可，不影响主档。</p>
     *
     * <p>调用方显式给了货位就用它的；商品没登记过位置就留空，不报错——
     * 位置只是管理信息，缺了不该挡住入库过账。</p>
     */
    private void applyDefaultPosition(InventoryBatch batch)
    {
        if (batch.getPosition() != null && !batch.getPosition().trim().isEmpty()) return;
        InventoryGoodsPosition registered = goodsPositionMapper.selectPositionByGoodsCode(
                batch.getShopCode(), batch.getGoodsCode(), null);
        if (registered != null)
        {
            batch.setPosition(registered.getPosition());
        }
    }

    @Override
    public int updateBatchPosition(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state, String position)
    {
        InventoryBatch existing = batchMapper.selectBatchByKey(shopCode, batchCode, goodsCode, wareCode, state);
        if (existing == null)
        {
            throw new ServiceException("批次不存在");
        }
        String trimmed = position == null ? null : position.trim();
        if (trimmed != null && trimmed.length() > 100)
        {
            // 列是 varchar(100)，不截住的话会撞 Data truncation 的原始报错
            throw new ServiceException("存放位置不能超过 100 个字符");
        }
        // 清空是合法操作：货挪走了、还没定新位置，留空比留个错的强
        return batchMapper.updateBatchPosition(shopCode, batchCode, goodsCode, wareCode, state,
                trimmed == null || trimmed.isEmpty() ? null : trimmed);
    }

    /**
     * 修改批次信息
     */
    @Override
    public int updateBatch(InventoryBatch batch)
    {
        // 校验是否存在
        InventoryBatch existing = batchMapper.selectBatchByKey(
                batch.getShopCode(), batch.getBatchCode(), batch.getGoodsCode(),
                batch.getWareCode(), batch.getStockState());
        if (existing == null)
        {
            throw new ServiceException("批次记录不存在");
        }

        return batchMapper.updateBatch(batch);
    }

    /**
     * 扣减批次剩余数量
     */
    @Override
    public int deductBatchQty(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state, BigDecimal deductQty)
    {
        if (deductQty == null || deductQty.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("扣减数量必须大于0");
        }

        // 校验批次是否存在且有足够库存
        InventoryBatch batch = batchMapper.selectBatchByKey(
                shopCode, batchCode, goodsCode, wareCode, state);
        if (batch == null)
        {
            throw new ServiceException("批次记录不存在");
        }
        if (!"active".equals(batch.getStatus()))
        {
            throw new ServiceException("批次已关闭或过期");
        }
        BigDecimal remainQty = batch.getRemainQty() != null ? batch.getRemainQty() : BigDecimal.ZERO;
        if (remainQty.compareTo(deductQty) < 0)
        {
            throw new ServiceException("批次剩余数量不足，当前剩余：" + remainQty);
        }

        int rows = batchMapper.deductBatchQty(
                shopCode, batchCode, goodsCode, wareCode, state, deductQty);
        if (rows == 0)
        {
            throw new ServiceException("扣减失败，请刷新后重试");
        }
        return rows;
    }

    /**
     * 删除批次信息
     */
    @Override
    public int deleteBatchByKey(String shopCode, String batchCode, String goodsCode,
            String wareCode, StockState state)
    {
        // 校验是否存在
        InventoryBatch existing = batchMapper.selectBatchByKey(
                shopCode, batchCode, goodsCode, wareCode, state);
        if (existing == null)
        {
            throw new ServiceException("批次记录不存在");
        }
        BigDecimal existingRemainQty = existing.getRemainQty() != null ? existing.getRemainQty() : BigDecimal.ZERO;
        if (existingRemainQty.compareTo(BigDecimal.ZERO) > 0)
        {
            throw new ServiceException("批次还有剩余库存，不能删除");
        }

        return batchMapper.deleteBatchByKey(shopCode, batchCode, goodsCode, wareCode, state);
    }

    /**
     * 生成批次号：PC + Redis 秒级 Base36 大写编码。
     */
    @Override
    public String generateBatchCode(String shopCode)
    {
        return codeGenerator.nextBatchCode();
    }

    @Override
    public List<InventoryBatch> selectAvailableBatchByContext(String shopCode, String goodsCode, String wareCode,
            StockState state)
    {
        return batchMapper.selectAvailableBatchByContext(shopCode, goodsCode, wareCode, state);
    }

    @Override
    public List<InventoryBatchAllocation> deductFifoBatches(String shopCode, String goodsCode,
            String wareCode, StockState state, BigDecimal deductQty, String orderDetailCode)
    {
        if (deductQty == null || deductQty.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("FIFO扣减数量必须大于0");
        }

        List<InventoryBatch> batches = batchMapper.selectAvailableBatchForUpdate(
                shopCode, goodsCode, wareCode, state, orderDetailCode);
        BigDecimal availableQty = BigDecimal.ZERO;
        for (InventoryBatch batch : batches)
        {
            availableQty = availableQty.add(batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty());
        }
        if (availableQty.compareTo(deductQty) < 0)
        {
            // 差额可能全在别人的定制批次里：仓库明明有货却发不出去，不说清楚会被当成数据错乱。
            // 那些货是专门为别的订单做的，本来就不该被这一单拿走。
            throw new ServiceException("FIFO批次库存不足，可用批次数量：" + availableQty
                    + "，需要出库：" + deductQty
                    + "。注意：为其他销售订单专门生产的定制批次不计入可用量");
        }

        List<InventoryBatchAllocation> allocations = new java.util.ArrayList<>();
        BigDecimal pendingQty = deductQty;
        for (InventoryBatch batch : batches)
        {
            if (pendingQty.compareTo(BigDecimal.ZERO) <= 0)
            {
                break;
            }

            BigDecimal beforeQty = batch.getRemainQty() == null ? BigDecimal.ZERO : batch.getRemainQty();
            BigDecimal allocatedQty = beforeQty.min(pendingQty);
            int rows = batchMapper.deductBatchQty(shopCode, batch.getBatchCode(), goodsCode,
                    wareCode, state, allocatedQty);
            if (rows == 0)
            {
                throw new ServiceException("FIFO批次'" + batch.getBatchCode() + "'已被其他业务修改，请重试");
            }

            InventoryBatchAllocation allocation = new InventoryBatchAllocation();
            allocation.setBatchCode(batch.getBatchCode());
            allocation.setQty(allocatedQty);
            allocation.setCostPrice(InventoryCostMath.batchCostPrice(batch));
            allocation.setBeforeQty(beforeQty);
            allocation.setAfterQty(beforeQty.subtract(allocatedQty));
            allocations.add(allocation);
            pendingQty = pendingQty.subtract(allocatedQty);
        }
        return allocations;
    }

    @Override
    public void bindOwner(String shopCode, String batchCode, String goodsCode, String wareCode,
            StockState state, String ownerOrderCode, String ownerDetailCode)
    {
        if (batchCode == null || batchCode.trim().isEmpty()) throw new ServiceException("批次号不能为空");
        if (ownerDetailCode == null || ownerDetailCode.trim().isEmpty())
        {
            throw new ServiceException("专属订单明细代码不能为空");
        }
        // SQL 的 where 里放了「没绑过或就是绑给它」，更新 0 行只可能是已经绑给了别人
        if (batchMapper.bindBatchOwner(shopCode, batchCode, goodsCode, wareCode, state,
                ownerOrderCode, ownerDetailCode) != 1)
        {
            throw new ServiceException("批次'" + batchCode + "'已经是其他销售订单的定制货，不能再绑给"
                    + ownerOrderCode + "。请为本次产出换一个批次号");
        }
    }

    @Override
    public int releaseOwner(String shopCode, String ownerDetailCode)
    {
        if (ownerDetailCode == null || ownerDetailCode.trim().isEmpty()) return 0;
        return batchMapper.releaseBatchOwnerByDetail(shopCode, ownerDetailCode);
    }

    @Override
    public List<InventoryBatch> selectBatchesByOwner(String shopCode, String ownerDetailCode)
    {
        if (ownerDetailCode == null || ownerDetailCode.trim().isEmpty())
        {
            return java.util.Collections.emptyList();
        }
        return batchMapper.selectBatchesByOwnerDetail(shopCode, ownerDetailCode);
    }

    @Override
    public boolean hasOwnedBatch(String shopCode, String goodsCode, String wareCode, StockState state)
    {
        return batchMapper.countOwnedBatches(shopCode, goodsCode, wareCode, state) > 0;
    }
}
