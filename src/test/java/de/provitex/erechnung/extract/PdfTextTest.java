// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.extract;

import static org.assertj.core.api.Assertions.assertThat;

import de.provitex.erechnung.TestData;
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
    void garbageThrowsIoException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PdfText.extract("x".getBytes()))
            .isInstanceOf(java.io.IOException.class);
    }
}
