package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ShopStorage;

/**
 * 商家对象存储配置数据层。
 *
 * <p>一个商家每个厂商各一行，主键 (shop_code, provider)；其中一行 {@code active='1'}
 * 表示当前生效。所以查询分两种：查「当前用的那套」和查「某个厂商那套」——
 * 前者给上传用，后者给界面上切换厂商时回显用。</p>
 */
public interface ShopStorageMapper
{
    /** 当前生效的那套。accessKeySecret 取出来是密文，调用方负责解密 */
    ShopStorage selectActive(@Param("shopCode") String shopCode);

    /** 指定厂商那套，没配过返回 null。界面切换厂商时用它回显 */
    ShopStorage selectByProvider(@Param("shopCode") String shopCode,
            @Param("provider") String provider);

    /** 商家配过哪几家，界面上给已配置的厂商打个标记 */
    List<String> selectConfiguredProviders(@Param("shopCode") String shopCode);

    int insertStorage(ShopStorage storage);

    int updateStorage(ShopStorage storage);

    /**
     * 只更新除密钥外的字段。
     *
     * <p>界面上改配置时密钥输入框留空 = 不改密钥，这时走这条，
     * 避免把库里的密文覆盖成空。</p>
     */
    int updateStorageKeepSecret(ShopStorage storage);

    /** 把这个商家所有行的 active 置 0，紧接着再把选中的那行置 1 */
    int clearActive(@Param("shopCode") String shopCode);

    int markActive(@Param("shopCode") String shopCode, @Param("provider") String provider);

    int deleteByShop(@Param("shopCode") String shopCode);
}
