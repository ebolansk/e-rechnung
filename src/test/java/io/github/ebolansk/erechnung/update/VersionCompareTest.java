// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionCompareTest {
    @Test
    void comparesNumerically() {
        assertThat(VersionCompare.isNewer("0.2.0", "0.1.0")).isTrue();
        assertThat(VersionCompare.isNewer("v0.10.0", "0.9.5")).isTrue();
        assertThat(VersionCompare.isNewer("1.0", "0.9.9")).isTrue();
        assertThat(VersionCompare.isNewer("0.1.0", "0.1.0")).isFalse();
        assertThat(VersionCompare.isNewer("0.1", "0.1.0")).isFalse();
        assertThat(VersionCompare.isNewer("0.0.9", "0.1.0")).isFalse();
    }

    @Test
    void unreadableVersionsAreNeverNewer() {
        assertThat(VersionCompare.isNewer("nightly", "0.1.0")).isFalse();
        assertThat(VersionCompare.isNewer("0.2.0-beta", "0.1.0")).isFalse();
        assertThat(VersionCompare.isNewer("0.2.0", "dev")).isFalse();
        assertThat(VersionCompare.isNewer(null, "0.1.0")).isFalse();
    }
}
