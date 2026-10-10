// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import java.io.IOException;

/** Die Anfrage wurde vom Nutzer abgebrochen (kein Fehler der KI). */
public final class AiCancelledException extends IOException {
    private static final long serialVersionUID = 1L;

    public AiCancelledException() {
        super("Die Anfrage an die KI wurde abgebrochen.");
    }
}
