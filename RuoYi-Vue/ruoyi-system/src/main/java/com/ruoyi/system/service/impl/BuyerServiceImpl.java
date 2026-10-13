package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.MobileUtils;
import com.ruoyi.system.domain.Buyer;
import com.ruoyi.system.domain.Buyer2;
import com.ruoyi.system.mapper.Buyer2Mapper;
import com.ruoyi.system.mapper.BuyerMapper;
import com.ruoyi.system.mapper.SalesOrderMapper;
import com.ruoyi.system.service.IBuyerService;
import com.ruoyi.system.utils.MonthTableSuffix;

/**
 * 销售客户 Service 实现。
 */
@Service
public class BuyerServiceImpl implements IBuyerService
{
    /** 选择器一次最多返回多少条。够业务员在弹窗里翻，再多就该用搜索了。 */
    private static final int SELECTOR_LIMIT = 50;

    @Autowired
    private BuyerMapper buyerMapper;

    @Autowired
    private Buyer2Mapper buyer2Mapper;

    @Autowired
    private InventoryDocumentCodeGenerator codeGenerator;

    /** 统计客户历史订单要遍历订单月表，月表清单从这里取 */
    @Autowired
    private SalesOrderMapper orderMapper;

    @Override
    public List<Buyer> selectBuyerList(Buyer query)
    {
        requireShop(query == null ? null : query.getShopCode());
        return buyerMapper.selectList(query);
    }

    @Override
    public Buyer selectBuyerByCode(String shopCode, String buyerCode)
    {
        requireShop(shopCode);
        Buyer buyer = buyerMapper.selectByCode(shopCode, trimToNull(buyerCode));
        if (buyer != null)
        {
            buyer.setOrderCount(countOrders(shopCode, buyer.getBuyerCode()));
            Buyer2 query = new Buyer2();
            query.setShopCode(shopCode);
            query.setBuyerCode(buyer.getBuyerCode());
            List<Buyer2> children = buyer2Mapper.selectList(query);
            buyer.setChildren(children);
            buyer.setBuyer2Count(children.size());
        }
        return buyer;
    }

    @Override
    public List<Buyer> selectSelectorList(String shopCode, String keyword)
    {
        requireShop(shopCode);
        return buyerMapper.selectSelectorList(shopCode, trimToNull(keyword), SELECTOR_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Buyer insertBuyer(Buyer buyer)
    {
        requireShop(buyer == null ? null : buyer.getShopCode());
        normalize(buyer);
        validate(buyer);

        // 编码一律服务端生成：让前端传编码等于把主键交给前端，
        // 既能撞号，也能被拿来覆盖别人的客户。
        buyer.setBuyerCode(codeGenerator.nextBuyerCode());
        // 一期只有个人客户，商家客户二期再开。这里写死而不是信前端传的值。
        buyer.setBuyerType(Buyer.TYPE_PERSONAL);
        buyer.setSysUserCode(null);
        buyer.setBuyerShop(null);
        // 归属业务员＝建档人，值由 Controller 从登录态取好塞进来（与 createUser 同一套做法），
        // 前端传的会被覆盖掉——那个输入框是 disabled 的，但 disabled 只挡得住页面上的手。

        try
        {
            buyerMapper.insert(buyer);
        }
        catch (DuplicateKeyException e)
        {
            // 唯一键只有手机号一个，撞了必然是它。先查后插挡不住并发，只能靠这里兜底。
            throw new ServiceException(mobileConflictMessage(buyer.getShopCode(), buyer.getMobile()));
        }
        return buyer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBuyer(Buyer buyer)
    {
        requireShop(buyer == null ? null : buyer.getShopCode());
        normalize(buyer);
        validate(buyer);
        if (isBlank(buyer.getBuyerCode()))
        {
            throw new ServiceException("客户编码不能为空");
        }

        Buyer existing = buyerMapper.selectByCode(buyer.getShopCode(), buyer.getBuyerCode());
        if (existing == null)
        {
            throw new ServiceException("客户不存在：" + buyer.getBuyerCode());
        }
        // 归属业务员不随改单变动：客户是谁建的档就归谁，别人来改一下资料
        // 不该把客户划到自己名下。要转交归属得另开一个「转移客户」的动作。
        buyer.setStaffCode(existing.getStaffCode());
        buyer.setStaffName(existing.getStaffName());

        try
        {
            return buyerMapper.update(buyer);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException(mobileConflictMessage(buyer.getShopCode(), buyer.getMobile()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBuyerByCodes(String shopCode, String[] buyerCodes)
    {
        requireShop(shopCode);
        if (buyerCodes == null || buyerCodes.length == 0)
        {
            throw new ServiceException("请选择要删除的客户");
        }

        // 订单里存的是快照，删客户不会让历史订单变成空白。挡在这里是业务上的考虑：
        // 客户档案是订单的来源凭据，有成交记录还被删掉，后面对账时无从追溯。
        // 需要「不再往来」用停用，不用删除。
        for (String code : buyerCodes)
        {
            String buyerCode = trimToNull(code);
            if (buyerCode == null) continue;
            int orders = countOrders(shopCode, buyerCode);
            if (orders > 0)
            {
                Buyer buyer = buyerMapper.selectByCode(shopCode, buyerCode);
                String name = buyer == null ? buyerCode : buyer.resolveDisplayName();
                throw new ServiceException("客户「" + name + "」已有 " + orders
                        + " 笔历史订单，不能删除；如不再往来请改为停用");
            }
        }
        // 先删子表再删主表。顺序反过来也能删掉，但中途异常回滚前会有一瞬间的孤儿子记录，
        // 而且这个顺序读起来就是「先摘干净再删本体」，跟其它主子表一致。
        buyer2Mapper.deleteByBuyerCodes(shopCode, buyerCodes);
        return buyerMapper.deleteByCodes(shopCode, buyerCodes);
    }

    // ---- 二级客户 ----

    @Override
    public List<Buyer2> selectBuyer2List(Buyer2 query)
    {
        requireShop(query == null ? null : query.getShopCode());
        if (isBlank(query.getBuyerCode()))
        {
            throw new ServiceException("请先选择主客户");
        }
        return buyer2Mapper.selectList(query);
    }

    @Override
    public List<Buyer2> selectBuyer2Options(String shopCode, String buyerCode)
    {
        requireShop(shopCode);
        String code = trimToNull(buyerCode);
        if (code == null)
        {
            return new ArrayList<>();
        }
        return buyer2Mapper.selectOptions(shopCode, code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Buyer2 insertBuyer2(Buyer2 buyer2)
    {
        requireShop(buyer2 == null ? null : buyer2.getShopCode());
        normalize2(buyer2);
        validate2(buyer2);
        // 主客户必须存在且属于本商户，否则会挂出一条永远查不到爹的子记录
        requireBuyer(buyer2.getShopCode(), buyer2.getBuyerCode());

        buyer2.setBuyer2Code(codeGenerator.nextBuyer2Code());
        buyer2Mapper.insert(buyer2);
        return buyer2;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBuyer2(Buyer2 buyer2)
    {
        requireShop(buyer2 == null ? null : buyer2.getShopCode());
        normalize2(buyer2);
        if (isBlank(buyer2.getBuyer2Code()))
        {
            throw new ServiceException("二级客户编码不能为空");
        }
        Buyer2 existing = buyer2Mapper.selectByCode(buyer2.getShopCode(), buyer2.getBuyer2Code());
        if (existing == null)
        {
            throw new ServiceException("二级客户不存在：" + buyer2.getBuyer2Code());
        }
        // 归属不可改。前端就算把 buyerCode 传成别人，也以库里那条为准，
        // update 语句本身也不含 buyercode 列。
        buyer2.setBuyerCode(existing.getBuyerCode());
        validate2(buyer2);
        return buyer2Mapper.update(buyer2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBuyer2ByCodes(String shopCode, String[] buyer2Codes)
    {
        requireShop(shopCode);
        if (buyer2Codes == null || buyer2Codes.length == 0)
        {
            throw new ServiceException("请选择要删除的二级客户");
        }
        // 二级客户不像主客户那样拦「有历史订单就不让删」：订单上存的是快照，
        // 而门店本身撤店是常事，拦住反而逼着业务员留一堆废数据。
        return buyer2Mapper.deleteByCodes(shopCode, buyer2Codes);
    }

    @Override
    public Buyer2 requireBuyer2(String shopCode, String buyerCode, String buyer2Code)
    {
        requireShop(shopCode);
        String code = trimToNull(buyer2Code);
        if (code == null)
        {
            throw new ServiceException("二级客户编码不能为空");
        }
        Buyer2 buyer2 = buyer2Mapper.selectByCode(shopCode, code);
        if (buyer2 == null)
        {
            throw new ServiceException("二级客户不存在：" + code);
        }
        // 关键校验：少了这条，前端传个别的客户的 buyer2code 就能把 A 客户的订单
        // 配上 B 客户的门店，而且快照一旦落库就再也查不出来是错的。
        if (!buyer2.getBuyerCode().equals(trimToNull(buyerCode)))
        {
            throw new ServiceException("二级客户「" + buyer2.resolveDisplayName()
                    + "」不属于所选客户，请重新选择");
        }
        return buyer2;
    }

    private void normalize2(Buyer2 buyer2)
    {
        buyer2.setBuyer2Code(trimToNull(buyer2.getBuyer2Code()));
        buyer2.setBuyerCode(trimToNull(buyer2.getBuyerCode()));
        buyer2.setBuyer2Name(trimToNull(buyer2.getBuyer2Name()));
        buyer2.setBuyer2User(trimToNull(buyer2.getBuyer2User()));
        buyer2.setMobile(trimToNull(buyer2.getMobile()));
        buyer2.setAddress(trimToNull(buyer2.getAddress()));
        buyer2.setImageUrl(trimToNull(buyer2.getImageUrl()));
        buyer2.setRemark(trimToNull(buyer2.getRemark()));
    }

    private void validate2(Buyer2 buyer2)
    {
        if (isBlank(buyer2.getBuyerCode()))
        {
            throw new ServiceException("请先选择主客户");
        }
        if (isBlank(buyer2.getBuyer2Name()))
        {
            throw new ServiceException("二级客户名称不能为空");
        }
        // 全项目的手机号 / 电话字段统一按 11 位手机号收，口径见 MobileUtils
        MobileUtils.validateOptional(buyer2.getMobile(), "二级客户电话");
        Integer active = buyer2.getActiveFlag();
        if (active != null && active != 0 && active != 1)
        {
            throw new ServiceException("状态只能是启用或停用");
        }
    }

    @Override
    public Buyer requireBuyer(String shopCode, String buyerCode)
    {
        requireShop(shopCode);
        String code = trimToNull(buyerCode);
        if (code == null)
        {
            throw new ServiceException("客户编码不能为空");
        }
        // 带上 shopCode 查，跨商户的编码在这里就查不到，串码到此为止
        Buyer buyer = buyerMapper.selectByCode(shopCode, code);
        if (buyer == null)
        {
            throw new ServiceException("客户不存在：" + code);
        }
        return buyer;
    }

    /**
     * 空串归一成 null。
     *
     * <p>手机号这一步是必须的：唯一索引允许多个 null，空串却只能有一条，
     * 存空串会让第二个没填手机号的客户直接撞唯一键。其余文本字段一起处理，
     * 免得库里 null 和空串两种「没填」混着存。</p>
     */
    private void normalize(Buyer buyer)
    {
        buyer.setBuyerCode(trimToNull(buyer.getBuyerCode()));
        buyer.setMobile(trimToNull(buyer.getMobile()));
        buyer.setTelephone(trimToNull(buyer.getTelephone()));
        buyer.setBuyerSimple(trimToNull(buyer.getBuyerSimple()));
        buyer.setBuyerFull(trimToNull(buyer.getBuyerFull()));
        buyer.setContactPerson(trimToNull(buyer.getContactPerson()));
        buyer.setBuyerUser(trimToNull(buyer.getBuyerUser()));
        buyer.setStaffCode(trimToNull(buyer.getStaffCode()));
        buyer.setStaffName(trimToNull(buyer.getStaffName()));
        buyer.setAddress(trimToNull(buyer.getAddress()));
        buyer.setPartnerCode(trimToNull(buyer.getPartnerCode()));
        buyer.setImageUrl(trimToNull(buyer.getImageUrl()));
        buyer.setShopImage(trimToNull(buyer.getShopImage()));
        buyer.setRemark(trimToNull(buyer.getRemark()));
    }

    private void validate(Buyer buyer)
    {
        if (isBlank(buyer.getBuyerSimple()) && isBlank(buyer.getBuyerFull()))
        {
            throw new ServiceException("客户简称和全称至少填一个");
        }
        // 全项目的手机号 / 电话字段统一按 11 位手机号收，口径见 MobileUtils
        MobileUtils.validateOptional(buyer.getMobile(), "客户手机号");
        MobileUtils.validateOptional(buyer.getTelephone(), "客户固定电话");
        Integer star = buyer.getStarRating();
        if (star != null && (star < 0 || star > 5))
        {
            // 库上有 ck_buyer_starrating 兜底，这里先拦是为了给一句人话，
            // 而不是让业务员看到一行 CHECK constraint violated
            throw new ServiceException("客户星级只能是 0-5");
        }
        Integer active = buyer.getActiveFlag();
        if (active != null && active != 0 && active != 1)
        {
            throw new ServiceException("状态只能是启用或停用");
        }
    }

    /** 手机号冲突时把占用者报出来，否则业务员只知道「重复」却不知道跟谁重复 */
    private String mobileConflictMessage(String shopCode, String mobile)
    {
        Buyer owner = mobile == null ? null : buyerMapper.selectByMobile(shopCode, mobile);
        if (owner == null)
        {
            return "客户保存失败：手机号 " + mobile + " 已被占用";
        }
        return "手机号 " + mobile + " 已被客户「" + owner.resolveDisplayName()
                + "」（" + owner.getBuyerCode() + "）使用";
    }

    /** 跨所有订单月表数这个客户的订单。只在详情和删除校验时调，列表不要用。 */
    private int countOrders(String shopCode, String buyerCode)
    {
        int total = 0;
        for (String suffix : MonthTableSuffix.validDescending(orderMapper.selectOrderTableSuffixes()))
        {
            total += buyerMapper.countOrdersInMonth(shopCode, buyerCode, suffix);
        }
        return total;
    }

    private void requireShop(String shopCode)
    {
        if (isBlank(shopCode))
        {
            throw new ServiceException("商家编码不能为空");
        }
    }

    private static String trimToNull(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
