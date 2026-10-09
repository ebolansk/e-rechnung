// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import de.provitex.erechnung.archive.ArchiveIndex;
import de.provitex.erechnung.archive.ArchiveInput;
import de.provitex.erechnung.archive.ArchiveLayout;
import de.provitex.erechnung.archive.ArchiveWriter;
import de.provitex.erechnung.archive.BelegStatus;
import de.provitex.erechnung.archive.Confirmation;
import de.provitex.erechnung.audit.AuditTrail;
import de.provitex.erechnung.extract.ExtractionResult.PrintedTotals;
import de.provitex.erechnung.model.InvoiceCalculator;
import de.provitex.erechnung.config.AppConfig;
import de.provitex.erechnung.config.ConfigStore;
import de.provitex.erechnung.extract.ExtractionResult;
import de.provitex.erechnung.extract.PdfText;
import de.provitex.erechnung.extract.RuleBasedExtractor;
import de.provitex.erechnung.generate.PdfaNotPossibleException;
import de.provitex.erechnung.generate.XRechnungGenerator;
import de.provitex.erechnung.generate.ZugferdGenerator;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.mandant.MandantMatcher;
import de.provitex.erechnung.mandant.MandantStore;
import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.OutputFormat;
import de.provitex.erechnung.util.Hashes;
import de.provitex.erechnung.validate.ProtocolHtml;
import de.provitex.erechnung.validate.ValidationReport;
import de.provitex.erechnung.validate.ValidationService;
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
        List<String> problems = new ArrayList<>(InvoiceChecks.missingFields(d));
        if (!problems.isEmpty()) {
            return new Outcome(false, null, null, problems);
        }
        List<String> mismatch = InvoiceChecks.totalsMismatch(InvoiceCalculator.compute(d.items()),
            new PrintedTotals(confirmation.printedNet(), confirmation.printedTax(), confirmation.printedGross()));
        if (!mismatch.isEmpty() && !confirmation.totalsMismatchConfirmed()) {
            problems.add("Die Summen im PDF weichen ab und wurden nicht ausdrücklich bestätigt: " + String.join(" ", mismatch));
            return new Outcome(false, null, null, problems);
        }
        AppConfig cfg = configStore.load(home);
        Path root = Path.of(cfg.archiveRoot());
        Path target = ArchiveLayout.resolve(root, cfg.folderTemplate(), m.name(), d.issueDate(), d.number());
        ArchiveIndex index = ArchiveIndex.scan(root);
        if (index.findByNumber(m.id(), d.number()).isPresent() || Files.exists(target)) {
            problems.add("Die Rechnung " + d.number() + " ist für diesen Mandanten bereits archiviert und wird nicht überschrieben.");
            return new Outcome(false, null, null, problems);
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
            return new Outcome(false, null, null, problems);
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
            return new Outcome(false, null, report, problems);
        }
        BelegStatus.BelegAssessment beleg = BelegStatus.assess(d.issueDate(), d.deliveryDate(), d.deliveryPeriodEnd(), cfg.cutoffDate());
        ArchiveInput in = new ArchiveInput(m.id(), m.name(), d, cfg.outputFormat(), p.pdf(), p.fileName(), output, outputName,
            protocol, report, beleg, confirmedBy, now, confirmation);
        ArchiveWriter.ArchiveResult result;
        try {
            result = new ArchiveWriter().write(target, in);
        } catch (FileAlreadyExistsException e) {
            problems.add(e.getReason() != null ? e.getReason() : "Archiveintrag existiert bereits.");
            return new Outcome(false, null, report, problems);
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
        return new Outcome(true, target, report, List.copyOf(notes));
    }
}
