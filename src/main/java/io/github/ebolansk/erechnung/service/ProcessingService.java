// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import io.github.ebolansk.erechnung.archive.ArchiveIndex;
import io.github.ebolansk.erechnung.archive.ArchiveInput;
import io.github.ebolansk.erechnung.archive.ArchiveLayout;
import io.github.ebolansk.erechnung.archive.ArchiveWriter;
import io.github.ebolansk.erechnung.archive.BelegStatus;
import io.github.ebolansk.erechnung.archive.Confirmation;
import io.github.ebolansk.erechnung.audit.AuditTrail;
import io.github.ebolansk.erechnung.extract.ExtractionResult.PrintedTotals;
import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.extract.ExtractionResult;
import io.github.ebolansk.erechnung.extract.PdfText;
import io.github.ebolansk.erechnung.extract.RuleBasedExtractor;
import io.github.ebolansk.erechnung.generate.GeneratedAmounts;
import io.github.ebolansk.erechnung.generate.PdfaNotPossibleException;
import io.github.ebolansk.erechnung.generate.XRechnungGenerator;
import io.github.ebolansk.erechnung.generate.ZugferdGenerator;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantMatcher;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.util.Hashes;
import io.github.ebolansk.erechnung.validate.ProtocolHtml;
import io.github.ebolansk.erechnung.validate.ValidationReport;
import io.github.ebolansk.erechnung.validate.ValidationService;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProcessingService {
    public record Prepared(String fileName, byte[] pdf, String sha256, String text, int pages,
                           Optional<Mandant> mandant, ExtractionResult extraction,
                           Optional<ArchiveIndex.Entry> duplicateByHash, List<PdfText.Cell> cells) {
    }

    public record Outcome(boolean archived, Path directory, ValidationReport report, List<String> problems) {
    }

    /** Eine erzeugte und formal geprüfte E-Rechnung, die noch nicht im Archiv liegt (Dateien in stagingDir zum Ansehen). */
    public record Draft(Prepared prepared, Mandant mandant, InvoiceData data, Confirmation confirmation, String confirmedBy,
                        OutputFormat format, byte[] output, String outputName, String protocolHtml, ValidationReport report,
                        Instant createdAt, Path stagingDir) {
        public Path outputFile() {
            return stagingDir.resolve(outputName);
        }

        public Path protocolFile() {
            return stagingDir.resolve("pruefprotokoll.html");
        }
    }

    /** Ergebnis von {@link #generate}: entweder ein Entwurf oder die Gründe, warum keiner entstanden ist. */
    public record Generated(Draft draft, ValidationReport report, List<String> problems) {
    }

    private final Path home;
    private final ConfigStore configStore;
    private final MandantStore mandanten;
    private final AuditTrail audit;
    private final Clock clock;
    private final ValidationService validator = new ValidationService();
    private final XRechnungGenerator xrechnung = new XRechnungGenerator();
    private final ZugferdGenerator zugferd = new ZugferdGenerator();

    public ProcessingService(Path home, ConfigStore configStore, MandantStore mandanten, AuditTrail audit, Clock clock) {
        this.home = home;
        this.configStore = configStore;
        this.mandanten = mandanten;
        this.audit = audit;
        this.clock = clock;
    }

    public Prepared prepare(Path pdfFile) throws IOException {
        byte[] pdf = Files.readAllBytes(pdfFile);
        String sha = Hashes.sha256Hex(pdf);
        PdfText.Result text = PdfText.extract(pdf);
        Optional<Mandant> m = MandantMatcher.match(mandanten.all(), text.text());
        ExtractionResult extraction = RuleBasedExtractor.extract(text.text(), text.cells(), m.orElse(null));
        AppConfig cfg = configStore.load(home);
        Optional<ArchiveIndex.Entry> dup = ArchiveIndex.scan(Path.of(cfg.archiveRoot())).findByOriginalHash(sha);
        audit.append("importiert", Map.of("datei", pdfFile.getFileName().toString(), "sha256", sha,
            "mandant", m.map(Mandant::id).orElse("unbekannt")));
        return new Prepared(pdfFile.getFileName().toString(), pdf, sha, text.text(), text.pages(), m, extraction, dup, text.cells());
    }

    /** Speichert geänderte Stammdaten eines Ausstellers (zum Beispiel aus dem Tab „Verkäufer“ der Prüfmaske) und protokolliert es. */
    public Mandant updateMandant(Mandant m) throws IOException {
        mandanten.update(m);
        audit.append("mandant-geaendert", Map.of("id", m.id(), "name", m.name()));
        return m;
    }

    public Mandant registerMandant(Mandant m) throws IOException {
        Mandant saved = mandanten.add(m);
        audit.append("mandant-angelegt", Map.of("id", saved.id(), "name", saved.name()));
        return saved;
    }

    /** Ohne ausdrückliche Bestätigung: die im PDF gelesenen Summen müssen mit den berechneten übereinstimmen. */
    public Outcome generateAndArchive(Prepared p, Mandant m, InvoiceData d, String confirmedBy) throws IOException {
        PrintedTotals printed = p.extraction().printed();
        return generateAndArchive(p, m, d, confirmedBy,
            new Confirmation(printed.net(), printed.tax(), printed.gross(), false, null));
    }

    public Outcome generateAndArchive(Prepared p, Mandant m, InvoiceData d, String confirmedBy, Confirmation confirmation)
        throws IOException {
        Generated g = generate(p, m, d, confirmedBy, confirmation);
        if (g.draft() == null) {
            return new Outcome(false, null, g.report(), g.problems());
        }
        return archive(g.draft());
    }

    /**
     * Erzeugt die E-Rechnung und prüft sie formal, legt sie aber nur als Entwurf ab (daten/entwurf/…): zum Ansehen und Prüfen.
     * Ins Archiv kommt sie erst mit {@link #archive(Draft)}.
     */
    public Generated generate(Prepared p, Mandant m, InvoiceData d, String confirmedBy, Confirmation confirmation) throws IOException {
        AppConfig cfg = configStore.load(home);
        List<String> problems = new ArrayList<>(InvoiceChecks.missingFields(d, cfg.outputFormat()));
        if (!problems.isEmpty()) {
            return new Generated(null, null, problems);
        }
        List<String> mismatch = InvoiceChecks.totalsMismatch(InvoiceCalculator.compute(d.items()),
            new PrintedTotals(confirmation.printedNet(), confirmation.printedTax(), confirmation.printedGross()));
        if (!mismatch.isEmpty() && !confirmation.totalsMismatchConfirmed()) {
            problems.add("Die Summen im PDF weichen ab und wurden nicht ausdrücklich bestätigt: " + String.join(" ", mismatch));
            return new Generated(null, null, problems);
        }
        Path root = Path.of(cfg.archiveRoot());
        if (alreadyArchived(root, cfg, m, d)) {
            problems.add("Die Rechnung " + d.number() + " ist für diesen Mandanten bereits archiviert und wird nicht überschrieben.");
            return new Generated(null, null, problems);
        }
        byte[] output;
        String outputName;
        try {
            if (cfg.outputFormat() == OutputFormat.ZUGFERD) {
                output = zugferd.generate(d, p.pdf());
                outputName = "ausgabe-zugferd.pdf";
            } else {
                output = xrechnung.generate(d);
                outputName = "ausgabe-xrechnung.xml";
            }
        } catch (PdfaNotPossibleException e) {
            problems.add(e.getMessage());
            audit.append("erzeugung-abgelehnt", Map.of("rechnung", d.number(), "grund", e.getMessage()));
            return new Generated(null, null, problems);
        }
        List<String> amountDiffs = GeneratedAmounts.differences(output, cfg.outputFormat() == OutputFormat.ZUGFERD, d.items());
        if (!amountDiffs.isEmpty()) {
            // Der Validator prüft nur die Selbstkonsistenz des XML: Weicht es von den bestätigten Summen ab, wird nichts abgelegt.
            String reason = "Die Beträge in der erzeugten E-Rechnung weichen von den bestätigten Summen ab: " + String.join(" ", amountDiffs);
            problems.add(reason);
            audit.append("erzeugung-abgelehnt", Map.of("rechnung", d.number(), "grund", reason));
            return new Generated(null, null, problems);
        }
        ValidationReport report = validator.validate(output, outputName);
        String formatLabel = cfg.outputFormat() == OutputFormat.ZUGFERD ? "ZUGFeRD (EN 16931)" : "XRechnung";
        Instant now = Instant.now(clock);
        String protocol = ProtocolHtml.render(new ProtocolHtml.Input(outputName, formatLabel, m.name(), d.number(), report, now));
        Map<String, String> auditDetails = new LinkedHashMap<>();
        auditDetails.put("rechnung", d.number());
        auditDetails.put("mandant", m.id());
        auditDetails.put("ergebnis", report.passed() ? "bestanden" : "nicht bestanden");
        auditDetails.put("validator", report.validatorVersion());
        audit.append("validiert", auditDetails);
        if (!report.passed()) {
            report.errors().forEach(f -> problems.add(f.ruleId() + ": " + f.message()));
            return new Generated(null, report, problems);
        }
        Path stage = home.resolve("daten").resolve("entwurf").resolve(java.util.UUID.randomUUID().toString());
        Files.createDirectories(stage);
        Files.write(stage.resolve(outputName), output);
        Files.writeString(stage.resolve("pruefprotokoll.html"), protocol, java.nio.charset.StandardCharsets.UTF_8);
        Draft draft = new Draft(p, m, d, confirmation, confirmedBy, cfg.outputFormat(), output, outputName, protocol, report, now, stage);
        return new Generated(draft, report, List.of());
    }

    private boolean alreadyArchived(Path root, AppConfig cfg, Mandant m, InvoiceData d) throws IOException {
        Path target = ArchiveLayout.resolve(root, cfg.folderTemplate(), m.name(), d.issueDate(), d.number());
        return ArchiveIndex.scan(root).findByNumber(m.id(), d.number()).isPresent() || Files.exists(target);
    }

    /** Übernimmt einen erzeugten Entwurf ins Archiv (unveränderlich, mit Hashes und Protokolleintrag) und räumt den Entwurf auf. */
    public Outcome archive(Draft draft) throws IOException {
        AppConfig cfg = configStore.load(home);
        Mandant m = draft.mandant();
        InvoiceData d = draft.data();
        List<String> problems = new ArrayList<>();
        Path root = Path.of(cfg.archiveRoot());
        Path target = ArchiveLayout.resolve(root, cfg.folderTemplate(), m.name(), d.issueDate(), d.number());
        if (alreadyArchived(root, cfg, m, d)) {
            problems.add("Die Rechnung " + d.number() + " ist für diesen Mandanten bereits archiviert und wird nicht überschrieben.");
            return new Outcome(false, null, draft.report(), problems);
        }
        BelegStatus.BelegAssessment beleg = BelegStatus.assess(d.issueDate(), d.deliveryDate(), d.deliveryPeriodEnd(), cfg.cutoffDate());
        Confirmation confirmation = draft.confirmation();
        ArchiveInput in = new ArchiveInput(m.id(), m.name(), d, draft.format(), draft.prepared().pdf(), draft.prepared().fileName(),
            draft.output(), draft.outputName(), draft.protocolHtml(), draft.report(), beleg, draft.confirmedBy(), draft.createdAt(),
            confirmation);
        ArchiveWriter.ArchiveResult result;
        try {
            result = new ArchiveWriter().write(target, in);
        } catch (FileAlreadyExistsException e) {
            problems.add(e.getReason() != null ? e.getReason() : "Archiveintrag existiert bereits.");
            return new Outcome(false, null, draft.report(), problems);
        } catch (IOException e) {
            throw new IOException("Das Archiv konnte nicht geschrieben werden (" + target + "): " + e.getMessage(), e);
        }
        var effective = confirmation.belegOverride() != null ? confirmation.belegOverride() : beleg.status();
        Map<String, String> done = new LinkedHashMap<>();
        done.put("rechnung", d.number());
        done.put("mandant", m.id());
        done.put("pfad", target.toString());
        done.put("belegStatus", effective.name());
        done.put("belegStatusOverridden", String.valueOf(effective != beleg.status()));
        done.put("totalsMismatchConfirmed", String.valueOf(confirmation.totalsMismatchConfirmed()));
        result.sha256ByFile().forEach((k, v) -> done.put("sha256:" + k, v));
        List<String> notes = new ArrayList<>(beleg.warnings());
        try {
            audit.append("archiviert", done);
        } catch (IOException e) {
            notes.add("Die Rechnung ist archiviert, aber der Protokolleintrag konnte nicht geschrieben werden: " + e.getMessage()
                + ". Bitte das Protokoll (daten/protokoll.jsonl) prüfen und den Vorgang dokumentieren.");
        }
        removeStaging(draft);
        return new Outcome(true, target, draft.report(), List.copyOf(notes));
    }

    /** Verwirft einen Entwurf, der nicht ins Archiv soll (löscht die Entwurfsdateien, protokolliert es). */
    public void discard(Draft draft) throws IOException {
        removeStaging(draft);
        audit.append("entwurf-verworfen", Map.of("rechnung", draft.data().number(), "mandant", draft.mandant().id()));
    }

    /** Beim Programmstart: Entwürfe einer früheren Sitzung gibt es nicht mehr (sie wurden nicht archiviert). */
    public void cleanupDrafts() {
        try {
            io.github.ebolansk.erechnung.launcher.Swap.deleteTree(home.resolve("daten").resolve("entwurf"));
        } catch (IOException ignored) {
            // bleibt liegen, der nächste Start räumt erneut auf
        }
    }

    private static void removeStaging(Draft draft) {
        try {
            io.github.ebolansk.erechnung.launcher.Swap.deleteTree(draft.stagingDir());
        } catch (IOException ignored) {
            // gesperrte Datei: wird beim nächsten Start aufgeräumt
        }
    }
}
