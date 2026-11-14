package com.logistock.ventes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FactureServiceTest {

    @Test
    void formatMontantFrancais() {
        assertEquals("357,30", FactureService.argent(new BigDecimal("357.30")));
        assertEquals("1 234,50", FactureService.argent(new BigDecimal("1234.5")));
        assertEquals("0,00", FactureService.argent(BigDecimal.ZERO));
    }
}
