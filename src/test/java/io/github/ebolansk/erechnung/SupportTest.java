// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SupportTest {
    @Test
    void supportLinkIsAnHttpsAddressOfTheCoffeePage() {
        URI uri = URI.create(Support.COFFEE_URL);
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("buymeacoffee.com");
        assertThat(uri.getPath()).isEqualTo("/ebolansk");
    }
}
