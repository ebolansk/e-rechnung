// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ai;

import de.provitex.erechnung.model.LineItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Vorschlag der KI. fields: Schlüssel der Prüfmaske (z. B. "number", "bName") mit Werten in deutscher Schreibweise.
 * warnings: Schlüssel (auch "items") mit Text der Plausibilitätsprüfung. notes: allgemeine Hinweise.
 */
public record AiSuggestion(Map<String, String> fields, List<LineItem> items, BigDecimal net, BigDecimal tax,
                           BigDecimal gross, String iban, Map<String, String> warnings, List<String> notes) {
}
