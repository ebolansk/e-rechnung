// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Filtert die Archivsätze; leere Felder der Anfrage filtern nicht. */
public final class ArchiveSearch {
    /** Alle Wörter in {@code text} müssen in Rechnungsnummer, Käufer, Ort oder Mandant vorkommen (ohne Groß-/Kleinschreibung). */
    public record Query(String text, LocalDate from, LocalDate to, BigDecimal minGross, BigDecimal maxGross,
                        String mandantId, String belegStatus, String version, Boolean retentionExpired) {
        public static Query all() {
            return new Query("", null, null, null, null, null, null, null, null);
        }
    }

    private ArchiveSearch() {
    }

    public static List<ArchiveRecord> filter(List<ArchiveRecord> records, Query q, LocalDate today) {
        String[] words = q.text() == null ? new String[0] : q.text().toLowerCase(Locale.GERMAN).trim().split("\\s+");
        return records.stream().filter(r -> {
            String hay = (r.number() + " " + r.buyerName() + " " + r.buyerCity() + " " + r.mandantName()).toLowerCase(Locale.GERMAN);
            for (String w : words) {
                if (!w.isEmpty() && !hay.contains(w)) {
                    return false;
                }
            }
            if (q.from() != null && (r.issueDate() == null || r.issueDate().isBefore(q.from()))) {
                return false;
            }
            if (q.to() != null && (r.issueDate() == null || r.issueDate().isAfter(q.to()))) {
                return false;
            }
            if (q.minGross() != null && (r.gross() == null || r.gross().compareTo(q.minGross()) < 0)) {
                return false;
            }
            if (q.maxGross() != null && (r.gross() == null || r.gross().compareTo(q.maxGross()) > 0)) {
                return false;
            }
            if (q.mandantId() != null && !q.mandantId().isEmpty() && !q.mandantId().equals(r.mandantId())) {
                return false;
            }
            if (q.belegStatus() != null && !q.belegStatus().isEmpty() && !q.belegStatus().equals(r.belegStatus())) {
                return false;
            }
            if (q.version() != null && !q.version().isEmpty() && !r.toolVersion().equals(q.version())) {
                return false;
            }
            if (q.retentionExpired() != null) {
                boolean expired = r.retainUntil() != null && r.retainUntil().isBefore(today);
                if (expired != q.retentionExpired()) {
                    return false;
                }
            }
            return true;
        }).sorted(Comparator.comparing(ArchiveRecord::issueDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ArchiveRecord::number)).toList();
    }
}
