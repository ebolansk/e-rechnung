// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SelfTestTest {
    @Test
    void bundledSampleRunsThroughBothFormats() {
        assertThat(SelfTest.run()).isZero();
    }
}
