// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionTest {
    @Test
    void toolAndRulesAreSet() {
        assertThat(Version.TOOL).isEqualTo("0.1.0");
        assertThat(Version.RULES).contains("Mustang-Validator 2.26.0").contains("XRechnung 3.0");
    }
}
