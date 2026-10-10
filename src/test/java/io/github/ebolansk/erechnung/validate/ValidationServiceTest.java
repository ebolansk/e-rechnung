// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ValidationServiceTest {
    @Test
    void garbageXmlFailsWithFindings() {
        var report = new ValidationService().validate("<nichts/>".getBytes(StandardCharsets.UTF_8), "x.xml");
        assertThat(report.passed()).isFalse();
        assertThat(report.errors()).isNotEmpty();
    }

    @Test
    void notAnInvoiceFileFails() {
        var report = new ValidationService().validate("kein xml und kein pdf".getBytes(StandardCharsets.UTF_8), "x.bin");
        assertThat(report.passed()).isFalse();
    }
}
