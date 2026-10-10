// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ebolansk.erechnung.TestData;
import io.github.ebolansk.erechnung.validate.ValidationService;
import org.junit.jupiter.api.Test;

class ZugferdGeneratorTest {
    private final ZugferdGenerator generator = new ZugferdGenerator();

    @Test
    void embeddedFontPdfBecomesValidZugferd() throws Exception {
        byte[] src = TestData.embeddedFontPdf("Rechnung RE-2027-0001 - Summe 357,00 EUR");
        byte[] out = generator.generate(TestData.invoice(), src);
        var report = new ValidationService().validate(out, "zugferd.pdf");
        assertThat(report.errors()).as("Befunde: %s", report.errors()).isEmpty();
        assertThat(report.pdfValid()).isTrue();
        assertThat(report.xmlValid()).isTrue();
        assertThat(report.passed()).isTrue();
    }

    @Test
    void notEmbeddedFontsAreRejectedAndNothingIsProduced() throws Exception {
        byte[] src = TestData.base14Pdf("Rechnung");
        assertThatThrownBy(() -> generator.generate(TestData.invoice(), src))
            .isInstanceOf(PdfaNotPossibleException.class);
    }
}
