// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import java.util.List;
import java.util.Map;

/**
 * Vorschlag der KI für die Stammdaten eines Rechnungsausstellers. fields: name, street, zip, city, country, vatId, taxNumber,
 * email, phone, contactName, iban, bic (nur was gefunden wurde). warnings: Schlüssel mit Text der Plausibilitätsprüfung.
 */
public record SellerSuggestion(Map<String, String> fields, Map<String, String> warnings, List<String> notes) {
}
