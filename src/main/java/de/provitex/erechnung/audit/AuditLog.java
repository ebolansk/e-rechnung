// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.provitex.erechnung.util.Hashes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class AuditLog implements AuditTrail {
    public static final String GENESIS = "0".repeat(64);
    private static final ObjectMapper LINE = new ObjectMapper();

    public record Entry(long seq, String time, String user, String action,
                        Map<String, String> details, String prev, String hash) {
    }

    public record Verification(boolean valid, long entries, long firstBadSeq, String message) {
    }

    private final Path file;
    private final Clock clock;
    private final String user;
    private long lastSeq;
    private String lastHash = GENESIS;
    private boolean loaded;

    public AuditLog(Path file, Clock clock, String user) {
        this.file = file;
        this.clock = clock;
        this.user = user;
    }

    @Override
    public synchronized Entry append(String action, Map<String, String> details) throws IOException {
        load();
        long seq = lastSeq + 1;
        String time = Instant.now(clock).toString();
        Map<String, String> d = new TreeMap<>(details);
        String hash = hashOf(seq, time, user, action, d, lastHash);
        Entry e = new Entry(seq, time, user, action, d, lastHash, hash);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, LINE.writeValueAsString(e) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        lastSeq = seq;
        lastHash = hash;
        return e;
    }

    /** Alle Einträge in Reihenfolge; unlesbare Zeilen werden übersprungen (die Prüfung in verify() meldet sie). */
    public synchronized List<Entry> entries() throws IOException {
        List<Entry> all = new java.util.ArrayList<>();
        if (!Files.exists(file)) {
            return all;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                try {
                    all.add(LINE.readValue(line, Entry.class));
                } catch (IOException ignored) {
                    // siehe verify()
                }
            }
        }
        return all;
    }

    public synchronized Verification verify() throws IOException {
        if (!Files.exists(file)) {
            return new Verification(true, 0, 0, "Protokoll ist leer.");
        }
        long expectedSeq = 1;
        String prev = GENESIS;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            Entry e;
            try {
                e = LINE.readValue(line, Entry.class);
            } catch (IOException ex) {
                return new Verification(false, expectedSeq - 1, expectedSeq, "Eintrag " + expectedSeq + " ist nicht lesbar.");
            }
            if (e.seq() != expectedSeq) {
                return new Verification(false, expectedSeq - 1, e.seq(),
                    "Folgenummer " + e.seq() + " statt " + expectedSeq + ": Einträge fehlen oder wurden verschoben.");
            }
            if (!prev.equals(e.prev())) {
                return new Verification(false, expectedSeq - 1, e.seq(), "Verkettung bei Eintrag " + e.seq() + " gebrochen.");
            }
            String calc = hashOf(e.seq(), e.time(), e.user(), e.action(), e.details(), e.prev());
            if (!calc.equals(e.hash())) {
                return new Verification(false, expectedSeq - 1, e.seq(), "Eintrag " + e.seq() + " wurde verändert.");
            }
            prev = e.hash();
            expectedSeq++;
        }
        return new Verification(true, expectedSeq - 1, 0, "Protokoll ist unverändert.");
    }

    private void load() throws IOException {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
            .filter(l -> !l.isBlank()).collect(Collectors.toList());
        if (!lines.isEmpty()) {
            Entry last = LINE.readValue(lines.get(lines.size() - 1), Entry.class);
            lastSeq = last.seq();
            lastHash = last.hash();
        }
    }

    static String hashOf(long seq, String time, String user, String action, Map<String, String> details, String prev) {
        String canonicalDetails = new TreeMap<>(details).entrySet().stream()
            .map(e -> e.getKey() + "\u001f" + e.getValue())
            .collect(Collectors.joining("\u001e"));
        String data = seq + "\n" + time + "\n" + user + "\n" + action + "\n" + canonicalDetails + "\n" + prev;
        return Hashes.sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }
}
