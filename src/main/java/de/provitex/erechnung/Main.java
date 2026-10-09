// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung;

import de.provitex.erechnung.audit.AuditLog;
import de.provitex.erechnung.config.ConfigStore;
import de.provitex.erechnung.mandant.MandantStore;
import de.provitex.erechnung.service.ProcessingService;
import de.provitex.erechnung.ui.MainFrame;
import de.provitex.erechnung.ui.Ui;
import de.provitex.erechnung.update.UpdateStartup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Path home = resolveHome();
        Path daten = home.resolve("daten");
        try {
            Files.createDirectories(daten.resolve("cache"));
            Files.createDirectories(daten.resolve("tmp"));
            System.setProperty("pdfbox.fontcache", daten.resolve("cache").toString());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Der Programmordner ist nicht beschreibbar:\n" + daten
                + "\n\nBitte das Programm in einen beschreibbaren Ordner legen.", "E-Rechnung-Tool", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                var config = new ConfigStore(daten.resolve("konfiguration.json"));
                var mandanten = new MandantStore(daten.resolve("mandanten.json"));
                var audit = new AuditLog(daten.resolve("protokoll.jsonl"), Clock.systemDefaultZone(),
                    System.getProperty("user.name", "unbekannt"));
                var service = new ProcessingService(home, config, mandanten, audit, Clock.systemDefaultZone());
                var frame = new MainFrame(home, service, config, mandanten, audit);
                frame.setVisible(true);
                confirmDisclaimer(frame, daten, audit);
                UpdateStartup.confirmStarted(home);
                var notice = UpdateStartup.consume(home, Version.TOOL, audit);
                notice.ifPresent(n -> JOptionPane.showMessageDialog(frame, n.message(), "Programmversion",
                    n.problem() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Start fehlgeschlagen: " + e.getMessage(), "E-Rechnung-Tool",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /** Beim ersten Start (und nach Textänderung) muss der Nutzungshinweis bestätigt werden; sonst beendet sich das Programm. */
    private static void confirmDisclaimer(JFrame frame, Path daten, AuditLog audit) throws java.io.IOException {
        Path file = daten.resolve("hinweis.json");
        if (!Disclaimer.needed(file)) {
            return;
        }
        var area = new JTextArea(Disclaimer.TEXT, 14, 56);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setCaretPosition(0);
        int choice = JOptionPane.showOptionDialog(frame, new JScrollPane(area), "Nutzungs- und Haftungshinweis",
            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
            new String[] {"Verstanden und einverstanden", "Beenden"}, "Beenden");
        if (choice != 0) {
            System.exit(0);
        }
        Disclaimer.accept(file, audit, Clock.systemDefaultZone(), System.getProperty("user.name", "unbekannt"));
    }

    static Path resolveHome() {
        String prop = System.getProperty("erechnung.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath();
        }
        try {
            Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            if (dir != null && dir.getFileName() != null && dir.getFileName().toString().equals("lib")
                && dir.getParent() != null && dir.getParent().getParent() != null) {
                return dir.getParent().getParent();
            }
        } catch (Exception ignored) {
            // Fallback unten
        }
        return Path.of("").toAbsolutePath();
    }
}
