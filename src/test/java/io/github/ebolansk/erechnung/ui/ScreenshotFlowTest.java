// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.ebolansk.erechnung.archive.ArchiveIndex;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.service.ArchiveService;
import io.github.ebolansk.erechnung.service.ProcessingService;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Erzeugt die README-Screenshots der Übersicht (Import) und des Archivs: Die Beispielrechnungen laufen durch das echte
 * Fenster, einige werden ins Archiv übernommen, andere bleiben als Entwurf stehen. Ergebnis in target/screenshots/.
 */
class ScreenshotFlowTest {
    private static final List<String> FILES = List.of(
        "beispiel-klassisch-nordlicht.pdf", "beispiel-modern-feldweg.pdf", "beispiel-02-kurzdatum-brutto-ohne-tausender-feldweg.pdf",
        "beispiel-03-steuernummer-lueckenloses-gap-meta-nordlicht.pdf", "beispiel-07-viele-positionen-zwei-seiten-nordlicht.pdf",
        "beispiel-09-nur-sieben-prozent-summenzeile-nordlicht.pdf");

    @Test
    void overviewAndArchiveWithSeveralDocuments(@TempDir Path home) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "braucht eine Anzeige");
        Files.createDirectories(home.resolve("daten"));
        Clock clock = Clock.systemDefaultZone();
        var config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        config.save(new AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}",
            OutputFormat.XRECHNUNG, LocalDate.of(2027, 1, 1)));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "demo");
        var mandanten = new MandantStore(home.resolve("daten/mandanten.json"));
        var service = new ProcessingService(home, config, mandanten, audit, clock);
        service.registerMandant(new Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart", "DE",
            "DE811234567", "12/345/67890", "info@nordlicht-werbung.example", "Erika Beispiel", "+49 711 5550123",
            "DE89370400440532013000", "COBADEFFXXX", ""));
        service.registerMandant(new Mandant(null, "Studio Feldweg UG (haftungsbeschränkt)", "Gartenstraße 7", "72070", "Tübingen", "DE",
            "DE299876543", "86/123/45678", "hallo@studio-feldweg.example", "Mara Feld", "07071 998877",
            "DE75512108001245126199", "SSKMDEMMXXX", ""));

        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame(home, service, config, mandanten, audit);
            frame[0].setSize(1200, 640);
            frame[0].setVisible(true);
        });
        try {
            JTable table = findTable(frame[0].getContentPane());
            Method process = MainFrame.class.getDeclaredMethod("processFiles", List.class);
            process.setAccessible(true);
            List<File> files = FILES.stream().map(f -> Path.of("beispiele", f).toAbsolutePath().toFile()).toList();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    process.invoke(frame[0], files);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            // Jede Rechnung erscheint nacheinander in der Prüfmaske: Käufer-E-Mail ergänzen, erzeugen.
            for (int i = 0; i < FILES.size(); i++) {
                int n = i;
                ReviewPanel[] panel = new ReviewPanel[1];
                await(() -> {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof JDialog d && d.isVisible() && findReviewPanel(d) != null) {
                            panel[0] = findReviewPanel(d);
                            return true;
                        }
                    }
                    return false;
                }, () -> "Prüfmaske " + (n + 1) + " " + describe(table));
                int row = i;
                SwingUtilities.invokeAndWait(() -> {
                    panel[0].buyerEmailField().setText("einkauf" + row + "@kunde-beispiel.example");
                    panel[0].accept();
                });
                await(() -> {
                    closeInfoDialogs();
                    return "Erzeugt".equals(table.getModel().getValueAt(row, 4));
                }, () -> "Status Erzeugt in Zeile " + (row + 1) + " " + describe(table));
            }
            // Die ersten vier ins Archiv übernehmen, die letzten zwei bleiben als Entwurf stehen.
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 3));
            JButton adopt = findButton(frame[0].getContentPane(), "Ins Archiv übernehmen");
            await(adopt::isEnabled, "Knopf Ins Archiv übernehmen");
            SwingUtilities.invokeAndWait(adopt::doClick);
            await(() -> {
                closeInfoDialogs();
                for (int r = 0; r < 4; r++) {
                    if (!"Archiviert".equals(table.getModel().getValueAt(r, 4))) {
                        return false;
                    }
                }
                return true;
            }, "vier Zeilen archiviert");
            SwingUtilities.invokeAndWait(table::clearSelection);
            snapshotFrame(frame[0], "uebersicht.png");

            // Archiv: auch die letzten beiden übernehmen, dann das Archivfenster mit allen sechs Rechnungen aufnehmen.
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(4, 5));
            await(adopt::isEnabled, "Knopf Ins Archiv übernehmen (2)");
            SwingUtilities.invokeAndWait(adopt::doClick);
            await(() -> {
                closeInfoDialogs();
                return "Archiviert".equals(table.getModel().getValueAt(4, 4)) && "Archiviert".equals(table.getModel().getValueAt(5, 4));
            }, "alle Zeilen archiviert");
            assertThat(ArchiveIndex.scan(home.resolve("archiv")).size()).isEqualTo(6);
            var archive = new ArchiveService(home, config, audit, clock);
            javax.swing.JFrame[] archiveFrame = new javax.swing.JFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                var panel = new ArchivePanel(archive, p -> { }, null);
                panel.reload();
                archiveFrame[0] = new javax.swing.JFrame("Archiv");
                archiveFrame[0].setContentPane(panel);
                archiveFrame[0].setSize(1200, 360);
                archiveFrame[0].setVisible(true);
            });
            Thread.sleep(500);
            var content = archiveFrame[0].getContentPane();
            SwingUtilities.invokeAndWait(() -> {
                var img = new java.awt.image.BufferedImage(content.getWidth(), content.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                content.paint(g);
                g.dispose();
                try {
                    javax.imageio.ImageIO.write(img, "png", Path.of("target/screenshots/archiv.png").toFile());
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    w.dispose();
                }
            });
        }
        assertThat(Path.of("target/screenshots/uebersicht.png")).isNotEmptyFile();
        assertThat(Path.of("target/screenshots/archiv.png")).isNotEmptyFile();
    }

    private static void snapshotFrame(MainFrame frame, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var content = frame.getContentPane();
            var img = new java.awt.image.BufferedImage(content.getWidth(), content.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            content.paint(g);
            g.dispose();
            try {
                Path out = Path.of("target/screenshots");
                Files.createDirectories(out);
                javax.imageio.ImageIO.write(img, "png", out.resolve(name).toFile());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static String describe(JTable table) {
        StringBuilder sb = new StringBuilder("[Zeilen:");
        for (int r = 0; r < table.getModel().getRowCount(); r++) {
            sb.append(' ').append(table.getModel().getValueAt(r, 4));
        }
        sb.append("; Fenster:");
        for (Window w : Window.getWindows()) {
            if (w.isVisible() && w instanceof JDialog d) {
                sb.append(' ').append(d.getTitle());
            }
        }
        return sb.append(']').toString();
    }

    private static void closeInfoDialogs() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog d && d.isVisible() && findReviewPanel(d) == null) {
                        d.dispose();
                    }
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(BooleanSupplier condition, String what) throws Exception {
        await(condition, () -> what);
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> what) throws Exception {
        long end = System.currentTimeMillis() + 60_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Zeitüberschreitung beim Warten auf: " + what.get());
            }
            Thread.sleep(50);
        }
    }

    private static JTable findTable(Component c) {
        if (c instanceof JTable t) {
            return t;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                JTable t = findTable(child);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static ReviewPanel findReviewPanel(Component c) {
        if (c instanceof ReviewPanel p) {
            return p;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                ReviewPanel p = findReviewPanel(child);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Component c, String text) {
        if (c instanceof JButton b && text.equals(b.getText())) {
            return b;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                JButton b = findButton(child, text);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }
}
