package com.ruoyi.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;

class InventoryDocumentTableRouterTest
{
    private final InventoryDocumentTableRouter router = new InventoryDocumentTableRouter();

    @Test
    void validatesAndReturnsAValidSuffix()
    {
        assertEquals("202608", router.validate("202608"));
    }

    @Test
    void rejectsMalformedOrImpossibleSuffixes()
    {
        assertThrows(ServiceException.class, () -> router.validate("2026-08"));
        assertThrows(ServiceException.class, () -> router.validate("202613"));
    }

    @Test
    void resolvesBusinessDateInShanghaiTime()
    {
        Date date = Date.from(LocalDate.of(2026, 8, 1)
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());

        assertEquals("202608", router.forBusinessDate(date));
    }

    @Test
    void emptySuffixUsesCurrentBusinessMonth()
    {
        assertEquals(router.currentSuffix(), router.validate(""));
    }
}
