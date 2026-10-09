package de.provitex.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import de.provitex.erechnung.SampleInvoices;
import de.provitex.erechnung.audit.AuditLog;
import de.provitex.erechnung.config.ConfigStore;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.mandant.MandantStore;
import de.provitex.erechnung.service.ProcessingService;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Erzeugt den README-Screenshot der Prüfmaske aus einer erfundenen Beispielrechnung nach target/screenshots/. */
class ScreenshotTest {
    @Test
    void reviewMaskOfSampleInvoice(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("daten"));
        Path pdf = home.resolve("beispiel-klassisch.pdf");
        Files.write(pdf, SampleInvoices.classic());
        Clock clock = Clock.systemDefaultZone();
        var service = new ProcessingService(home, new ConfigStore(home.resolve("daten/konfiguration.json")),
            new MandantStore(home.resolve("daten/mandanten.json")), new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "demo"), clock);
        Mandant m = service.registerMandant(new Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart", "DE",
            "DE811234567", "", "rechnung@nordlicht-werbung.example", "Erika Beispiel", "+49 711 5550123",
            "DE89370400440532013000", "COBADEFFXXX", ""));
        var p = service.prepare(pdf);
        var panel = new ReviewPanel(p.pdf(), p.extraction(), p.mandant().orElse(m), LocalDate.of(2027, 1, 1), () -> { }, () -> { });
        panel.buyerEmailField().setText("einkauf@baeckerei-sonnenschein.example");
        Path out = Path.of("target/screenshots/pruefmaske.png");
        UiSmokeTest.snapshot(panel, new Dimension(1200, 800), out);
        panel.close();
        assertThat(out).exists().isNotEmptyFile();
    }
}
