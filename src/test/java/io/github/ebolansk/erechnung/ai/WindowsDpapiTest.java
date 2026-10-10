// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/** Läuft nur unter Windows (dort in der CI): echter Rundlauf über DPAPI. */
class WindowsDpapiTest {
    @Test
    void roundTripEncryptsAndDecryptsForTheCurrentUser() throws Exception {
        assumeTrue(WindowsDpapi.isWindows(), "DPAPI gibt es nur unter Windows");
        var dpapi = new WindowsDpapi();
        String secret = "sk-ant-äöü-1234";
        String blob = dpapi.protect(secret);
        assertThat(blob).isNotEqualTo(secret).doesNotContain("1234").doesNotContain("sk-ant");
        assertThat(dpapi.unprotect(blob)).isEqualTo(secret);
        assertThat(dpapi.protect(secret)).as("DPAPI salzt: gleicher Klartext, anderer Wert").isNotEqualTo(blob);
    }
}
