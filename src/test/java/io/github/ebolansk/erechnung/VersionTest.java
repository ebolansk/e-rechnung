// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionTest {
    /** Die Version steht in pom.xml und in Version.TOOL; ein Abweichen fiele sonst erst beim Release auf. */
    private static String pomVersion() throws Exception {
        String pom = java.nio.file.Files.readString(java.nio.file.Path.of("pom.xml"));
        var m = java.util.regex.Pattern.compile("<version>([^<]+)</version>").matcher(pom);
        m.find();
        return m.group(1);
    }

    @Test
    void toolAndRulesAreSet() throws Exception {
        assertThat(Version.TOOL).as("Version.TOOL und <version> in pom.xml").isEqualTo(pomVersion());
        assertThat(Version.RULES).contains("Mustang-Validator 2.26.0").contains("XRechnung 3.0");
    }
}
