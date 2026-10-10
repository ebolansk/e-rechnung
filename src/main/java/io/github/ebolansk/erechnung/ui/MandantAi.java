// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.service.AiAssistService;

/**
 * KI-Hilfe für die Stammdaten eines Rechnungsausstellers. text/fileName: die Rechnung, aus der gelesen wird; ist text null
 * (Bearbeiten eines vorhandenen Ausstellers), lässt das Formular eine Rechnungs-PDF auswählen.
 */
record MandantAi(AiAssistService service, String text, String fileName) {
}
