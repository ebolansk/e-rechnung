// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import java.math.BigDecimal;

/**
 * Was der Nutzer in der Prüfmaske ausdrücklich bestätigt hat: die im PDF gelesenen Summen,
 * ob eine Abweichung bewusst bestätigt wurde, und ein überschriebener Beleg-Status (oder null).
 */
public record Confirmation(BigDecimal printedNet, BigDecimal printedTax, BigDecimal printedGross,
                           boolean totalsMismatchConfirmed, BelegStatus belegOverride) {
    public static Confirmation none() {
        return new Confirmation(null, null, null, false, null);
    }
}
