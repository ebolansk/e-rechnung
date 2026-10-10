// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.extract;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.TestData;
import org.junit.jupiter.api.Test;

class PdfTextTest {
    @Test
    void readsTextAndPageCount() throws Exception {
        byte[] pdf = TestData.embeddedFontPdf("Rechnungsnummer: RE-2027-0001");
        var r = PdfText.extract(pdf);
        assertThat(r.text()).contains("Rechnungsnummer: RE-2027-0001");
        assertThat(r.pages()).isEqualTo(1);
    }

    @Test
    void typographicMinusInThePdfBecomesAnAsciiMinusSoNoSignIsLost() throws Exception {
        byte[] pdf = TestData.embeddedFontPdf("Rabatt 1 Stk \u221250,00 EUR");
        var r = PdfText.extract(pdf);
        assertThat(r.text()).contains("-50,00").doesNotContain("\u2212");
        assertThat(r.cells()).anyMatch(c -> c.text().contains("-50,00"));
    }

    @Test
    void garbageThrowsIoException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PdfText.extract("x".getBytes()))
            .isInstanceOf(java.io.IOException.class);
    }
}
