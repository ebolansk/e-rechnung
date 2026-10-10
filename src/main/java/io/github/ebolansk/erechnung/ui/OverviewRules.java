// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.util.ArrayList;
import java.util.List;

/** Regeln der Übersichtstabelle auf der Startseite: Fortschritt je PDF und welche Zeilen mit Entf entfernt werden dürfen. */
final class OverviewRules {
    static final String WAITING = "Wartet";
    static final String READING = "In Bearbeitung";
    static final String REVIEW = "In Prüfung";
    static final String GENERATING = "Wird erzeugt …";
    static final String ARCHIVED = "Archiviert";
    static final String READY = "Erzeugt";
    static final String ARCHIVING = "Wird archiviert …";
    static final String DUPLICATE = "Bereits archiviert";
    static final String CANCELLED = "Abgebrochen";

    enum Kind { ACTIVE, READY, DONE, NEUTRAL, CANCELLED, FAILED }

    private OverviewRules() {
    }

    static int percent(String status) {
        return switch (status) {
            case WAITING -> 0;
            case READING -> 25;
            case REVIEW -> 50;
            case GENERATING -> 75;
            case READY -> 90;
            case ARCHIVING -> 95;
            case ARCHIVED, DUPLICATE -> 100;
            case CANCELLED -> 0;
            default -> 100;
        };
    }

    static Kind kind(String status) {
        return switch (status) {
            case WAITING, READING, REVIEW, GENERATING, ARCHIVING -> Kind.ACTIVE;
            case READY -> Kind.READY;
            case ARCHIVED -> Kind.DONE;
            case DUPLICATE -> Kind.NEUTRAL;
            case CANCELLED -> Kind.CANCELLED;
            default -> Kind.FAILED;
        };
    }

    /**
     * Zeilen, die „Löschen“ (Entf, Kontextmenü) entfernt: alle abgeschlossenen, aber nur wenn nichts mehr verarbeitet wird (laufende
     * Vorgänge halten die Zeilennummer ihrer Tabellenzeile). Absteigend sortiert, damit sich beim Entfernen keine Nummern verschieben.
     */
    static List<Integer> removable(List<String> statusByRow, int[] selectedRows, boolean busy) {
        List<Integer> rows = new ArrayList<>();
        if (busy) {
            return rows;
        }
        for (int r : selectedRows) {
            if (r >= 0 && r < statusByRow.size() && kind(statusByRow.get(r)) != Kind.ACTIVE) {
                rows.add(r);
            }
        }
        rows.sort(java.util.Comparator.reverseOrder());
        return rows;
    }

    /** Zeilen, die „Ins Archiv übernehmen“ aufnimmt: erzeugte Entwürfe, in Tabellenreihenfolge. */
    static List<Integer> adoptable(List<String> statusByRow, int[] selectedRows) {
        List<Integer> rows = new ArrayList<>();
        for (int r : selectedRows) {
            if (r >= 0 && r < statusByRow.size() && READY.equals(statusByRow.get(r))) {
                rows.add(r);
            }
        }
        rows.sort(java.util.Comparator.naturalOrder());
        return rows;
    }

    /** Alle Zeilen angehakt? (Zustand der Master-Checkbox; leere Tabelle gilt als nicht angehakt.) */
    static boolean allChecked(List<Boolean> checked) {
        return !checked.isEmpty() && checked.stream().allMatch(Boolean.TRUE::equals);
    }

    /** Die angehakten Zeilen; ist keine angehakt, gelten die markierten (hervorgehobenen) Zeilen. */
    static int[] target(List<Boolean> checked, int[] highlighted) {
        int[] ticked = java.util.stream.IntStream.range(0, checked.size()).filter(i -> Boolean.TRUE.equals(checked.get(i))).toArray();
        return ticked.length > 0 ? ticked : highlighted;
    }

    /** Zeilen, die „Erneut verarbeiten“ sinnvoll aufnehmen: abgebrochene und fehlgeschlagene (aufsteigend, in Reihenfolge der Tabelle). */
    static List<Integer> reprocessable(List<String> statusByRow, int[] selectedRows) {
        List<Integer> rows = new ArrayList<>();
        for (int r : selectedRows) {
            if (r >= 0 && r < statusByRow.size()) {
                Kind k = kind(statusByRow.get(r));
                if (k == Kind.CANCELLED || k == Kind.FAILED) {
                    rows.add(r);
                }
            }
        }
        rows.sort(java.util.Comparator.naturalOrder());
        return rows;
    }
}
