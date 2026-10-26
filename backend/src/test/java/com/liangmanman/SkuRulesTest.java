package com.liangmanman;

import com.liangmanman.service.SkuService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** “同一款”的判断依据：条码、登记证号、规格的统一写法 */
class SkuRulesTest {

    @Test
    void normalize() {
        assertEquals("50kg", SkuService.normSpec("50 千克"));
        assertEquals("50kg", SkuService.normSpec("50KG"));
        assertEquals("40kg", SkuService.normSpec("40公斤"));
        assertEquals("500ml", SkuService.normSpec("500毫升"));
        assertEquals("500ml", SkuService.normSpec("500ml"));
        assertEquals("1L", SkuService.normSpec("1升"));
        assertEquals("100g", SkuService.normSpec("100克"));
        assertEquals("1卷", SkuService.normSpec("1卷"));
        assertEquals("农肥(2024)准字0001号", SkuService.normRegNo("农肥（2024）准字 0001号"));
        assertEquals("PD20180001", SkuService.normRegNo("pd 20180001"));
        assertEquals("6901234567892", SkuService.normBarcode(" 6901234567892 "));
        assertNull(SkuService.normBarcode("69012345678a2"));
    }

    @Test
    void ean() {
        assertTrue(SkuService.validEan("6901234567892"));
        assertFalse(SkuService.validEan("6901234567893"));
        assertTrue(SkuService.validEan("96385074"));
    }
}
