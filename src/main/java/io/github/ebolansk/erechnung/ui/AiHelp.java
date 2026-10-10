// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.service.AiAssistService;

/** Alles, was die Prüfmaske für „Mit KI nachbessern“ braucht: Dienst, Rechnungstext, Dateiname. */
public record AiHelp(AiAssistService service, String text, String fileName) {
}
