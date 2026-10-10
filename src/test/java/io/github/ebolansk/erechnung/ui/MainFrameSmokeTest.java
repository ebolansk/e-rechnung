// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.service.ProcessingService;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Baut das echte Hauptfenster auf (braucht eine Anzeige, läuft also nicht in einer Umgebung ohne Bildschirm). */
class MainFrameSmokeTest {
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

    @Test
    void mainWindowHasSelectionColumnWithMasterCheckboxAndTheNewMenuBar(@TempDir Path home) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "braucht eine Anzeige");
        Files.createDirectories(home.resolve("daten"));
        Clock clock = Clock.systemUTC();
        var config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "test");
        var mandanten = new MandantStore(home.resolve("daten/mandanten.json"));
        var service = new ProcessingService(home, config, mandanten, audit, clock);
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> frame[0] = new MainFrame(home, service, config, mandanten, audit));
        try {
            JTable table = findTable(frame[0].getContentPane());
            assertThat(table).isNotNull();
            assertThat(table.getColumnModel().getColumn(0).getHeaderValue()).isEqualTo("Auswahl");
            Component header = table.getColumnModel().getColumn(0).getHeaderRenderer()
                .getTableCellRendererComponent(table, "Auswahl", false, false, -1, 0);
            assertThat(header).isInstanceOf(JCheckBox.class);
            assertThat(table.getColumnModel().getColumn(table.getColumnModel().getColumnIndex("Status")).getCellRenderer())
                .isInstanceOf(ProgressCellRenderer.class);

            // Die Symbol-Spalte trägt keine Überschrift, heißt für die Spaltenauswahl aber weiter „Ordner“.
            var folderColumn = table.getColumnModel().getColumn(table.getColumnModel().getColumnIndex("Ordner"));
            assertThat(folderColumn.getHeaderValue()).isEqualTo("");
            assertThat(folderColumn.getIdentifier()).isEqualTo("Ordner");

            // Spalten ein- und ausblenden (Auswahl und Datei bleiben immer sichtbar), die Wahl wird gemerkt.
            int columns = table.getColumnCount();
            SwingUtilities.invokeAndWait(() -> {
                frame[0].setColumnVisible("Format", false);
                frame[0].setColumnVisible("Datei", false);
            });
            assertThat(table.getColumnCount()).isEqualTo(columns - 1);
            assertThat(table.getColumnModel().getColumn(0).getHeaderValue()).isEqualTo("Auswahl");
            assertThat(Files.readString(home.resolve("daten/ansicht.json"))).contains("Format").doesNotContain("Datei");
            SwingUtilities.invokeAndWait(() -> frame[0].setColumnVisible("Format", true));
            assertThat(table.getColumnCount()).isEqualTo(columns);

            List<String> menus = new ArrayList<>();
            for (int i = 0; i < frame[0].getJMenuBar().getMenuCount(); i++) {
                if (frame[0].getJMenuBar().getMenu(i) instanceof JMenu m) {
                    menus.add(m.getText());
                }
            }
            assertThat(menus).containsExactly("Archiv", "Mandanten", "Konfiguration", "Hilfe", "Beenden");
            for (int i = 0; i < frame[0].getJMenuBar().getMenuCount(); i++) {
                if (frame[0].getJMenuBar().getMenu(i) instanceof JMenu m && List.of("Archiv", "Mandanten", "Konfiguration", "Beenden").contains(m.getText())) {
                    assertThat(m.getMenuComponentCount()).as(m.getText() + " öffnet direkt, ohne Untermenü").isZero();
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
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

    private static void await(BooleanSupplier condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Zeitüberschreitung beim Warten auf: " + what);
            }
            Thread.sleep(50);
        }
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

    private static JButton findButtonByTip(Component c, String tip) {
        if (c instanceof JButton b && tip.equals(b.getToolTipText())) {
            return b;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                JButton b = findButtonByTip(child, tip);
                if (b != null) {
                    return b;
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

    /** Schließt Hinweisfenster (modale Meldungen), die der Ablauf nach dem Übernehmen zeigen kann. */
    private static void closeInfoDialogs(MainFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isVisible() && findReviewPanel(d) == null) {
                    d.dispose();
                }
            }
        });
    }

    @Test
    void realFlowDropGenerateReviewThenAdoptIntoArchive(@TempDir Path home) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "braucht eine Anzeige");
        Files.createDirectories(home.resolve("daten"));
        Clock clock = Clock.systemUTC();
        var config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        config.save(new io.github.ebolansk.erechnung.config.AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}",
            io.github.ebolansk.erechnung.model.OutputFormat.XRECHNUNG, java.time.LocalDate.of(2027, 1, 1)));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "test");
        var mandanten = new MandantStore(home.resolve("daten/mandanten.json"));
        var service = new ProcessingService(home, config, mandanten, audit, clock);
        service.registerMandant(new io.github.ebolansk.erechnung.mandant.Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173",
            "Stuttgart", "DE", "DE811234567", "", "rechnung@nordlicht-werbung.example", "Erika Beispiel", "+49 711 5550123",
            "DE89370400440532013000", "COBADEFFXXX", ""));
        Path pdf = home.resolve("rechnung.pdf");
        Files.write(pdf, io.github.ebolansk.erechnung.SampleInvoices.classic());

        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame(home, service, config, mandanten, audit);
            frame[0].setVisible(true);
        });
        try {
            JTable table = findTable(frame[0].getContentPane());
            Method process = MainFrame.class.getDeclaredMethod("processFiles", List.class);
            process.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    process.invoke(frame[0], List.of(new File(pdf.toString())));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(table.getModel().getValueAt(0, 4)).isIn("Wartet", "In Bearbeitung", "In Prüfung");

            // Die Prüfmaske erscheint: Pflichtangabe ergänzen, dann „XRechnung erzeugen“.
            ReviewPanel[] panel = new ReviewPanel[1];
            await(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog d && d.isVisible() && findReviewPanel(d) != null) {
                        panel[0] = findReviewPanel(d);
                        return true;
                    }
                }
                return false;
            }, "Prüfmaske");
            assertThat(panel[0].okButtonText()).isEqualTo("XRechnung erzeugen");
            SwingUtilities.invokeAndWait(() -> {
                panel[0].buyerEmailField().setText("einkauf@baeckerei-sonnenschein.example");
                panel[0].accept();
            });

            // Erzeugt: nur ein Entwurf, nichts im Archiv.
            await(() -> "Erzeugt".equals(table.getModel().getValueAt(0, 4)), "Status Erzeugt");
            assertThat(Files.list(home.resolve("daten/entwurf")).count()).isEqualTo(1);
            assertThat(home.resolve("archiv")).satisfiesAnyOf(p -> assertThat(p).doesNotExist(),
                p -> assertThat(io.github.ebolansk.erechnung.archive.ArchiveIndex.scan(p).size()).isZero());

            snapshotFrame(frame[0], "hauptfenster-erzeugt.png");

            // Zeile anhaken und ins Archiv übernehmen.
            JButton adopt = findButton(frame[0].getContentPane(), "Ins Archiv übernehmen");
            assertThat(adopt.isEnabled()).as("ohne Auswahl nicht wählbar").isFalse();
            JButton original = findButtonByTip(frame[0].getContentPane(), "Vorlage (Original-PDF) öffnen");
            JButton output = findButtonByTip(frame[0].getContentPane(), "E-Rechnung (Ausgabe) öffnen");
            JButton protocol = findButtonByTip(frame[0].getContentPane(), "Prüfprotokoll öffnen");
            JButton folder = findButtonByTip(frame[0].getContentPane(), "Archivordner öffnen");
            assertThat(List.of(original.isEnabled(), output.isEnabled(), protocol.isEnabled(), folder.isEnabled()))
                .as("ohne ausgewählte Zeile ist nichts zu öffnen").containsOnly(false);
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
            assertThat(table.getModel().getValueAt(0, 6)).as("Zeile angeklickt: Häkchen vorne gesetzt").isEqualTo(true);
            // Erzeugt, noch nicht im Archiv: Vorlage, Ausgabe und Prüfprotokoll lassen sich öffnen, einen Archivordner gibt es noch nicht.
            assertThat(List.of(original.isEnabled(), output.isEnabled(), protocol.isEnabled(), folder.isEnabled()))
                .containsExactly(true, true, true, false);
            assertThat(frame[0].docs(0).output().toString()).contains("entwurf");
            await(adopt::isEnabled, "Knopf Ins Archiv übernehmen");
            SwingUtilities.invokeAndWait(adopt::doClick);
            await(() -> {
                try {
                    closeInfoDialogs(frame[0]);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return "Archiviert".equals(table.getModel().getValueAt(0, 4));
            }, "Status Archiviert");
            assertThat(io.github.ebolansk.erechnung.archive.ArchiveIndex.scan(home.resolve("archiv")).size()).isEqualTo(1);
            assertThat(home.resolve("daten/entwurf")).satisfiesAnyOf(p -> assertThat(p).doesNotExist(),
                p -> assertThat(p.toFile().list()).isEmpty());
            assertThat(String.valueOf(table.getModel().getValueAt(0, 5))).contains("archiv");
            // Ordner-Spalte: Symbol statt Pfad, der Pfad steht im Hilfetext.
            int folderView = table.getColumnModel().getColumnIndex("Ordner");
            var cell = (javax.swing.JLabel) table.getCellRenderer(0, folderView)
                .getTableCellRendererComponent(table, table.getModel().getValueAt(0, 5), false, false, 0, folderView);
            assertThat(cell.getIcon()).isNotNull();
            assertThat(cell.getText()).isEmpty();
            var at = table.getCellRect(0, folderView, true);
            String tip = table.getToolTipText(new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, at.x + 5, at.y + 5, 0, false));
            assertThat(tip).contains("Archivordner").contains("archiv");
            // Nach der Übernahme: dieselben Knöpfe zeigen auf die Dateien im Archiv, zusätzlich der Archivordner.
            assertThat(List.of(original.isEnabled(), output.isEnabled(), protocol.isEnabled(), folder.isEnabled())).containsOnly(true);
            var docs = frame[0].docs(0);
            assertThat(docs.folder()).isDirectory();
            assertThat(docs.original()).exists().hasFileName("vorlage-original.pdf");
            assertThat(docs.output()).exists().hasFileName("ausgabe-xrechnung.xml");
            assertThat(docs.protocol()).exists().hasFileName("pruefprotokoll.html");
            assertThat(docs.output().getParent()).isEqualTo(docs.folder());
            snapshotFrame(frame[0], "hauptfenster-archiviert.png");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    w.dispose();
                }
            });
        }
    }

    private static ReviewPanel awaitReviewPanel() throws Exception {
        ReviewPanel[] panel = new ReviewPanel[1];
        await(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isVisible() && findReviewPanel(d) != null) {
                    panel[0] = findReviewPanel(d);
                    return true;
                }
            }
            return false;
        }, "Prüfmaske");
        return panel[0];
    }

    @Test
    void cancelledRowReopensTheReviewMaskWhenProcessedAgain(@TempDir Path home) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "braucht eine Anzeige");
        Files.createDirectories(home.resolve("daten"));
        Clock clock = Clock.systemUTC();
        var config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "test");
        var mandanten = new MandantStore(home.resolve("daten/mandanten.json"));
        var service = new ProcessingService(home, config, mandanten, audit, clock);
        service.registerMandant(new io.github.ebolansk.erechnung.mandant.Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173",
            "Stuttgart", "DE", "DE811234567", "", "rechnung@nordlicht-werbung.example", "Erika Beispiel", "+49 711 5550123",
            "DE89370400440532013000", "COBADEFFXXX", ""));
        Path pdf = home.resolve("rechnung.pdf");
        Files.write(pdf, io.github.ebolansk.erechnung.SampleInvoices.classic());
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame(home, service, config, mandanten, audit);
            frame[0].setVisible(true);
        });
        try {
            JTable table = findTable(frame[0].getContentPane());
            Method process = MainFrame.class.getDeclaredMethod("processFiles", List.class);
            process.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    process.invoke(frame[0], List.of(new File(pdf.toString())));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            // Erste Prüfmaske: abbrechen.
            ReviewPanel first = awaitReviewPanel();
            JButton cancel = findButton((Container) SwingUtilities.getWindowAncestor(first), "Abbrechen");
            SwingUtilities.invokeAndWait(cancel::doClick);
            await(() -> "Abgebrochen".equals(table.getModel().getValueAt(0, 4)), "Status Abgebrochen");

            // Zeile anklicken und „Erneut verarbeiten“: die Prüfmaske muss wieder erscheinen.
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
            JButton again = findButton(frame[0].getContentPane(), "Erneut verarbeiten");
            await(again::isEnabled, "Knopf Erneut verarbeiten");
            SwingUtilities.invokeAndWait(again::doClick);
            ReviewPanel second = awaitReviewPanel();
            assertThat(second).isNotNull();
            assertThat(table.getRowCount()).as("gleiche Zeile, keine neue").isEqualTo(1);
            assertThat(table.getModel().getValueAt(0, 4)).isEqualTo("In Prüfung");
            SwingUtilities.invokeAndWait(() -> findButton((Container) SwingUtilities.getWindowAncestor(second), "Abbrechen").doClick());
            await(() -> "Abgebrochen".equals(table.getModel().getValueAt(0, 4)), "wieder Abgebrochen");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    w.dispose();
                }
            });
        }
    }
}
