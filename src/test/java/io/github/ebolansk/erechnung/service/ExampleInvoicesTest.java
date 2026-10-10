// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.SampleInvoices;
import io.github.ebolansk.erechnung.extract.PdfText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Erzeugt die Beispielrechnungen für den Ordner beispiele/ nach target/beispiele/: zwölf erfundene Rechnungen von nur zwei
 * Ausstellern (Mandanten), damit beim Ausprobieren der zweite Beleg desselben Ausstellers wiedererkannt wird.
 * Keine davon ist eine E-Rechnung (kein eingebettetes XML); sie sind die Vorlagen, aus denen das Tool erst eine macht.
 */
class ExampleInvoicesTest {
    private record Seller(String key, String name, String street, String zip, String city, String vatId, String taxNo, String iban, String bic,
                          String contact, String phone, String email) {
    }

    private static final Seller NORDLICHT = new Seller("nordlicht", "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart",
        "DE811234567", "12/345/67890", "DE89370400440532013000", "COBADEFFXXX", "Erika Beispiel", "+49 711 5550123",
        "info@nordlicht-werbung.example");
    private static final Seller FELDWEG = new Seller("feldweg", "Studio Feldweg UG (haftungsbeschränkt)", "Gartenstraße 7", "72070",
        "Tübingen", "DE299876543", "86/123/45678", "DE75512108001245126199", "SSKMDEMMXXX", "Mara Feld", "07071 998877",
        "hallo@studio-feldweg.example");

    @Test
    void writeExamplesOfTwoSellers(@org.junit.jupiter.api.io.TempDir Path home) throws Exception {
        Path out = Path.of("target", "beispiele");
        Files.createDirectories(out);
        Files.write(out.resolve("beispiel-klassisch-nordlicht.pdf"), SampleInvoices.classic(NORDLICHT.contact()));
        Files.write(out.resolve("beispiel-modern-feldweg.pdf"), SampleInvoices.modern(FELDWEG.contact()));
        int n = 0;
        for (SampleVariants.V v : SampleVariants.all()) {
            Seller s = n++ % 2 == 0 ? NORDLICHT : FELDWEG;
            boolean hadVat = !v.vatId.isBlank();
            v.sellerName = s.name();
            v.street = s.street();
            v.zip = s.zip();
            v.city = s.city();
            v.vatId = hadVat ? s.vatId() : "";
            v.taxNo = v.taxNo.isBlank() && hadVat ? "" : s.taxNo();
            v.iban = s.iban();
            v.bic = s.bic();
            v.contact = s.contact();
            v.phone = s.phone();
            v.email = s.email();
            Files.write(out.resolve("beispiel-" + v.label + "-" + s.key() + ".pdf"), SampleVariants.render(v));
        }
        List<Path> files;
        try (var list = Files.list(out)) {
            files = list.filter(p -> p.toString().endsWith(".pdf")).toList();
        }
        assertThat(files).hasSize(12);
        for (Path f : files) {
            byte[] pdf = Files.readAllBytes(f);
            assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).as(f + " enthält kein eingebettetes XML")
                .doesNotContain("EmbeddedFile");
            String text = PdfText.extract(pdf).text();
            assertThat(text).as(f.toString()).containsAnyOf(NORDLICHT.name(), FELDWEG.name());
            assertThat(text).as(f + " nennt den Ansprechpartner des Ausstellers")
                .containsAnyOf("Ansprechpartner: " + NORDLICHT.contact(), "Ansprechpartner: " + FELDWEG.contact());
        }

        // Mit beiden Ausstellern als Mandanten wird jede Beispielrechnung dem richtigen zugeordnet.
        Files.createDirectories(home.resolve("daten"));
        var clock = java.time.Clock.systemUTC();
        var service = new ProcessingService(home, new io.github.ebolansk.erechnung.config.ConfigStore(home.resolve("daten/konfiguration.json")),
            new io.github.ebolansk.erechnung.mandant.MandantStore(home.resolve("daten/mandanten.json")),
            new io.github.ebolansk.erechnung.audit.AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "test"), clock);
        for (Seller s : List.of(NORDLICHT, FELDWEG)) {
            service.registerMandant(new io.github.ebolansk.erechnung.mandant.Mandant(null, s.name(), s.street(), s.zip(), s.city(), "DE", s.vatId(),
                s.taxNo(), s.email(), s.contact(), s.phone(), s.iban(), s.bic(), ""));
        }
        for (Path f : files) {
            String expected = f.getFileName().toString().endsWith("nordlicht.pdf") ? NORDLICHT.name() : FELDWEG.name();
            assertThat(service.prepare(f).mandant()).as(f.getFileName().toString()).isPresent().get().extracting("name").isEqualTo(expected);
        }
    }
}
