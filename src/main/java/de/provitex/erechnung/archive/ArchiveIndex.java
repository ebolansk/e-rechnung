// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class ArchiveIndex {
    public record Entry(Path dir, String mandantId, String number, String originalSha256) {
    }

    private final List<ArchiveRecord> records;
    private final List<Entry> entries;

    private ArchiveIndex(List<ArchiveRecord> records) {
        this.records = records;
        this.entries = records.stream().map(ArchiveRecord::toEntry).toList();
    }

    public static ArchiveIndex scan(Path root) throws IOException {
        List<ArchiveRecord> found = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.walk(root, 10)) {
                for (Path p : (Iterable<Path>) s.filter(x -> x.getFileName().toString().equals("daten.json"))::iterator) {
                    try {
                        found.add(ArchiveRecord.parse(p));
                    } catch (IOException | RuntimeException ignored) {
                        // Unlesbare Einträge überspringen; die Integritätsprüfung meldet sie.
                    }
                }
            }
        }
        return new ArchiveIndex(found);
    }

    public List<ArchiveRecord> records() {
        return records;
    }

    public int size() {
        return records.size();
    }

    public Optional<Entry> findByNumber(String mandantId, String number) {
        return entries.stream().filter(e -> e.mandantId().equals(mandantId) && e.number().equals(number)).findFirst();
    }

    public Optional<Entry> findByOriginalHash(String sha256) {
        return entries.stream().filter(e -> !e.originalSha256().isEmpty() && e.originalSha256().equals(sha256)).findFirst();
    }
}
