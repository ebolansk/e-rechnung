// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import java.io.IOException;

public interface AiClient {
    /** Sendet Systemanweisung und Nutzertext, liefert den Antworttext des Modells. */
    String complete(String system, String user) throws IOException;

    /** Wie {@link #complete(String, String)}, lässt sich über {@code cancel} abbrechen (dann {@link AiCancelledException}). */
    default String complete(String system, String user, AiCancel cancel) throws IOException {
        if (cancel.isCancelled()) {
            throw new AiCancelledException();
        }
        return complete(system, user);
    }
}
