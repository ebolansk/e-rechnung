// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ebolansk.erechnung.Version;
import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.util.Hashes;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ArchiveWriter {
    public record ArchiveResult(Path directory, Map<String, String> sha256ByFile) {
    }

    public ArchiveResult write(Path target, ArchiveInput in) throws IOException {
        Objects.requireNonNull(in.output(), "output");
        Objects.requireNonNull(in.originalPdf(), "originalPdf");
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString(), null,
                "Für diese Rechnung gibt es bereits einen Archiveintrag. Archivierte Rechnungen werden nie überschrieben.");
        }
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = parent.resolve("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectory(tmp);
            Map<String, String> hashes = new LinkedHashMap<>();
            put(tmp, "vorlage-original.pdf", in.originalPdf(), hashes);
            put(tmp, in.outputFileName(), in.output(), hashes);
            put(tmp, "pruefprotokoll.html", in.protocolHtml().getBytes(StandardCharsets.UTF_8), hashes);
            Files.write(tmp.resolve("daten.json"), Json.mapper().writeValueAsBytes(dataJson(in, hashes)));
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target);
            }
            hashes.put("daten.json", Hashes.sha256Hex(target.resolve("daten.json")));
            return new ArchiveResult(target, hashes);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tmp);
            throw e;
        }
    }

    private static void put(Path dir, String name, byte[] data, Map<String, String> hashes) throws IOException {
        Files.write(dir.resolve(name), data);
        hashes.put(name, Hashes.sha256Hex(data));
    }

    private static ObjectNode dataJson(ArchiveInput in, Map<String, String> hashes) {
        var m = Json.mapper();
        ObjectNode root = m.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("toolVersion", Version.TOOL);
        root.put("rules", Version.RULES);
        root.put("createdAt", in.confirmedAt().toString());
        root.put("confirmedBy", in.confirmedBy());
        root.put("confirmedAt", in.confirmedAt().toString());
        ObjectNode mandant = root.putObject("mandant");
        mandant.put("id", in.mandantId());
        mandant.put("name", in.mandantName());
        root.set("invoice", m.valueToTree(in.invoice()));
        root.set("totals", m.valueToTree(InvoiceCalculator.compute(in.invoice().items())));
        root.put("format", in.format().name());
        root.put("originalFileName", in.originalFileName());
        Confirmation c = in.confirmation() == null ? Confirmation.none() : in.confirmation();
        BelegStatus effective = c.belegOverride() != null ? c.belegOverride() : in.beleg().status();
        root.put("belegStatus", effective.name());
        root.put("belegStatusAuto", in.beleg().status().name());
        root.put("belegStatusOverridden", effective != in.beleg().status());
        root.set("belegWarnings", m.valueToTree(in.beleg().warnings()));
        ObjectNode printed = root.putObject("printedTotals");
        putAmount(printed, "net", c.printedNet());
        putAmount(printed, "tax", c.printedTax());
        putAmount(printed, "gross", c.printedGross());
        root.put("totalsMismatchConfirmed", c.totalsMismatchConfirmed());
        root.put("retainUntil", LocalDate.of(in.invoice().issueDate().getYear() + 8, 12, 31).toString());
        ObjectNode v = root.putObject("validation");
        v.put("passed", in.report().passed());
        v.put("xmlValid", in.report().xmlValid());
        if (in.report().pdfValid() != null) {
            v.put("pdfValid", in.report().pdfValid());
        }
        v.put("validator", in.report().validatorVersion());
        v.put("profile", in.report().profile());
        v.put("errorCount", in.report().errors().size());
        ObjectNode files = root.putObject("files");
        for (Map.Entry<String, String> e : hashes.entrySet()) {
            files.putObject(e.getKey()).put("sha256", e.getValue());
        }
        return root;
    }

    private static void putAmount(ObjectNode node, String name, java.math.BigDecimal value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value.toPlainString());
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException | RuntimeException ignored) {
            // Aufräumen ist Best Effort.
        }
    }
}
