// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.archive.ArchiveRecord;
import de.provitex.erechnung.archive.ArchiveSearch.Query;
import de.provitex.erechnung.archive.BelegStatus;
import de.provitex.erechnung.archive.IntegrityCheck;
import de.provitex.erechnung.service.ArchiveService;
import de.provitex.erechnung.util.GermanFormats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Archiv: Suche mit Filtern, Treffer öffnen (E-Rechnung, Vorlage, Protokoll, beide nebeneinander) und Integritätsprüfung. */
public final class ArchivePanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String ALL = "Alle";

    private final ArchiveService service;
    private final Consumer<Path> opener;
    private final Component dialogParent;

    private final JTextField text = new JTextField(16);
    private final JTextField from = new JTextField(8);
    private final JTextField to = new JTextField(8);
    private final JTextField minGross = new JTextField(7);
    private final JTextField maxGross = new JTextField(7);
    private final JComboBox<String> mandant = new JComboBox<>(new String[] {ALL});
    private final JComboBox<String> status = new JComboBox<>(new String[] {ALL, label(BelegStatus.PDF_IST_ORIGINAL.name()),
        label(BelegStatus.E_RECHNUNG_IST_ORIGINAL.name())});
    private final JComboBox<String> version = new JComboBox<>(new String[] {ALL});
    private final JCheckBox expired = new JCheckBox("Aufbewahrungsfrist abgelaufen");
    private final JLabel info = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(new String[] {"Rechnungsnummer", "Datum", "Mandant", "Käufer",
        "Brutto", "Format", "Beleg-Status", "Aufbewahren bis", "Version"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<ArchiveRecord> shown = new ArrayList<>();
    private List<ArchiveRecord> everything = List.of();

    public ArchivePanel(ArchiveService service, Consumer<Path> opener, Component dialogParent) {
        super(new BorderLayout(0, 6));
        this.service = service;
        this.opener = opener;
        this.dialogParent = dialogParent;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel filters = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        JPanel r1 = row();
        r1.add(new JLabel("Suchen:"));
        r1.add(text);
        r1.add(new JLabel("Datum von"));
        r1.add(from);
        r1.add(new JLabel("bis"));
        r1.add(to);
        r1.add(new JLabel("Brutto von"));
        r1.add(minGross);
        r1.add(new JLabel("bis"));
        r1.add(maxGross);
        JPanel r2 = row();
        r2.add(new JLabel("Mandant"));
        r2.add(mandant);
        r2.add(new JLabel("Beleg-Status"));
        r2.add(status);
        JPanel r3 = row();
        r3.add(new JLabel("Tool-Version"));
        r3.add(version);
        r3.add(expired);
        JButton search = new JButton("Suchen");
        search.addActionListener(e -> search());
        JButton reset = new JButton("Zurücksetzen");
        reset.addActionListener(e -> reset());
        r3.add(search);
        r3.add(reset);
        filters.add(r1);
        filters.add(r2);
        filters.add(r3);
        text.setToolTipText("Rechnungsnummer, Käufer, Ort oder Mandant; mehrere Wörter müssen alle vorkommen.");
        from.setToolTipText("TT.MM.JJJJ");
        to.setToolTipText("TT.MM.JJJJ");
        text.addActionListener(e -> search());
        add(filters, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setRowHeight(22);
        int[] widths = {120, 80, 150, 190, 90, 90, 160, 110, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setFillsViewportHeight(true);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected(r -> r.output());
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JPanel buttons = row();
        buttons.add(button("E-Rechnung öffnen", () -> openSelected(ArchiveRecord::output)));
        buttons.add(button("Vorlage öffnen", () -> openSelected(ArchiveRecord::originalPdf)));
        buttons.add(button("Prüfprotokoll öffnen", () -> openSelected(ArchiveRecord::protocol)));
        buttons.add(button("Vorlage und E-Rechnung öffnen", this::openBoth));
        buttons.add(button("Ordner öffnen", () -> openSelected(ArchiveRecord::dir)));
        buttons.add(button("Integrität prüfen", this::checkIntegrity));
        south.add(buttons, BorderLayout.NORTH);
        south.add(info, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        reload();
    }

    private static JPanel row() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    }

    private static JButton button(String label, Runnable r) {
        JButton b = new JButton(label);
        b.addActionListener(e -> r.run());
        return b;
    }

    static String label(String statusName) {
        return switch (statusName) {
            case "PDF_IST_ORIGINAL" -> "PDF ist Original";
            case "E_RECHNUNG_IST_ORIGINAL" -> "E-Rechnung ist Original";
            default -> statusName;
        };
    }

    /** Liest das Archiv neu ein und füllt die Auswahllisten; danach läuft die Suche mit den aktuellen Filtern. */
    public void reload() {
        try {
            everything = service.records();
        } catch (IOException | RuntimeException e) {
            everything = List.of();
            info.setText("Das Archiv kann nicht gelesen werden: " + e.getMessage());
            return;
        }
        fill(mandant, everything.stream().map(ArchiveRecord::mandantName).toList());
        fill(version, everything.stream().map(ArchiveRecord::toolVersion).toList());
        search();
    }

    private static void fill(JComboBox<String> box, List<String> values) {
        Object selected = box.getSelectedItem();
        Set<String> distinct = new LinkedHashSet<>(values.stream().filter(v -> !v.isBlank()).sorted().toList());
        box.removeAllItems();
        box.addItem(ALL);
        distinct.forEach(box::addItem);
        if (selected != null && (selected.equals(ALL) || distinct.contains(selected))) {
            box.setSelectedItem(selected);
        }
    }

    /** Wendet die Filter an. Ungültige Eingaben erscheinen als Hinweis, die Liste bleibt unverändert. */
    public void search() {
        Query q;
        try {
            q = query();
        } catch (IllegalArgumentException e) {
            info.setText("Eingabe prüfen: " + e.getMessage());
            return;
        }
        try {
            shown.clear();
            shown.addAll(service.search(q));
        } catch (IOException | RuntimeException e) {
            info.setText("Suche nicht möglich: " + e.getMessage());
            return;
        }
        model.setRowCount(0);
        for (ArchiveRecord r : shown) {
            model.addRow(new Object[] {r.number(), r.issueDate() == null ? "" : DATE.format(r.issueDate()), r.mandantName(),
                r.buyerName(), r.gross() == null ? "" : GermanFormats.formatAmount(r.gross()) + " €", r.format(),
                label(r.belegStatus()) + (r.belegOverridden() ? " (manuell)" : ""),
                r.retainUntil() == null ? "" : DATE.format(r.retainUntil()), r.toolVersion()});
        }
        info.setText(shown.size() + " Treffer von " + everything.size() + " archivierten Rechnungen. Doppelklick öffnet die E-Rechnung.");
    }

    private Query query() {
        return new Query(text.getText(), date(from, "Datum von"), date(to, "Datum bis"), amount(minGross, "Brutto von"),
            amount(maxGross, "Brutto bis"), mandantId(), statusName(), selectedOrNull(version), expired.isSelected() ? Boolean.TRUE : null);
    }

    private static LocalDate date(JTextField f, String name) {
        String s = f.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return GermanFormats.parseDate(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " ist kein Datum (TT.MM.JJJJ).");
        }
    }

    private static BigDecimal amount(JTextField f, String name) {
        String s = f.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return GermanFormats.parseAmount(s);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + " ist kein Betrag.");
        }
    }

    private String mandantId() {
        String name = selectedOrNull(mandant);
        if (name == null) {
            return null;
        }
        return everything.stream().filter(r -> r.mandantName().equals(name)).map(ArchiveRecord::mandantId).findFirst().orElse(null);
    }

    private String statusName() {
        int i = status.getSelectedIndex();
        return i == 1 ? BelegStatus.PDF_IST_ORIGINAL.name() : i == 2 ? BelegStatus.E_RECHNUNG_IST_ORIGINAL.name() : null;
    }

    private static String selectedOrNull(JComboBox<String> box) {
        Object o = box.getSelectedItem();
        return o == null || o.equals(ALL) ? null : o.toString();
    }

    private void reset() {
        text.setText("");
        from.setText("");
        to.setText("");
        minGross.setText("");
        maxGross.setText("");
        mandant.setSelectedIndex(0);
        status.setSelectedIndex(0);
        version.setSelectedIndex(0);
        expired.setSelected(false);
        search();
    }

    /** Für Tests und Aufrufer: aktuell angezeigte Treffer. */
    public List<ArchiveRecord> results() {
        return List.copyOf(shown);
    }

    public void setText(String s) {
        text.setText(s);
    }

    public void select(int row) {
        table.setRowSelectionInterval(row, row);
    }

    private ArchiveRecord selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            info.setText("Bitte zuerst eine Rechnung in der Liste auswählen.");
            return null;
        }
        return shown.get(row);
    }

    private void openSelected(java.util.function.Function<ArchiveRecord, Path> file) {
        ArchiveRecord r = selected();
        if (r != null) {
            open(file.apply(r));
        }
    }

    private void openBoth() {
        ArchiveRecord r = selected();
        if (r != null) {
            open(r.originalPdf());
            open(r.output());
        }
    }

    private void open(Path p) {
        try {
            opener.accept(p);
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(dialogParent, "Die Datei kann nicht geöffnet werden:\n" + p + "\n" + e.getMessage(), "Fehler",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkIntegrity() {
        info.setText("Integritätsprüfung läuft …");
        new SwingWorker<IntegrityCheck.Report, Void>() {
            @Override
            protected IntegrityCheck.Report doInBackground() throws Exception {
                return service.check();
            }

            @Override
            protected void done() {
                try {
                    IntegrityCheck.Report report = get();
                    info.setText(report.summary());
                    showReport(dialogParent, report);
                } catch (Exception e) {
                    info.setText("Integritätsprüfung fehlgeschlagen: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        }.execute();
    }

    /** Zeigt das Ergebnis; bei Abweichungen mit der Liste der Befunde. */
    public static void showReport(Component parent, IntegrityCheck.Report report) {
        StringBuilder sb = new StringBuilder(report.summary()).append("\n");
        for (IntegrityCheck.Finding f : report.findings()) {
            sb.append('\n').append(f.error() ? "FEHLER  " : "Hinweis ").append(f.dir() == null ? "" : f.dir() + "\n         ")
                .append(f.message());
        }
        if (!report.log().valid()) {
            sb.append("\n\nFEHLER  Protokoll: ").append(report.log().message());
        }
        JTextArea area = new JTextArea(sb.toString(), 16, 90);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(parent, new JScrollPane(area), "Integritätsprüfung",
            report.ok() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }
}
