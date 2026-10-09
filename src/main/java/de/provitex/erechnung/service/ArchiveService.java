// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import de.provitex.erechnung.archive.ArchiveIndex;
import de.provitex.erechnung.archive.ArchiveRecord;
import de.provitex.erechnung.archive.ArchiveSearch;
import de.provitex.erechnung.archive.IntegrityCheck;
import de.provitex.erechnung.audit.AuditLog;
import de.provitex.erechnung.config.ConfigStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Suche und Integritätsprüfung über dem Archivordner aus der Konfiguration. */
public final class ArchiveService {
    private final Path home;
    private final ConfigStore config;
    private final AuditLog audit;
    private final Clock clock;

    public ArchiveService(Path home, ConfigStore config, AuditLog audit, Clock clock) {
        this.home = home;
        this.config = config;
        this.audit = audit;
        this.clock = clock;
    }

    public Path root() throws IOException {
        return Path.of(config.load(home).archiveRoot());
    }

    public List<ArchiveRecord> records() throws IOException {
        return ArchiveIndex.scan(root()).records();
    }

    public List<ArchiveRecord> search(ArchiveSearch.Query q) throws IOException {
        return ArchiveSearch.filter(records(), q, LocalDate.now(clock));
    }

    /** Prüft das Archiv und hält Zeitpunkt und Ergebnis im Protokoll fest. */
    public IntegrityCheck.Report check() throws IOException {
        IntegrityCheck.Report report = IntegrityCheck.run(root(), audit);
        Map<String, String> d = new LinkedHashMap<>();
        d.put("ergebnis", report.ok() ? "in Ordnung" : "Abweichungen");
        d.put("rechnungen", String.valueOf(report.invoices()));
        d.put("fehler", String.valueOf(report.errors()));
        d.put("hinweise", String.valueOf(report.findings().size() - report.errors()));
        d.put("protokollKette", report.log().valid() ? "gültig" : "gebrochen");
        audit.append("integritaetspruefung", d);
        return report;
    }
}
