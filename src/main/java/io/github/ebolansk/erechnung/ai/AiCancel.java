// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

/** Abbruch einer laufenden KI-Anfrage: die Oberfläche ruft {@link #cancel()}, der Client schließt dann sofort die Verbindung. */
public final class AiCancel {
    private volatile boolean cancelled;
    private volatile Runnable hook;

    public void cancel() {
        cancelled = true;
        Runnable h = hook;
        if (h != null) {
            h.run();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Der Client meldet an, was beim Abbruch zu tun ist (Verbindung schließen); war schon abgebrochen, geschieht es sofort. */
    void onCancel(Runnable action) {
        hook = action;
        if (cancelled) {
            action.run();
        }
    }
}
