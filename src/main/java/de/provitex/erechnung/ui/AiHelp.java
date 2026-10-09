// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.service.AiAssistService;

/** Alles, was die Prüfmaske für „Mit KI nachbessern“ braucht: Dienst, Rechnungstext, Dateiname. */
public record AiHelp(AiAssistService service, String text, String fileName) {
}
