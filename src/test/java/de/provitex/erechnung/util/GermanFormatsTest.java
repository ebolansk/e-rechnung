// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GermanFormatsTest {
    @Test
    void parsesGermanAndEnglishAmounts() {
        assertThat(GermanFormats.parseAmount("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(GermanFormats.parseAmount("1234,56")).isEqualByComparingTo("1234.56");
        assertThat(GermanFormats.parseAmount("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(GermanFormats.parseAmount("0,5")).isEqualByComparingTo("0.5");
        assertThat(GermanFormats.parseAmount("-12,50")).isEqualByComparingTo("-12.50");
        assertThat(GermanFormats.parseAmount("€ 12,50")).isEqualByComparingTo("12.50");
        assertThat(GermanFormats.parseAmount("12,50 EUR")).isEqualByComparingTo("12.50");
        assertThat(GermanFormats.parseAmount("12.345")).isEqualByComparingTo("12345");
        assertThat(GermanFormats.parseAmount("1.234.567,89")).isEqualByComparingTo("1234567.89");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> GermanFormats.parseAmount("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GermanFormats.parseAmount("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesDates() {
        assertThat(GermanFormats.parseDate("15.01.2027")).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(GermanFormats.parseDate("5.1.2027")).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(GermanFormats.parseDate("15.01.27")).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(GermanFormats.parseDate("2027-01-15")).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(GermanFormats.parseDate("15. Januar 2027")).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(GermanFormats.parseDate("3. März 2027")).isEqualTo(LocalDate.of(2027, 3, 3));
        assertThatThrownBy(() -> GermanFormats.parseDate("32.13.2027")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesIbanAndFormatsAmounts() {
        assertThat(GermanFormats.normalizeIban("de02 1203 0000 0000 2020 51")).isEqualTo("DE02120300000000202051");
        assertThat(GermanFormats.formatAmount(new java.math.BigDecimal("1234567.5"))).isEqualTo("1.234.567,50");
    }
}
