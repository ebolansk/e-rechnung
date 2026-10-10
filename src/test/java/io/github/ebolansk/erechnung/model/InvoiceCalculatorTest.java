// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceCalculatorTest {
    private static LineItem item(String qty, String price, String vat, String cat) {
        return new LineItem("Pos", "", "C62", new BigDecimal(qty), new BigDecimal(price),
            new BigDecimal(vat), cat, "");
    }

    @Test
    void singleLineAt19Percent() {
        var t = InvoiceCalculator.compute(List.of(item("3", "100.00", "19", "S")));
        assertThat(t.netTotal()).isEqualByComparingTo("300.00");
        assertThat(t.taxTotal()).isEqualByComparingTo("57.00");
        assertThat(t.grossTotal()).isEqualByComparingTo("357.00");
        assertThat(t.taxLines()).hasSize(1);
    }

    @Test
    void mixedRatesAreGroupedAndRoundedPerRate() {
        var t = InvoiceCalculator.compute(List.of(
            item("2", "10.00", "7", "S"), item("1", "50.00", "19", "S"), item("1", "5.00", "7", "S")));
        assertThat(t.taxLines()).hasSize(2);
        var seven = t.taxLines().stream().filter(l -> l.percent().compareTo(new BigDecimal("7")) == 0).findFirst().orElseThrow();
        assertThat(seven.basis()).isEqualByComparingTo("25.00");
        assertThat(seven.tax()).isEqualByComparingTo("1.75");
        assertThat(t.taxTotal()).isEqualByComparingTo("11.25");
        assertThat(t.grossTotal()).isEqualByComparingTo("86.25");
    }

    @Test
    void lineNetIsRoundedHalfUp() {
        assertThat(InvoiceCalculator.lineNet(item("3", "33.333", "19", "S"))).isEqualByComparingTo("100.00");
        var t = InvoiceCalculator.compute(List.of(item("3", "33.333", "19", "S")));
        assertThat(t.taxTotal()).isEqualByComparingTo("19.00");
    }

    @Test
    void exemptLineHasNoTax() {
        var t = InvoiceCalculator.compute(List.of(item("1", "200.00", "0", "E")));
        assertThat(t.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(t.grossTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void emptyListGivesZeroTotals() {
        var t = InvoiceCalculator.compute(List.of());
        assertThat(t.grossTotal()).isEqualByComparingTo("0.00");
        assertThat(t.taxLines()).isEmpty();
    }
}
