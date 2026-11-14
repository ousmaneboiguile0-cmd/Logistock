package com.logistock.kpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class KpiControllerTest {

    @Test
    void variationPositive() {
        assertEquals(new BigDecimal("25.0"), KpiController.variation(new BigDecimal("500"), new BigDecimal("400")));
    }

    @Test
    void variationNegative() {
        assertEquals(new BigDecimal("-50.0"), KpiController.variation(new BigDecimal("100"), new BigDecimal("200")));
    }

    @Test
    void variationSansMoisPrecedent() {
        assertNull(KpiController.variation(new BigDecimal("100"), BigDecimal.ZERO));
    }
}
