// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.Version;
import de.provitex.erechnung.archive.IntegrityCheck;
import de.provitex.erechnung.service.ArchiveService;
import java.time.Clock;
import de.provitex.erechnung.archive.Confirmation;
import de.provitex.erechnung.ai.AiSettingsStore;
import de.provitex.erechnung.service.UpdateService;
import de.provitex.erechnung.update.ReleaseInfo;
import de.provitex.erechnung.update.UpdateSettingsStore;
import de.provitex.erechnung.audit.AuditLog;
import de.provitex.erechnung.service.AiAssistService;
import de.provitex.erechnung.config.AppConfig;
import de.provitex.erechnung.config.ConfigStore;
import de.provitex.erechnung.extract.ExtractionResult;
import de.provitex.erechnung.extract.RuleBasedExtractor;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.mandant.MandantStore;
import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.OutputFormat;
import de.provitex.erechnung.service.ProcessingService;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public final class MainFrame extends JFrame {
    private static final String[] COLUMNS = {"Datei", "Mandant", "Rechnungsnummer", "Format", "Status", "Ordner"};

    private final Path home;
    private final ProcessingService service;
    private final ConfigStore config;
    private final MandantStore mandanten;
    private final AuditLog audit;
    private final ArchiveService archive;
    private final AiSettingsStore aiStore;
    private final AiAssistService aiAssist;
    private final UpdateSettingsStore updateStore;
    private final UpdateService updates;
    private final DefaultTableModel table = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel statusBar = new JLabel(" ");
    private final JMenuItem checkUpdateItem = new JMenuItem("Nach Updates suchen…");
    private final Deque<Path> queue = new ArrayDeque<>();
    private boolean busy;

    public MainFrame(Path home, ProcessingService service, ConfigStore config, MandantStore mandanten, AuditLog audit) {
        super("E-Rechnung-Tool");
        this.home = home;
        this.service = service;
        this.config = config;
        this.mandanten = mandanten;
        this.audit = audit;
        this.archive = new ArchiveService(home, config, audit, Clock.systemDefaultZone());
        this.aiStore = new AiSettingsStore(home.resolve("daten").resolve("ki.json"));
        this.aiAssist = new AiAssistService(aiStore, audit);
        this.updateStore = new UpdateSettingsStore(home.resolve("daten").resolve("update.json"));
        this.updates = new UpdateService(home, updateStore, audit, Version.TOOL);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(AppIcon.images());
        setLayout(new BorderLayout(0, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 4, 10));

        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Datei");
        JMenuItem settings = new JMenuItem("Konfiguration…");
        settings.addActionListener(e -> {
            new SettingsDialog(this, home, config, mandanten, settingsTabs()).setVisible(true);
            updateStatusBar();
        });
        JMenuItem exit = new JMenuItem("Beenden");
        exit.addActionListener(e -> dispose());
        file.add(settings);
        file.addSeparator();
        file.add(exit);
        JMenu archiveMenu = new JMenu("Archiv");
        JMenuItem search = new JMenuItem("Suchen…");
        search.addActionListener(e -> new ArchiveDialog(this, archive).setVisible(true));
        JMenuItem check = new JMenuItem("Integrität prüfen");
        check.addActionListener(e -> checkArchive(true));
        archiveMenu.add(search);
        archiveMenu.add(check);
        JMenu help = new JMenu("Hilfe");
        JMenuItem about = new JMenuItem("Info");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "E-Rechnung-Tool " + Version.TOOL + "\nRegelbasis: " + Version.RULES
                + "\n\nMachbarkeitsstudie (Proof of Concept), kein fertiges Produkt.\nDas Tool unterstützt beim Erstellen von E-Rechnungen. Die inhaltliche Verantwortung für die\n"
                + "Rechnungsangaben liegt beim Rechnungsaussteller. Das Prüf-Protokoll bestätigt nur das Format."
                + "\n\nCopyright 2026 Stefan Schmitt. Open Source unter der Apache License 2.0 (siehe LICENSE und NOTICE).\n"
                + "Die Software wird ohne Gewährleistung und ohne Haftung bereitgestellt, soweit gesetzlich zulässig.",
            "Info", JOptionPane.INFORMATION_MESSAGE));
        JMenuItem notice = new JMenuItem("Nutzungs- und Haftungshinweis");
        notice.addActionListener(e -> {
            var area = new javax.swing.JTextArea(de.provitex.erechnung.Disclaimer.TEXT, 14, 56);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new javax.swing.JScrollPane(area), "Nutzungs- und Haftungshinweis",
                JOptionPane.INFORMATION_MESSAGE);
        });
        JMenuItem checkUpdate = checkUpdateItem;
        checkUpdate.addActionListener(e -> checkForUpdate());
        JMenuItem rollback = new JMenuItem("Vorherige Version wiederherstellen…");
        rollback.addActionListener(e -> requestRollback());
        help.add(checkUpdate);
        help.add(rollback);
        help.addSeparator();
        help.add(notice);
        help.add(about);
        bar.add(file);
        bar.add(archiveMenu);
        bar.add(help);
        setJMenuBar(bar);

        add(new DropPanel(this::processFiles), BorderLayout.NORTH);
        JTable view = new JTable(table);
        view.setFillsViewportHeight(true);
        view.setRowHeight(24);
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && view.getSelectedRow() >= 0) {
                    openFolder(String.valueOf(table.getValueAt(view.getSelectedRow(), 5)));
                }
            }
        });
        add(new JScrollPane(view), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        updateStatusBar();
        setPreferredSize(new Dimension(1000, 640));
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                checkArchive(false);
                checkUpdateOnStart();
            }
        });
    }

    /** Prüft das Archiv im Hintergrund. Beim Start meldet sich das Tool nur bei Fehlern, auf Knopfdruck immer. */
    private void checkArchive(boolean manual) {
        new SwingWorker<IntegrityCheck.Report, Void>() {
            @Override
            protected IntegrityCheck.Report doInBackground() throws Exception {
                return archive.check();
            }

            @Override
            protected void done() {
                try {
                    IntegrityCheck.Report report = get();
                    updateStatusBar();
                    statusBar.setText(statusBar.getText() + "   ·   Integrität: " + (report.ok() ? "in Ordnung" : "ABWEICHUNGEN"));
                    if (manual || !report.ok()) {
                        ArchivePanel.showReport(MainFrame.this, report);
                    }
                } catch (Exception e) {
                    if (manual) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Die Integritätsprüfung ist fehlgeschlagen: " + cause(e).getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void updateStatusBar() {
        try {
            AppConfig c = config.load(home);
            statusBar.setText("Ausgabeformat: " + (c.outputFormat() == OutputFormat.ZUGFERD ? "ZUGFeRD" : "XRechnung")
                + "   ·   Archiv: " + c.archiveRoot());
            statusBar.setToolTipText("Doppelklick auf eine Zeile der Tabelle öffnet den Archivordner der Rechnung.");
        } catch (IOException e) {
            statusBar.setText("Konfiguration nicht lesbar: " + e.getMessage());
        }
    }

    private void processFiles(List<File> files) {
        List<String> rejected = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                queue.add(f.toPath());
            } else {
                rejected.add(f.getName());
            }
        }
        if (!rejected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nur PDF-Dateien werden verarbeitet. Abgelehnt:\n" + String.join("\n", rejected),
                "Dateien abgelehnt", JOptionPane.WARNING_MESSAGE);
        }
        if (!busy) {
            next();
        }
    }

    private void next() {
        Path p = queue.poll();
        if (p == null) {
            busy = false;
            return;
        }
        busy = true;
        int row = addRow(p.getFileName().toString());
        new SwingWorker<ProcessingService.Prepared, Void>() {
            @Override
            protected ProcessingService.Prepared doInBackground() throws Exception {
                return service.prepare(p);
            }

            @Override
            protected void done() {
                try {
                    handlePrepared(row, get());
                } catch (Exception e) {
                    fail(row, "Nicht lesbar", cause(e));
                }
            }
        }.execute();
    }

    /** Stille Prüfung beim Start (nur wenn in der Konfiguration eingeschaltet): bei einer neueren Version Hinweis, sonst nichts. */
    private void checkUpdateOnStart() {
        new SwingWorker<Optional<ReleaseInfo>, Void>() {
            @Override
            protected Optional<ReleaseInfo> doInBackground() {
                return updates.checkOnStart(java.time.Instant.now());
            }

            @Override
            protected void done() {
                try {
                    get().ifPresent(r -> {
                        statusBar.setText(statusBar.getText() + "   ·   Neue Version " + r.version() + " verfügbar (Hilfe → Nach Updates suchen)");
                        checkUpdateItem.setText("Nach Updates suchen… (neu: " + r.version() + ")");
                    });
                } catch (Exception e) {
                    // bleibt still: die Prüfung beim Start darf nie stören
                }
            }
        }.execute();
    }

    private void checkForUpdate() {
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<Optional<ReleaseInfo>, Void>() {
            @Override
            protected Optional<ReleaseInfo> doInBackground() throws Exception {
                return updates.check();
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    Optional<ReleaseInfo> r = get();
                    if (r.isEmpty()) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Sie nutzen die aktuelle Version (" + Version.TOOL + ").",
                            "Nach Updates suchen", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        offerUpdate(r.get());
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Die Suche nach Updates ist fehlgeschlagen:\n" + cause(e).getMessage(),
                        "Nach Updates suchen", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void offerUpdate(ReleaseInfo r) {
        JTextArea notes = new JTextArea(r.notes().isBlank() ? "(keine Hinweise im Release)" : r.notes(), 10, 60);
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.add(new JLabel("<html>Neue Version <b>" + r.version() + "</b> verfügbar (installiert: " + Version.TOOL + ", "
            + (r.size() / 1024 / 1024) + " MB).<br>Das Paket wird heruntergeladen, per SHA-256 geprüft und beim <b>nächsten Start</b> eingespielt.</html>"),
            BorderLayout.NORTH);
        box.add(new JScrollPane(notes), BorderLayout.CENTER);
        if (JOptionPane.showOptionDialog(this, box, "Update verfügbar", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            new Object[] {"Herunterladen und vorbereiten", "Abbrechen"}, "Abbrechen") != 0) {
            return;
        }
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                updates.prepare(r);
                return null;
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    get();
                    JOptionPane.showMessageDialog(MainFrame.this, "Version " + r.version() + " ist vorbereitet.\n"
                        + "Bitte das Programm schließen und mit start.cmd neu starten; dabei wird das Update eingespielt.",
                        "Update vorbereitet", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Das Update wurde nicht vorbereitet, die installierte Version ist unverändert:\n"
                        + cause(e).getMessage(), "Update", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void requestRollback() {
        if (!updates.rollbackPossible()) {
            JOptionPane.showMessageDialog(this, "Es gibt keine vorherige Version (app.alt) zum Wiederherstellen.",
                "Vorherige Version", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Beim nächsten Start wird die vorherige Programmversion wiederhergestellt.\n"
            + "Archivierte Rechnungen bleiben unverändert. Vormerken?", "Vorherige Version", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            updates.requestRollback();
            JOptionPane.showMessageDialog(this, "Vorgemerkt. Bitte das Programm schließen und mit start.cmd neu starten.",
                "Vorherige Version", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private java.util.Map<String, JPanel> settingsTabs() {
        java.util.Map<String, JPanel> tabs = new java.util.LinkedHashMap<>();
        tabs.put("KI", new AiSettingsPanel(aiStore));
        tabs.put("Update", new UpdateSettingsPanel(updateStore));
        return tabs;
    }

    private void handlePrepared(int row, ProcessingService.Prepared prep) {
        if (prep.duplicateByHash().isPresent()) {
            setCell(row, 4, "Bereits archiviert");
            setCell(row, 5, prep.duplicateByHash().get().dir().toString());
            JOptionPane.showMessageDialog(this, "Diese PDF wurde bereits archiviert:\n" + prep.duplicateByHash().get().dir(),
                "Doppelt", JOptionPane.WARNING_MESSAGE);
            next();
            return;
        }
        Mandant m = prep.mandant().orElse(null);
        ExtractionResult extraction = prep.extraction();
        if (m == null) {
            Optional<Mandant> created = new MandantDialog(this, RuleBasedExtractor.draftMandant(prep.text(), prep.cells())).showAndGet();
            if (created.isEmpty()) {
                setCell(row, 4, "Abgebrochen");
                next();
                return;
            }
            try {
                m = service.registerMandant(created.get());
            } catch (IOException e) {
                fail(row, "Mandant nicht gespeichert", e);
                return;
            }
            extraction = RuleBasedExtractor.extract(prep.text(), prep.cells(), m);
        }
        setCell(row, 1, m.name());
        setCell(row, 2, extraction.draft().number());
        AppConfig cfg;
        try {
            cfg = config.load(home);
        } catch (IOException e) {
            fail(row, "Konfiguration nicht lesbar", e);
            return;
        }
        setCell(row, 3, cfg.outputFormat() == OutputFormat.ZUGFERD ? "ZUGFeRD" : "XRechnung");
        ReviewDialog review = new ReviewDialog(this, prep.pdf(), extraction, m, cfg.cutoffDate(),
            new AiHelp(aiAssist, prep.text(), prep.fileName()));
        Optional<InvoiceData> data = review.showAndGet();
        final Confirmation confirmation = review.confirmation();
        if (data.isEmpty()) {
            setCell(row, 4, "Abgebrochen");
            next();
            return;
        }
        setCell(row, 2, data.get().number());
        setCell(row, 4, "Wird erzeugt und geprüft …");
        final Mandant mandant = m;
        new SwingWorker<ProcessingService.Outcome, Void>() {
            @Override
            protected ProcessingService.Outcome doInBackground() throws Exception {
                return service.generateAndArchive(prep, mandant, data.get(), System.getProperty("user.name", "unbekannt"), confirmation);
            }

            @Override
            protected void done() {
                try {
                    showOutcome(row, get());
                } catch (Exception e) {
                    fail(row, "Fehler", cause(e));
                }
            }
        }.execute();
    }

    private void showOutcome(int row, ProcessingService.Outcome out) {
        if (out.archived()) {
            setCell(row, 4, "Archiviert");
            setCell(row, 5, out.directory().toString());
            if (!out.problems().isEmpty()) {
                JOptionPane.showMessageDialog(this, String.join("\n", out.problems()), "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            setCell(row, 4, "Nicht archiviert");
            JTextArea area = new JTextArea(String.join("\n\n", out.problems()), 12, 70);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Die Rechnung wurde nicht archiviert", JOptionPane.ERROR_MESSAGE);
        }
        next();
    }

    /** Entpackt die ExecutionException des SwingWorkers, damit die Meldung die eigentliche Ursache zeigt. */
    private static Exception cause(Exception e) {
        Throwable t = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        return t instanceof Exception x ? x : new Exception(t);
    }

    private void fail(int row, String status, Exception e) {
        setCell(row, 4, status);
        JOptionPane.showMessageDialog(this, status + ": " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        next();
    }

    private int addRow(String name) {
        table.addRow(new Object[] {name, "", "", "", "In Bearbeitung", ""});
        return table.getRowCount() - 1;
    }

    private void setCell(int row, int col, String value) {
        table.setValueAt(value, row, col);
    }

    private void openFolder(String dir) {
        if (dir == null || dir.isBlank() || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(dir));
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(this, "Der Ordner kann nicht geöffnet werden:\n" + dir, "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
