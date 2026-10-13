package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.redis.KeyType;
import com.ruoyi.common.redis.RedisService;
import com.ruoyi.system.constant.InventoryDocumentTypes;

class InventoryDocumentCodeGeneratorTest
{
    @Mock
    private RedisService redisService;

    private InventoryDocumentCodeGenerator generator;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        generator = new InventoryDocumentCodeGenerator();
        ReflectionTestUtils.setField(generator, "redisService", redisService);
    }

    @Test
    void generatesAllMainDocumentCodesWithHourlyBase10Prefixes()
    {
        assertPrefix("RK", generator::nextInboundCode);
        assertPrefix("CK", generator::nextOutboundCode);
        assertPrefix("DB", generator::nextTransferCode);
        assertPrefix("CG", generator::nextPurchaseOrderCode);
        assertPrefix("JC", generator::nextLoanCode);
        assertPrefix("GH", generator::nextReturnCode);
        assertPrefix("SHDJ", generator::nextDamageCode);
        assertPrefix("SX", generator::nextRepairOutCode);
        assertPrefix("WXGH", generator::nextRepairInCode);
        assertPrefix("KCLSH", generator::nextLedgerCode);
        assertPrefix("LLCK", generator::nextMaterialOutboundCode);
        assertPrefix("SCCPRK", generator::nextProductInboundCode);
        assertPrefix("SCTLRK", generator::nextMaterialReturnCode);
    }

    @Test
    void generatesAllDetailCodesWithHourlyBase10Prefixes()
    {
        assertPrefix("CG", generator::nextPurchaseOrderDetailCode);
        assertPrefix("RK", () -> generator.nextDetailCode(InventoryDocumentTypes.PURCHASE_INBOUND));
        assertPrefix("CK", () -> generator.nextDetailCode(InventoryDocumentTypes.SALES_OUTBOUND));
        assertPrefix("DB", () -> generator.nextDetailCode(InventoryDocumentTypes.WAREHOUSE_TRANSFER));
        assertPrefix("JC", () -> generator.nextDetailCode(InventoryDocumentTypes.LOAN));
        assertPrefix("GH", () -> generator.nextDetailCode(InventoryDocumentTypes.RETURN));
        assertPrefix("SHDJ", () -> generator.nextDetailCode(InventoryDocumentTypes.DAMAGE));
        assertPrefix("SX", () -> generator.nextDetailCode(InventoryDocumentTypes.REPAIR_OUT));
        assertPrefix("WXGH", () -> generator.nextDetailCode(InventoryDocumentTypes.REPAIR_IN));
        assertPrefix("LLCK", () -> generator.nextDetailCode(InventoryDocumentTypes.MATERIAL_OUTBOUND));
        assertPrefix("SCCPRK", () -> generator.nextDetailCode(InventoryDocumentTypes.PRODUCT_INBOUND));
        assertPrefix("SCTLRK", () -> generator.nextDetailCode(InventoryDocumentTypes.MATERIAL_RETURN));
    }

    /**
     * 订单模块的单号：只有<b>订单号本身</b>是无前缀的纯数字，其余四个仍带前缀。
     *
     * <p>数字部分本来就全系统唯一（geHourKey 的序列号是全局共享的），
     * 去掉前缀不会跟别的单据撞号。</p>
     */
    @Test
    void generatesOrderModuleCodes()
    {
        assertPrefix("", generator::nextSalesOrderCode);
        assertPrefix("SOMX", generator::nextSalesOrderDetailCode);
        assertPrefix("XSFH", generator::nextShipBatchCode);
        assertPrefix("XSFHMX", generator::nextShipBatchDetailCode);
        assertPrefix("YZ", generator::nextReservationCode);
    }

    /** 订单号必须是纯数字：带上字母会被格式校验拦下，防止哪天前缀被改回来没人发现。 */
    @Test
    void rejectsSalesOrderCodeWithLetters()
    {
        reset(redisService);
        when(redisService.geHourKey(KeyType.Base10, "")).thenReturn(success("SO123456"));
        assertThrows(ServiceException.class, generator::nextSalesOrderCode);
    }

    @Test
    void rejectsUnknownDetailDocumentType()
    {
        assertThrows(ServiceException.class, () -> generator.nextDetailCode("UNKNOWN"));
    }

    @Test
    void reportsRedisFailureAndInvalidCodeFormat()
    {
        Map<String, Object> failure = new HashMap<>();
        failure.put("result", false);
        failure.put("errorinfo", "redis unavailable");
        when(redisService.geHourKey(KeyType.Base10, "JC")).thenReturn(failure);
        assertThrows(ServiceException.class, generator::nextLoanCode);

        reset(redisService);
        Map<String, Object> invalid = success("JCABC");
        when(redisService.geHourKey(KeyType.Base10, "JC")).thenReturn(invalid);
        assertThrows(ServiceException.class, generator::nextLoanCode);
    }

    private void assertPrefix(String prefix, Supplier<String> codeSupplier)
    {
        reset(redisService);
        String expected = prefix + "123456";
        when(redisService.geHourKey(KeyType.Base10, prefix)).thenReturn(success(expected));

        assertEquals(expected, codeSupplier.get());
        verify(redisService).geHourKey(KeyType.Base10, prefix);
    }

    private Map<String, Object> success(String primaryKey)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("result", true);
        result.put("primaryKey", primaryKey);
        return result;
    }
}
