// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileNamesTest {
    @Test
    void replacesUmlautsAndIllegalCharacters() {
        assertThat(FileNames.sanitize("Müller & Söhne GmbH")).isEqualTo("Mueller _ Soehne GmbH");
        assertThat(FileNames.sanitize("Straße")).isEqualTo("Strasse");
        assertThat(FileNames.sanitize("RE/2027/1")).isEqualTo("RE_2027_1");
        assertThat(FileNames.sanitize("a:b*c?d\"e<f>g|h")).isEqualTo("a_b_c_d_e_f_g_h");
    }

    @Test
    void neverReturnsPathTraversal() {
        String s = FileNames.sanitize("..\\..\\evil");
        assertThat(s).isEqualTo("_.._evil");
        assertThat(FileNames.sanitize("..")).isEqualTo("_");
        assertThat(FileNames.sanitize("../x")).doesNotContain("/").doesNotStartWith(".");
    }

    @Test
    void avoidsReservedWindowsNames() {
        assertThat(FileNames.sanitize("CON")).isEqualTo("CON_");
        assertThat(FileNames.sanitize("nul.txt")).isEqualTo("nul_.txt");
        assertThat(FileNames.sanitize("COM1")).isEqualTo("COM1_");
    }

    @Test
    void handlesEmptyAndLong() {
        assertThat(FileNames.sanitize("")).isEqualTo("_");
        assertThat(FileNames.sanitize("   ")).isEqualTo("_");
        assertThat(FileNames.sanitize(null)).isEqualTo("_");
        assertThat(FileNames.sanitize("x".repeat(300))).hasSize(80);
        assertThat(FileNames.sanitize("Name. ")).isEqualTo("Name");
    }
}
