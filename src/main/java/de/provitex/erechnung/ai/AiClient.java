// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ai;

import java.io.IOException;

public interface AiClient {
    /** Sendet Systemanweisung und Nutzertext, liefert den Antworttext des Modells. */
    String complete(String system, String user) throws IOException;
}
