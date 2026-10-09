// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.provitex.erechnung.TestData;
import org.junit.jupiter.api.Test;

class PdfaPreparerTest {
    private final PdfaPreparer preparer = new PdfaPreparer();

    @Test
    void rejectsNotEmbeddedFontsWithNameInMessage() throws Exception {
        byte[] pdf = TestData.base14Pdf("Rechnung");
        assertThatThrownBy(() -> preparer.prepare(pdf, "Rechnung 1"))
            .isInstanceOf(PdfaNotPossibleException.class)
            .hasMessageContaining("Helvetica")
            .hasMessageContaining("eingebettet");
    }

    @Test
    void embeddedFontPdfIsConvertedToPdfA3() throws Exception {
        byte[] src = TestData.embeddedFontPdf("Rechnung RE-1 für Müller");
        byte[] fixed = preparer.prepare(src, "Rechnung RE-1");
        assertThat(fixed).isNotEqualTo(src);
        assertThat(new String(fixed, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("pdfaid:part");
    }

    @Test
    void alreadyPdfA3IsPassedThroughUnchanged() throws Exception {
        byte[] fixed = preparer.prepare(TestData.embeddedFontPdf("x"), "t");
        assertThat(preparer.prepare(fixed, "t")).isSameAs(fixed);
    }

    @Test
    void garbageIsRejectedWithMessage() {
        assertThatThrownBy(() -> preparer.prepare("kein pdf".getBytes(), "t"))
            .isInstanceOf(PdfaNotPossibleException.class)
            .hasMessageContaining("PDF");
    }
}
