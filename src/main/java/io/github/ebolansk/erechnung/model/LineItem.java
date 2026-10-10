// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.model;

import java.math.BigDecimal;

/** vatCategory: S (Normal), Z (0 %), E (steuerbefreit), AE (Reverse Charge), K, G, O. */
public record LineItem(String name, String description, String unit, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal vatPercent, String vatCategory,
                       String exemptionReason) {
}
