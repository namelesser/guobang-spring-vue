package com.guobang.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.guobang.transport.common.DateSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RecordMathTest {
    @Test
    void monthBoundsUseClosedOpenRange() {
        var range = DateSupport.monthBounds("2026-12");
        assertThat(range.start().toString()).isEqualTo("2026-12-01");
        assertThat(range.end().toString()).isEqualTo("2027-01-01");
    }

    @Test
    void freightFormulaIncludesDetourSurcharge() {
        BigDecimal total = new BigDecimal("10").multiply(new BigDecimal("20").add(new BigDecimal("3")));
        assertThat(total).isEqualByComparingTo("230");
    }
}
