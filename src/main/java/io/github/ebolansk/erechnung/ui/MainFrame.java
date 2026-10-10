// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.Version;
import io.github.ebolansk.erechnung.archive.IntegrityCheck;
import io.github.ebolansk.erechnung.service.ArchiveService;
import java.time.Clock;
import io.github.ebolansk.erechnung.archive.Confirmation;
import io.github.ebolansk.erechnung.ai.AiSettingsStore;
import io.github.ebolansk.erechnung.service.UpdateService;
import io.github.ebolansk.erechnung.update.ReleaseInfo;
import io.github.ebolansk.erechnung.update.UpdateSettingsStore;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.service.AiAssistService;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.extract.ExtractionResult;
import io.github.ebolansk.erechnung.extract.RuleBasedExtractor;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.service.ProcessingService;
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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
    private static final String[] COLUMNS = {"Datei", "Mandant", "Rechnungsnummer", "Format", "Status", "Ordner", "Auswahl"};
    private static final int CHECK_COL = 6;

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

        @Override
        public Class<?> getColumnClass(int column) {
            return column == CHECK_COL ? Boolean.class : String.class;
        }
    };
    private final JLabel statusBar = new JLabel(" ");
    private final JMenuItem checkUpdateItem = new JMenuItem("Nach Updates suchen…");
    private record Pending(Path path, int row) {
    }

    private final Deque<Pending> queue = new ArrayDeque<>();
    /** Quelldatei je Tabellenzeile (gleicher Index wie das Tabellenmodell), für „Erneut verarbeiten“. */
    private final List<Path> rowFiles = new ArrayList<>();
    /** Erzeugte, noch nicht archivierte E-Rechnung je Tabellenzeile (null, wenn es keine gibt). */
    private final List<ProcessingService.Draft> rowDrafts = new ArrayList<>();
    /** Dateien je Tabellenzeile zum Öffnen: Vorlage, Ausgabe, Prüfprotokoll und (nach der Übernahme) der Archivordner. */
    private final List<RowDocs> rowDocs = new ArrayList<>();
    private final JButton originalButton = iconButton(MenuIcons.Kind.PDF, "Vorlage (Original-PDF) öffnen");
    private final JButton outputButton = iconButton(MenuIcons.Kind.FILE, "E-Rechnung (Ausgabe) öffnen");
    private final JButton protocolButton = iconButton(MenuIcons.Kind.PROTOCOL, "Prüfprotokoll öffnen");
    private final JButton folderButton = iconButton(MenuIcons.Kind.FOLDER, "Archivordner öffnen");

    /** Was zu einer Zeile geöffnet werden kann (jeweils null, wenn es das noch nicht gibt). */
    record RowDocs(Path original, Path output, Path protocol, Path folder) {
    }
    private final JButton adoptButton = new JButton("Ins Archiv übernehmen", MenuIcons.of(MenuIcons.Kind.ARCHIVE));
    private final JButton reprocessButton = new JButton("Erneut verarbeiten", MenuIcons.of(MenuIcons.Kind.UPDATE));
    private final JButton deleteButton = new JButton("Löschen", MenuIcons.of(MenuIcons.Kind.TRASH));
    private final JLabel selectionLabel = new JLabel(" ");
    private final JCheckBox masterBox = new JCheckBox();
    private JTable overview;
    private ColumnChooser columnChooser;
    private boolean busy;
    private boolean adopting;

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
        service.cleanupDrafts();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(AppIcon.images());
        setLayout(new BorderLayout(0, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 4, 10));

        JMenuBar bar = new JMenuBar();
        JMenu settingsMenu = DirectMenu.of("Konfiguration", MenuIcons.of(MenuIcons.Kind.SETTINGS), () -> {
            new SettingsDialog(this, home, config, settingsTabs(), new UpdateSettingsPanel(updateStore)).setVisible(true);
            updateStatusBar();
        });
        JMenu mandantenMenu = DirectMenu.of("Mandanten", MenuIcons.of(MenuIcons.Kind.PEOPLE),
            () -> new MandantenDialog(this, mandanten, aiAssist).setVisible(true));
        JMenu exitMenu = DirectMenu.of("Beenden", MenuIcons.of(MenuIcons.Kind.EXIT), this::requestExit);
        JMenu archiveMenu = DirectMenu.of("Archiv", MenuIcons.of(MenuIcons.Kind.ARCHIVE),
            () -> new ArchiveDialog(this, archive, home.resolve("daten").resolve("ansicht-archiv.json")).setVisible(true));
        JMenu help = new JMenu("Hilfe");
        JMenuItem about = new JMenuItem("Info");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "E-Rechnung-Tool " + Version.TOOL + "\nRegelbasis: " + Version.RULES
                + "\n\nMachbarkeitsstudie (Proof of Concept), kein fertiges Produkt.\nDas Tool unterstützt beim Erstellen von E-Rechnungen. Die inhaltliche Verantwortung für die\n"
                + "Rechnungsangaben liegt beim Rechnungsaussteller. Das Prüf-Protokoll bestätigt nur das Format."
                + "\n\nCopyright 2026 E-Rechnung-Tool contributors. Open Source unter der Apache License 2.0 (siehe LICENSE und NOTICE).\n"
                + "Die Software wird ohne Gewährleistung und ohne Haftung bereitgestellt, soweit gesetzlich zulässig.",
            "Info", JOptionPane.INFORMATION_MESSAGE));
        JMenuItem notice = new JMenuItem("Nutzungs- und Haftungshinweis");
        notice.addActionListener(e -> {
            var area = new javax.swing.JTextArea(io.github.ebolansk.erechnung.Disclaimer.TEXT, 14, 56);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setEditable(false);
            area.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new javax.swing.JScrollPane(area), "Nutzungs- und Haftungshinweis",
                JOptionPane.INFORMATION_MESSAGE);
        });
        JMenuItem checkUpdate = checkUpdateItem;
        checkUpdate.addActionListener(e -> checkForUpdate());
        help.add(checkUpdate);
        help.addSeparator();
        help.add(notice);
        help.add(about);
        JMenuItem support = new JMenuItem("Projekt unterstützen (Buy me a coffee)");
        support.setToolTipText("Öffnet die Seite im Browser. Freiwillig, ohne Anspruch auf Leistung.");
        support.addActionListener(e -> openSupportPage());
        help.addSeparator();
        help.add(support);
        help.setIcon(MenuIcons.of(MenuIcons.Kind.HELP));
        checkUpdate.setIcon(MenuIcons.of(MenuIcons.Kind.UPDATE));
        notice.setIcon(MenuIcons.of(MenuIcons.Kind.NOTICE));
        about.setIcon(MenuIcons.of(MenuIcons.Kind.INFO));
        bar.add(archiveMenu);
        bar.add(mandantenMenu);
        bar.add(settingsMenu);
        bar.add(help);
        bar.add(javax.swing.Box.createHorizontalGlue());
        bar.add(exitMenu);
        setJMenuBar(bar);

        add(new DropPanel(this::processFiles), BorderLayout.NORTH);
        // Ein Klick in die Auswahl-Spalte schaltet nur diese Zeile um (wie Strg+Klick); die Häkchen folgen der Zeilenauswahl.
        JTable view = new JTable(table) {
            @Override
            public void changeSelection(int row, int column, boolean toggle, boolean extend) {
                boolean onCheck = convertColumnIndexToModel(column) == CHECK_COL;
                super.changeSelection(row, column, onCheck || toggle, !onCheck && extend);
            }

            /** Hilfetext der Ordner-Spalte: der Pfad (bei Entwürfen der Hinweis und die Entwurfsdatei). */
            @Override
            public String getToolTipText(MouseEvent e) {
                int viewRow = rowAtPoint(e.getPoint());
                int viewCol = columnAtPoint(e.getPoint());
                if (viewRow >= 0 && viewCol >= 0 && convertColumnIndexToModel(viewCol) == 5) {
                    return folderTooltip(viewRow);
                }
                return super.getToolTipText(e);
            }
        };
        this.overview = view;
        view.setFillsViewportHeight(true);
        view.setRowHeight(24);
        view.getTableHeader().setReorderingAllowed(false);
        view.moveColumn(CHECK_COL, 0);
        view.getColumn("Status").setCellRenderer(new ProgressCellRenderer());
        view.getColumn("Datei").setPreferredWidth(260);
        view.getColumn("Mandant").setPreferredWidth(260);
        view.getColumn("Rechnungsnummer").setPreferredWidth(140);
        view.getColumn("Format").setPreferredWidth(90);
        view.getColumn("Status").setPreferredWidth(120);
        view.getColumn("Ordner").setPreferredWidth(60);
        view.getColumn("Ordner").setMaxWidth(80);
        view.getColumn("Ordner").setCellRenderer(new FolderCellRenderer(4));
        // Die Symbol-Spalte braucht keine Überschrift (der Name „Ordner“ bleibt für die Spaltenauswahl und den Hilfetext).
        var folderColumn = view.getColumn("Ordner");
        folderColumn.setIdentifier("Ordner");
        folderColumn.setHeaderValue("");
        view.getColumn("Auswahl").setMinWidth(46);
        view.getColumn("Auswahl").setMaxWidth(46);
        installSelectionHeader(view);
        columnChooser = new ColumnChooser(view, new ColumnVisibility(home.resolve("daten").resolve("ansicht.json")),
            java.util.Set.of("Auswahl", "Datei"));
        view.setToolTipText("Rechtsklick: Aktionen. Mit den Häkchen wählen Sie mehrere Zeilen für die Knöpfe oben. Entf löscht die markierten abgeschlossenen Zeilen aus der Übersicht (die PDF-Dateien bleiben unverändert).");
        view.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "entfernen");
        view.getActionMap().put("entfernen", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeRows(targetRows(view));
            }
        });
        installRowMenu(view);
        FileDropHandler dropHandler = new FileDropHandler(this::processFiles);
        setTransferHandler(dropHandler);
        view.setTransferHandler(dropHandler);
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = view.rowAtPoint(e.getPoint());
                int viewCol = view.columnAtPoint(e.getPoint());
                int modelCol = viewCol >= 0 ? view.convertColumnIndexToModel(viewCol) : -1;
                if (viewRow >= 0 && e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1 && modelCol == 5) {
                    openRow(viewRow);
                } else if (e.getClickCount() == 2 && viewRow >= 0 && viewCol >= 0 && modelCol != CHECK_COL && modelCol != 5) {
                    openRow(viewRow);
                }
            }
        });
        JScrollPane viewScroll = new JScrollPane(view);
        viewScroll.setTransferHandler(dropHandler);
        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.add(buildActions(view), BorderLayout.NORTH);
        center.add(viewScroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        table.addTableModelListener(e -> {
            view.getTableHeader().repaint();
            updateActions(view);
        });
        view.getSelectionModel().addListSelectionListener(e -> {
            syncChecks(view);
            updateActions(view);
        });
        updateActions(view);
        JLabel versionLabel = new JLabel("Version " + Version.TOOL);
        versionLabel.setForeground(java.awt.Color.GRAY);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.add(statusBar, BorderLayout.WEST);
        footer.add(versionLabel, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        updateStatusBar();
        setPreferredSize(new Dimension(1000, 640));
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                checkArchive();
                checkUpdateOnStart();
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                requestExit();
            }
        });
    }

    /** Prüft das Archiv beim Start im Hintergrund und meldet sich nur bei Abweichungen (die manuelle Prüfung ist im Archiv). */
    private void checkArchive() {
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
                    if (!report.ok()) {
                        ArchivePanel.showReport(MainFrame.this, report);
                    }
                } catch (Exception e) {
                    // Kein Dialog beim Start, aber sichtbar: Der schwerste Fall (Protokoll oder Archiv nicht lesbar) darf nicht
                    // unbemerkt bleiben. Die Prüfung lässt sich im Archiv von Hand wiederholen.
                    Throwable c = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
                    statusBar.setText("Archivprüfung fehlgeschlagen: " + c.getMessage() + " (im Archiv: Knopf „Integrität prüfen“)");
                }
            }
        }.execute();
    }

    private void updateStatusBar() {
        try {
            AppConfig c = config.load(home);
            statusBar.setText("Ausgabeformat: " + (c.outputFormat() == OutputFormat.ZUGFERD ? "ZUGFeRD" : "XRechnung"));
        } catch (IOException e) {
            statusBar.setText("Konfiguration nicht lesbar: " + e.getMessage());
        }
    }

    private void processFiles(List<File> files) {
        FileIntake intake = FileIntake.sort(files);
        List<String> rejected = intake.rejected();
        for (File f : intake.accepted()) {
            queue.add(new Pending(f.toPath(), addRow(f.toPath())));
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
        Pending pending = queue.poll();
        if (pending == null) {
            busy = false;
            return;
        }
        busy = true;
        Path p = pending.path();
        int row = pending.row();
        setCell(row, 4, OverviewRules.READING);
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
            + (r.size() / 1024 / 1024) + " MB).<br>Das Update wird heruntergeladen, geprüft und beim <b>nächsten Start</b> des Programms eingespielt.</html>"),
            BorderLayout.NORTH);
        box.add(new JScrollPane(notes), BorderLayout.CENTER);
        if (JOptionPane.showOptionDialog(this, box, "Update verfügbar", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            new Object[] {"Update einspielen", "Abbrechen"}, "Abbrechen") != 0) {
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
                    JOptionPane.showMessageDialog(MainFrame.this, "Version " + r.version() + " ist bereit.\n"
                        + "Bitte schließen Sie das Programm und starten es erneut.",
                        "Update erfolgreich", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Das Update konnte nicht geladen werden, die installierte Version ist unverändert:\n"
                        + cause(e).getMessage(), "Update", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private java.util.Map<String, JPanel> settingsTabs() {
        java.util.Map<String, JPanel> tabs = new java.util.LinkedHashMap<>();
        tabs.put("KI", new AiSettingsPanel(aiStore));
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
            Optional<Mandant> created = new MandantDialog(this, RuleBasedExtractor.draftMandant(prep.text(), prep.cells()),
            new MandantAi(aiAssist, prep.text(), prep.fileName())).showAndGet();
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
        setCell(row, 4, OverviewRules.REVIEW);
        ReviewDialog review = new ReviewDialog(this, prep.pdf(), extraction, m, cfg.outputFormat(),
            new AiHelp(aiAssist, prep.text(), prep.fileName()));
        Optional<InvoiceData> data = review.showAndGet();
        final Confirmation confirmation = review.confirmation();
        if (data.isEmpty()) {
            setCell(row, 4, "Abgebrochen");
            next();
            return;
        }
        if (review.mandantChanged()) {
            try {
                m = service.updateMandant(review.mandant());
                setCell(row, 1, m.name());
            } catch (IOException e) {
                fail(row, "Mandant nicht gespeichert", e);
                return;
            }
        }
        setCell(row, 2, data.get().number());
        setCell(row, 4, OverviewRules.GENERATING);
        final Mandant mandant = m;
        new SwingWorker<ProcessingService.Generated, Void>() {
            @Override
            protected ProcessingService.Generated doInBackground() throws Exception {
                return service.generate(prep, mandant, data.get(), System.getProperty("user.name", "unbekannt"), confirmation);
            }

            @Override
            protected void done() {
                try {
                    showGenerated(row, get());
                } catch (Exception e) {
                    fail(row, "Fehler", cause(e));
                }
            }
        }.execute();
    }

    /** Die E-Rechnung ist erzeugt und formal geprüft, liegt aber nur als Entwurf vor: ansehen, prüfen, dann ins Archiv übernehmen. */
    private void showGenerated(int row, ProcessingService.Generated g) {
        if (g.draft() != null) {
            rowDrafts.set(row, g.draft());
            rowDocs.set(row, new RowDocs(rowFiles.get(row), g.draft().outputFile(), g.draft().protocolFile(), null));
            setCell(row, 4, OverviewRules.READY);
            setCell(row, 5, "Entwurf, noch nicht im Archiv");
        } else {
            setCell(row, 4, "Nicht erzeugt");
            JTextArea area = new JTextArea(String.join("\n\n", g.problems()), 12, 70);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Die E-Rechnung wurde nicht erzeugt", JOptionPane.ERROR_MESSAGE);
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

    private int addRow(Path file) {
        rowFiles.add(file);
        rowDrafts.add(null);
        rowDocs.add(new RowDocs(file, null, null, null));
        table.addRow(new Object[] {file.getFileName().toString(), "", "", "", OverviewRules.WAITING, "", Boolean.FALSE});
        return table.getRowCount() - 1;
    }

    private List<String> rowStatuses() {
        List<String> status = new ArrayList<>();
        for (int r = 0; r < table.getRowCount(); r++) {
            status.add(String.valueOf(table.getValueAt(r, 4)));
        }
        return status;
    }

    /** Entf und „Löschen“: entfernt die markierten abgeschlossenen Zeilen aus der Übersicht (Dateien bleiben), erzeugte Entwürfe werden verworfen. */
    private void removeRows(int[] selected) {
        List<Integer> rows = OverviewRules.removable(rowStatuses(), selected, busy || adopting);
        if (rows.isEmpty()) {
            return;
        }
        long drafts = rows.stream().filter(r -> rowDrafts.get(r) != null).count();
        if (drafts > 0 && JOptionPane.showConfirmDialog(this, drafts + " erzeugte E-Rechnung(en) sind noch nicht im Archiv und werden verworfen.\n"
            + "Fortfahren?", "Entwürfe verwerfen", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        for (int row : rows) {
            ProcessingService.Draft draft = rowDrafts.get(row);
            if (draft != null) {
                try {
                    service.discard(draft);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Der Entwurf konnte nicht protokolliert verworfen werden: " + e.getMessage(), "Fehler",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
            table.removeRow(row);
            rowFiles.remove(row);
            rowDrafts.remove(row);
            rowDocs.remove(row);
        }
    }

    /** Blendet eine Spalte ein oder aus und merkt es (Auswahl und Datei bleiben sichtbar). */
    void setColumnVisible(String column, boolean visible) {
        columnChooser.setVisible(column, visible);
    }

    private List<Boolean> checkedList() {
        List<Boolean> checked = new ArrayList<>();
        for (int r = 0; r < table.getRowCount(); r++) {
            checked.add(Boolean.TRUE.equals(table.getValueAt(r, CHECK_COL)));
        }
        return checked;
    }

    /** Zielzeilen der Aktionen: die angehakten, sonst die markierten Zeilen. */
    private int[] targetRows(JTable view) {
        return OverviewRules.target(checkedList(), view.getSelectedRows());
    }

    /** Master-Checkbox: alle Zeilen auswählen oder die Auswahl aufheben (die Häkchen folgen der Auswahl). */
    private void setAllChecked(boolean on) {
        if (on) {
            overview.selectAll();
        } else {
            overview.clearSelection();
        }
    }

    /** Die Häkchen vorne spiegeln die ausgewählten Zeilen: Klick auf eine Zeile setzt ihr Häkchen, Aufheben der Auswahl nimmt es zurück. */
    private void syncChecks(JTable view) {
        for (int r = 0; r < table.getRowCount(); r++) {
            Boolean selected = view.isRowSelected(r);
            if (!selected.equals(table.getValueAt(r, CHECK_COL))) {
                table.setValueAt(selected, r, CHECK_COL);
            }
        }
    }

    /** Master-Checkbox in der Kopfzeile der Auswahl-Spalte: wählt alle Zeilen aus oder ab. */
    private void installSelectionHeader(JTable view) {
        masterBox.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        masterBox.setOpaque(true);
        masterBox.setToolTipText("Alle auswählen oder abwählen");
        view.getColumn("Auswahl").setHeaderRenderer((tbl, value, selected, focus, r, c) -> {
            masterBox.setSelected(OverviewRules.allChecked(checkedList()));
            masterBox.setBackground(javax.swing.UIManager.getColor("TableHeader.background"));
            return masterBox;
        });
        view.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = view.columnAtPoint(e.getPoint());
                if (col >= 0 && view.convertColumnIndexToModel(col) == CHECK_COL && table.getRowCount() > 0) {
                    setAllChecked(!OverviewRules.allChecked(checkedList()));
                }
            }
        });
    }

    /** Knöpfe für mehrere Zeilen (angehakt oder markiert): übernehmen, erneut verarbeiten, löschen. */
    private JPanel buildActions(JTable view) {
        adoptButton.setToolTipText("Übernimmt die erzeugten, geprüften E-Rechnungen der ausgewählten Zeilen ins Archiv.");
        reprocessButton.setToolTipText("Verarbeitet abgebrochene oder fehlgeschlagene PDFs der ausgewählten Zeilen erneut.");
        deleteButton.setToolTipText("Entfernt die ausgewählten abgeschlossenen Zeilen aus der Übersicht; nicht archivierte E-Rechnungen werden verworfen.");
        adoptButton.addActionListener(e -> adoptRows(targetRows(view)));
        reprocessButton.addActionListener(e -> reprocessRows(targetRows(view)));
        deleteButton.addActionListener(e -> removeRows(targetRows(view)));
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        bar.add(adoptButton);
        bar.add(reprocessButton);
        bar.add(deleteButton);
        bar.add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL) {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(8, 24);
            }
        });
        originalButton.addActionListener(e -> openDoc(view, RowDocs::original));
        outputButton.addActionListener(e -> openDoc(view, RowDocs::output));
        protocolButton.addActionListener(e -> openDoc(view, RowDocs::protocol));
        folderButton.addActionListener(e -> openDoc(view, RowDocs::folder));
        bar.add(originalButton);
        bar.add(outputButton);
        bar.add(protocolButton);
        bar.add(folderButton);
        bar.add(selectionLabel);
        return bar;
    }

    private void updateActions(JTable view) {
        List<String> status = rowStatuses();
        int[] rows = targetRows(view);
        adoptButton.setEnabled(!adopting && !OverviewRules.adoptable(status, rows).isEmpty());
        reprocessButton.setEnabled(!OverviewRules.reprocessable(status, rows).isEmpty());
        deleteButton.setEnabled(!OverviewRules.removable(status, rows, busy || adopting).isEmpty());
        originalButton.setEnabled(docOf(rows, RowDocs::original) != null);
        outputButton.setEnabled(docOf(rows, RowDocs::output) != null);
        protocolButton.setEnabled(docOf(rows, RowDocs::protocol) != null);
        folderButton.setEnabled(docOf(rows, RowDocs::folder) != null);
        long ticked = checkedList().stream().filter(Boolean::booleanValue).count();
        selectionLabel.setText(ticked > 0 ? ticked + " ausgewählt" : " ");
    }

    /** „Ins Archiv übernehmen“: archiviert die erzeugten Entwürfe der Zeilen nacheinander (unveränderlich, mit Hashes und Protokoll). */
    private void adoptRows(int[] selected) {
        List<Integer> rows = OverviewRules.adoptable(rowStatuses(), selected);
        if (rows.isEmpty() || adopting) {
            return;
        }
        adopting = true;
        List<ProcessingService.Draft> drafts = new ArrayList<>();
        for (int r : rows) {
            drafts.add(rowDrafts.get(r));
            setCell(r, 4, OverviewRules.ARCHIVING);
        }
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < rows.size(); i++) {
                    try {
                        publish(new Object[] {rows.get(i), service.archive(drafts.get(i)), null});
                    } catch (IOException e) {
                        publish(new Object[] {rows.get(i), null, e});
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] c : chunks) {
                    int row = (Integer) c[0];
                    var outcome = (ProcessingService.Outcome) c[1];
                    String number = rowDrafts.get(row).data().number();
                    if (outcome != null && outcome.archived()) {
                        Path folder = outcome.directory();
                        rowDocs.set(row, new RowDocs(folder.resolve("vorlage-original.pdf"), folder.resolve(rowDrafts.get(row).outputName()),
                            folder.resolve("pruefprotokoll.html"), folder));
                        setCell(row, 4, OverviewRules.ARCHIVED);
                        setCell(row, 5, folder.toString());
                        rowDrafts.set(row, null);
                        outcome.problems().forEach(n -> notes.add(number + ": " + n));
                    } else {
                        setCell(row, 4, OverviewRules.READY);
                        failures.add(number + ": " + (outcome != null ? String.join(" ", outcome.problems()) : ((Exception) c[2]).getMessage()));
                    }
                }
            }

            @Override
            protected void done() {
                adopting = false;
                updateActions(overview);
                if (!failures.isEmpty()) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Nicht ins Archiv übernommen:\n" + String.join("\n", failures),
                        "Archiv", JOptionPane.ERROR_MESSAGE);
                } else if (!notes.isEmpty()) {
                    JOptionPane.showMessageDialog(MainFrame.this, String.join("\n", notes), "Hinweis", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    /** „Erneut verarbeiten“: stellt abgebrochene oder fehlgeschlagene Zeilen wieder in die Warteschlange (gleiche Zeile). */
    private void reprocessRows(int[] selected) {
        for (int row : OverviewRules.reprocessable(rowStatuses(), selected)) {
            for (int col = 1; col <= 3; col++) {
                setCell(row, col, "");
            }
            setCell(row, 5, "");
            rowDocs.set(row, new RowDocs(rowFiles.get(row), null, null, null));
            setCell(row, 4, OverviewRules.WAITING);
            queue.add(new Pending(rowFiles.get(row), row));
        }
        if (!busy) {
            next();
        }
    }

    /** Hilfetext für das Symbol in der Ordner-Spalte: Pfad des Archivordners, bei Entwürfen Hinweis und Entwurfsdatei. */
    private String folderTooltip(int row) {
        ProcessingService.Draft draft = rowDrafts.get(row);
        if (draft != null) {
            return "<html>Entwurf, noch nicht im Archiv<br>" + draft.outputFile() + "<br>Klick: ansehen</html>";
        }
        String dir = String.valueOf(table.getValueAt(row, 5));
        return dir.isBlank() ? null : "<html>Archivordner<br>" + dir + "<br>Klick: Ordner öffnen</html>";
    }

    /** Klick auf das Symbol oder Doppelklick: erzeugten Entwurf ansehen, archivierte Rechnung im Ordner zeigen. */
    private void openRow(int row) {
        ProcessingService.Draft draft = rowDrafts.get(row);
        if (draft != null) {
            openFolder(draft.outputFile().toString());
        } else {
            String dir = String.valueOf(table.getValueAt(row, 5));
            if (new File(dir).exists()) {
                openFolder(dir);
            }
        }
    }

    /** Rechtsklick auf die Übersicht: ansehen, übernehmen, erneut verarbeiten, löschen, je nach Zeilenstatus ein- oder ausgeblendet. */
    private void installRowMenu(JTable view) {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        JMenuItem viewOriginal = new JMenuItem("Vorlage öffnen", MenuIcons.of(MenuIcons.Kind.PDF));
        JMenuItem viewOutput = new JMenuItem("E-Rechnung öffnen", MenuIcons.of(MenuIcons.Kind.FILE));
        JMenuItem viewProtocol = new JMenuItem("Prüfprotokoll öffnen", MenuIcons.of(MenuIcons.Kind.PROTOCOL));
        JMenuItem viewFolder = new JMenuItem("Archivordner öffnen", MenuIcons.of(MenuIcons.Kind.FOLDER));
        JMenuItem adopt = new JMenuItem("Ins Archiv übernehmen", MenuIcons.of(MenuIcons.Kind.ARCHIVE));
        JMenuItem again = new JMenuItem("Erneut verarbeiten", MenuIcons.of(MenuIcons.Kind.UPDATE));
        JMenuItem delete = new JMenuItem("Löschen", MenuIcons.of(MenuIcons.Kind.TRASH));
        viewOriginal.addActionListener(e -> openDoc(view, RowDocs::original));
        viewOutput.addActionListener(e -> openDoc(view, RowDocs::output));
        viewProtocol.addActionListener(e -> openDoc(view, RowDocs::protocol));
        viewFolder.addActionListener(e -> openDoc(view, RowDocs::folder));
        adopt.addActionListener(e -> adoptRows(targetRows(view)));
        again.addActionListener(e -> reprocessRows(targetRows(view)));
        delete.addActionListener(e -> removeRows(targetRows(view)));
        popup.add(viewOriginal);
        popup.add(viewOutput);
        popup.add(viewProtocol);
        popup.add(viewFolder);
        popup.addSeparator();
        popup.add(adopt);
        popup.add(again);
        popup.add(delete);
        MouseAdapter trigger = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                show(e);
            }

            private void show(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = view.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                if (!view.isRowSelected(row)) {
                    view.setRowSelectionInterval(row, row);
                }
                List<String> status = rowStatuses();
                int[] rows = targetRows(view);
                viewOriginal.setEnabled(docOf(rows, RowDocs::original) != null);
                viewOutput.setEnabled(docOf(rows, RowDocs::output) != null);
                viewProtocol.setEnabled(docOf(rows, RowDocs::protocol) != null);
                viewFolder.setEnabled(docOf(rows, RowDocs::folder) != null);
                adopt.setEnabled(!adopting && !OverviewRules.adoptable(status, rows).isEmpty());
                again.setEnabled(!OverviewRules.reprocessable(status, rows).isEmpty());
                delete.setEnabled(!OverviewRules.removable(status, rows, busy || adopting).isEmpty());
                popup.show(view, e.getX(), e.getY());
            }
        };
        view.addMouseListener(trigger);
    }

    private static JButton iconButton(MenuIcons.Kind kind, String tooltip) {
        JButton b = new JButton(MenuIcons.of(kind, 18));
        b.setToolTipText(tooltip);
        b.getAccessibleContext().setAccessibleName(tooltip);
        return b;
    }

    /** Der Pfad zu genau einer Zeile (angehakt oder markiert), wenn es ihn gibt und die Datei vorhanden ist; sonst null. */
    private Path docOf(int[] rows, java.util.function.Function<RowDocs, Path> which) {
        if (rows.length != 1 || rows[0] < 0 || rows[0] >= rowDocs.size()) {
            return null;
        }
        Path p = which.apply(rowDocs.get(rows[0]));
        return p != null && java.nio.file.Files.exists(p) ? p : null;
    }

    private void openDoc(JTable view, java.util.function.Function<RowDocs, Path> which) {
        Path p = docOf(targetRows(view), which);
        if (p != null) {
            openFolder(p.toString());
        }
    }

    /** Für Tests: was zu einer Zeile geöffnet werden kann. */
    RowDocs docs(int row) {
        return rowDocs.get(row);
    }

    /** Beenden: erzeugte, noch nicht archivierte E-Rechnungen gehen verloren, deshalb wird vorher nachgefragt. */
    private void requestExit() {
        long pending = rowDrafts.stream().filter(java.util.Objects::nonNull).count();
        if (pending > 0 && JOptionPane.showConfirmDialog(this, pending + " erzeugte E-Rechnung(en) sind noch nicht im Archiv und werden beim "
            + "Beenden verworfen.\nTrotzdem beenden?", "Beenden", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
            != JOptionPane.OK_OPTION) {
            return;
        }
        for (var draft : rowDrafts) {
            if (draft != null) {
                try {
                    service.discard(draft);
                } catch (IOException ignored) {
                    // Beim nächsten Start werden die Entwurfsdateien ohnehin gelöscht.
                }
            }
        }
        dispose();
        System.exit(0);
    }

    private void setCell(int row, int col, String value) {
        table.setValueAt(value, row, col);
    }

    /** Öffnet die Unterstützungsseite im Browser; nur auf Klick, das Programm selbst verbindet sich dafür nirgends hin. */
    private void openSupportPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI.create(io.github.ebolansk.erechnung.Support.COFFEE_URL));
                return;
            }
        } catch (IOException | UnsupportedOperationException e) {
            // unten: Adresse zum Kopieren anzeigen
        }
        JOptionPane.showMessageDialog(this, "Der Browser lässt sich nicht öffnen. Die Adresse lautet:\n"
            + io.github.ebolansk.erechnung.Support.COFFEE_URL, "Projekt unterstützen", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openFolder(String dir) {
        if (dir == null || dir.isBlank() || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(dir));
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(this, "Kann nicht geöffnet werden:\n" + dir, "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
