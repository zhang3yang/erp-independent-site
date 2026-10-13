package com.ruoyi.station.console.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteBuyerAddress;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteBuyerAddressMapper;

/**
 * 买家收货地址簿。
 *
 * <h3>⚠ 归属只从会话来</h3>
 * <p>这个类的每一个公开方法第一个参数都是 {@code buyerCode}，而调用方
 * （{@code BuyerAccountController}）只从买家会话取它。<b>没有一个方法接受
 * 「查/改谁的」参数</b>——这张表对外用连号的自增 id，少了这一条约束，
 * 把 id 改个数字就能读到、改掉别人的收货地址和电话。</p>
 *
 * <p>「不是他的那一条」一律走 {@link #require} 报 404，不报 403：
 * 403 等于告诉对方「这个 id 是真的，只是不属于你」，那是一台探测器。</p>
 *
 * <h3>⚠ 这里的地址不影响历史订单</h3>
 * <p>订单上的收货地是下单当时的快照（{@code u_s_order} 那几列）。买家改地址簿，
 * 已经发出去的货不会跟着变——所以这个类里没有任何一处去回写订单。</p>
 */
@Service
public class SiteBuyerAddressService
{
    /**
     * 一个买家最多存几条。
     *
     * <p>不是业务需要，是一道闸：这几个接口要买家会话，但会话是自助注册来的，
     * 没有这个上限，一个脚本就能往库里灌几十万行。20 条对「工厂 + 几个货代仓库 +
     * 几个客户指定地址」这种真实用法绰绰有余。</p>
     */
    private static final int MAX_ADDRESSES = 20;

    @Autowired
    private SiteBuyerAddressMapper addressMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    /** 他的地址簿。默认的排最前 */
    public List<SiteBuyerAddress> list(String buyerCode)
    {
        return addressMapper.selectByBuyer(requireOwner(buyerCode));
    }

    /**
     * 新增或修改一条。{@code form.id} 有值就是改，没有就是新增。
     *
     * <p>⚠ 改的时候 {@code id} 和 {@code buyerCode} 一起进 where。影响 0 行说明
     * 「这条不存在，或者不是他的」，两种都报同一句 404，见类注释。</p>
     *
     * @param makeDefault 顺手设为默认。<b>他的第一条地址一定是默认的</b>，
     *                    不管这个参数传什么——否则结账页会出现「有地址簿但没有默认」，
     *                    而买家看不出为什么要自己再点一下。
     */
    @Transactional(rollbackFor = Exception.class)
    public SiteBuyerAddress save(String buyerCode, SiteBuyerAddress form, boolean makeDefault)
    {
        String owner = requireOwner(buyerCode);
        if (form == null)
        {
            throw BuyerText.error("addr.empty", 400);
        }

        String contactName = cut(form.getContactName(), 100);
        String country = cut(form.getCountry(), 80);
        // 和结账页同一条规则：收件人和国家必填，别的都可以空。
        // 国家是唯一必填的地址字段——估运费最少要它（u_s_order.country 也是 not null）
        if (contactName == null)
        {
            throw BuyerText.error("addr.recipientRequired", 400);
        }
        if (country == null)
        {
            throw BuyerText.error("addr.countryRequired", 400);
        }

        SiteBuyerAddress entity = new SiteBuyerAddress();
        entity.setBuyerCode(owner);
        entity.setLabel(cut(form.getLabel(), 50));
        entity.setContactName(contactName);
        entity.setPhone(cut(form.getPhone(), 50));
        entity.setAddrLine1(cut(form.getAddrLine1(), 200));
        entity.setAddrLine2(cut(form.getAddrLine2(), 200));
        entity.setCity(cut(form.getCity(), 80));
        entity.setRegion(cut(form.getRegion(), 80));
        entity.setPostal(cut(form.getPostal(), 30));
        entity.setCountry(country);

        boolean creating = form.getId() == null;
        if (creating)
        {
            int existing = addressMapper.countByBuyer(owner);
            if (existing >= MAX_ADDRESSES)
            {
                throw BuyerText.error("addr.tooMany", 400, MAX_ADDRESSES);
            }
            SiteInfo site = siteInfoService.require();
            entity.setShopCode(site.getShopCode());
            entity.setSiteCode(site.getSiteCode());
            // 第一条一定是默认的，见方法注释
            entity.setIsDefault(existing == 0 || makeDefault);
            if (entity.isMarkedDefault())
            {
                addressMapper.clearDefault(owner);
            }
            addressMapper.insert(entity);
        }
        else
        {
            entity.setId(form.getId());
            if (addressMapper.update(entity) == 0)
            {
                throw BuyerText.error("addr.missing", 404);
            }
            if (makeDefault)
            {
                // update 那一句刻意不碰 isdefault（见 mapper XML），所以这里单独设
                addressMapper.clearDefault(owner);
                addressMapper.markDefault(entity.getId(), owner);
            }
        }
        return require(entity.getId(), owner);
    }

    /** 设默认。⚠ 先清零再置一，两句在同一个事务里 */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(String buyerCode, Long id)
    {
        String owner = requireOwner(buyerCode);
        require(id, owner);
        addressMapper.clearDefault(owner);
        addressMapper.markDefault(id, owner);
    }

    /**
     * 删一条。
     *
     * <p>⚠ 删掉的如果是默认地址，把剩下最新的一条顶上去。不补的话结账页会变成
     * 「有地址簿但选不出默认」，而买家看不出为什么——和 mapper 里
     * {@code selectLatestId} 的注释是同一件事。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String buyerCode, Long id)
    {
        String owner = requireOwner(buyerCode);
        SiteBuyerAddress existing = require(id, owner);
        addressMapper.delete(id, owner);
        if (existing.isMarkedDefault())
        {
            Long next = addressMapper.selectLatestId(owner);
            if (next != null)
            {
                addressMapper.markDefault(next, owner);
            }
        }
    }

    /**
     * 取一条，不是他的就报 404。
     *
     * <p>「不存在」和「不是你的」回同一句话：区分开等于告诉他哪些 id 是真的。
     * 和 {@code BuyerAccountController.myOrder} 那一处的口径一致。</p>
     */
    private SiteBuyerAddress require(Long id, String buyerCode)
    {
        SiteBuyerAddress found = id == null ? null : addressMapper.selectOne(id, buyerCode);
        if (found == null)
        {
            throw BuyerText.error("addr.missing", 404);
        }
        return found;
    }

    /** 会话里没有买家编码是调用方的错（它该先 requireBuyer），不是买家的输入问题 */
    private static String requireOwner(String buyerCode)
    {
        String owner = trimToNull(buyerCode);
        if (owner == null)
        {
            throw BuyerText.error("auth.signInRequired", 401);
        }
        return owner;
    }

    private static String trimToNull(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cut(String value, int max)
    {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
