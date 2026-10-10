// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.ai.AiSuggestion;
import io.github.ebolansk.erechnung.archive.Confirmation;
import io.github.ebolansk.erechnung.extract.ExtractionResult;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.model.DocumentType;
import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.model.Party;
import io.github.ebolansk.erechnung.service.InvoiceChecks;
import io.github.ebolansk.erechnung.util.GermanFormats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

/** Prüfmaske: PDF links, bestätigungspflichtige Felder rechts, mit Live-Prüfung. */
public final class ReviewPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Color AI_COLOR = new Color(255, 244, 196);
    private static final String[] COLUMNS = {"Bezeichnung", "Menge", "Einheit", "Einzelpreis", "USt %", "Kat.", "Befreiungsgrund"};

    private final ExtractionResult extraction;
    /** Die Stammdaten des Ausstellers (Tab „Verkäufer“), vorbelegt aus dem Mandanten und hier änderbar. */
    private final MandantPanel sellerPanel;
    private final Mandant originalSeller;
    private final OutputFormat format;
    private final Runnable onAccept;
    private final AiHelp ai;
    private final Set<JTextField> aiFilled = new LinkedHashSet<>();
    private final JButton aiButton = new JButton("Mit KI nachbessern");
    private PdfPreview preview;
    private boolean building = true;

    private final JComboBox<String> typeBox = new JComboBox<>(new String[] {"Rechnung (380)", "Gutschrift (381)"});
    private final JTextField number = new JTextField(22);
    private final JTextField issue = new JTextField();
    private final JTextField delivery = new JTextField();
    private final JTextField deliveryEnd = new JTextField();
    private final JTextField due = new JTextField();
    private final JTextField terms = new JTextField();
    private final JTextField buyerRef = new JTextField();
    private final JTextField currency = new JTextField("EUR");
    private final JTextField bName = new JTextField();
    private final JTextField bStreet = new JTextField();
    private final JTextField bZip = new JTextField();
    private final JTextField bCity = new JTextField();
    private final JTextField bCountry = new JTextField("DE");
    private final JTextField bVat = new JTextField();
    private final JTextField bEmail = new JTextField();
    private final ItemTableModel items = new ItemTableModel();
    private final JLabel computedLabel = new JLabel(" ");
    private final JLabel printedLabel = new JLabel(" ");
    private final JLabel matchLabel = new JLabel(" ");
    private final JTextArea problemsArea = new JTextArea(4, 40);
    private final JTextArea notesArea = new JTextArea(2, 40);
    private final JButton okButton = new JButton("E-Rechnung erzeugen");
    private final JCheckBox confirmMismatch = new JCheckBox("Abweichung der Summen geprüft und bewusst bestätigt");
    private JTable table;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statusLine = new JLabel(" ");
    private static final int CHECK_TAB = 4;
    private boolean hasMismatch;
    private String mismatchKey = "";

    private Optional<InvoiceData> current = Optional.empty();
    private Optional<InvoiceData> result = Optional.empty();
    private List<String> problems = List.of();
    private String totalsText = "";

    public ReviewPanel(byte[] pdf, ExtractionResult extraction, Mandant mandant, Runnable onAccept, Runnable onCancel) {
        this(pdf, extraction, mandant, null, onAccept, onCancel);
    }

    public ReviewPanel(byte[] pdf, ExtractionResult extraction, Mandant mandant, AiHelp ai, Runnable onAccept, Runnable onCancel) {
        this(pdf, extraction, mandant, OutputFormat.XRECHNUNG, ai, onAccept, onCancel);
    }

    /** format bestimmt, welche Angaben Pflicht sind (XRechnung verlangt mehr als ZUGFeRD). */
    public ReviewPanel(byte[] pdf, ExtractionResult extraction, Mandant mandant, OutputFormat format, AiHelp ai,
                       Runnable onAccept, Runnable onCancel) {
        super(new BorderLayout());
        this.format = format;
        this.ai = ai;
        this.extraction = extraction;
        this.sellerPanel = new MandantPanel(mandant, ai == null ? null : new MandantAi(ai.service(), ai.text(), ai.fileName()));
        this.originalSeller = sellerPanel.snapshot();
        okButton.setText(format == OutputFormat.ZUGFERD ? "ZUGFeRD erzeugen" : "XRechnung erzeugen");
        this.onAccept = onAccept;
        fill(extraction.draft());

        table = new JTable(items);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        JButton add = new JButton("Position hinzufügen");
        JButton remove = new JButton("Position entfernen");
        add.addActionListener(e -> items.addRow(new String[] {"", "1", "C62", "0,00", "19", "S", ""}));
        remove.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                items.removeRow(r);
            }
        });
        JPanel itemButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        itemButtons.add(add);
        itemButtons.add(remove);
        JScrollPane tableScroll = new JScrollPane(table);

        problemsArea.setEditable(false);
        problemsArea.setForeground(new Color(176, 0, 32));
        problemsArea.setLineWrap(true);
        problemsArea.setWrapStyleWord(true);
        notesArea.setEditable(false);
        notesArea.setForeground(Color.DARK_GRAY);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setText(String.join("\n", extraction.notes()));

        JPanel belegTab = new Ui.Form()
            .section("Beleg")
            .row("Typ", typeBox).row("Rechnungsnummer *", number).row("Rechnungsdatum *", Ui.dateRow(issue))
            .row("Leistungsdatum *", Ui.dateRow(delivery)).row("Leistungszeitraum bis", Ui.dateRow(deliveryEnd)).row("Fälligkeitsdatum", Ui.dateRow(due))
            .row("Zahlungsbedingungen", terms).row(format == OutputFormat.XRECHNUNG ? "Käuferreferenz / Leitweg-ID *" : "Käuferreferenz / Leitweg-ID", buyerRef).row("Währung", currency)
            .build();
        JPanel buyerTab = new Ui.Form()
            .section("Käufer")
            .row("Name *", bName).row("Straße *", bStreet).row("PLZ *", bZip).row("Ort *", bCity)
            .row("Land (ISO) *", bCountry).row("USt-IdNr.", bVat).row("E-Mail * (BT-49)", bEmail)
            .build();
        JPanel itemsTab = new JPanel(new BorderLayout(0, 6));
        itemsTab.setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 8));
        table.setFillsViewportHeight(true);
        itemsTab.add(tableScroll, BorderLayout.CENTER);
        itemsTab.add(itemButtons, BorderLayout.SOUTH);
        JPanel checkTab = new Ui.Form()
            .section("Summenabgleich")
            .wide(computedLabel).wide(printedLabel).wide(matchLabel).wide(confirmMismatch)
            .section("Noch zu klären")
            .wide(new JScrollPane(problemsArea))
            .section("Hinweise aus dem Auslesen")
            .wide(new JScrollPane(notesArea))
            .build();
        tabs.addTab("Beleg", formScroll(belegTab));
        tabs.addTab("Verkäufer", sellerScroll());
        tabs.addTab("Käufer", formScroll(buyerTab));
        tabs.addTab("Positionen", itemsTab);
        tabs.addTab("Prüfung", formScroll(checkTab));
        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(tabs, BorderLayout.CENTER);
        statusLine.setBorder(new javax.swing.border.EmptyBorder(2, 8, 2, 8));
        right.add(statusLine, BorderLayout.SOUTH);
        right.setPreferredSize(new Dimension(580, 700));

        JComponent left;
        try {
            preview = new PdfPreview(pdf);
            left = preview;
        } catch (IOException e) {
            left = new JLabel("Vorschau nicht verfügbar: " + e.getMessage());
        }
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        JButton cancel = new JButton("Abbrechen");
        okButton.addActionListener(e -> accept());
        cancel.addActionListener(e -> onCancel.run());
        JPanel buttons = new JPanel(new BorderLayout());
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtons.add(cancel);
        rightButtons.add(okButton);
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(aiButton);
        buttons.add(leftButtons, BorderLayout.WEST);
        buttons.add(rightButtons, BorderLayout.EAST);
        add(buttons, BorderLayout.SOUTH);
        configureAiButton();

        for (JTextField f : List.of(number, issue, delivery, deliveryEnd, due, terms, buyerRef, currency, bName, bStreet,
            bZip, bCity, bCountry, bVat, bEmail)) {
            Ui.onChange(f, this::refresh);
        }
        sellerPanel.onChange(this::refresh);
        typeBox.addActionListener(e -> refresh());
        confirmMismatch.setVisible(false);
        confirmMismatch.addActionListener(e -> refresh());
        items.addTableModelListener(e -> refresh());
        building = false;
        refresh();
    }

    /** Der Tab „Verkäufer“: dieselben Stammdaten wie im Mandanten-Dialog, hier änderbar (und mit KI aus der Rechnung aktualisierbar). */
    private JScrollPane sellerScroll() {
        return formScroll(sellerPanel);
    }

    /**
     * Die Stammdaten des Ausstellers, wie sie im Tab „Verkäufer“ stehen. Weichen sie vom Mandanten ab, speichert der Aufrufer sie
     * im Mandanten, damit Rechnung und Mandant übereinstimmen.
     */
    public Mandant mandant() {
        return sellerPanel.snapshot();
    }

    /** Wurden die Stammdaten im Tab „Verkäufer“ gegenüber dem Mandanten geändert? */
    public boolean mandantChanged() {
        return !sellerPanel.snapshot().equals(originalSeller);
    }

    private static JScrollPane formScroll(JPanel form) {
        JScrollPane sp = new JScrollPane(new Ui.TrackingPanel(form));
        sp.setBorder(null);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /** Eine Zeile unter den Tabs zeigt immer, ob noch etwas zu klären ist; der Tab „Prüfung“ trägt die Anzahl offener Punkte. */
    private void updateStatusLine() {
        Color red = new Color(176, 0, 32);
        if (!problems.isEmpty()) {
            statusLine.setForeground(red);
            statusLine.setText("<html>Noch zu klären: " + esc(problems.get(0)) + (problems.size() > 1 ? " (+" + (problems.size() - 1) + " weitere, siehe Tab „Prüfung“)" : "")
                + "</html>");
            tabs.setTitleAt(CHECK_TAB, "Prüfung (" + problems.size() + ")");
            tabs.setForegroundAt(CHECK_TAB, red);
        } else if (hasMismatch && !confirmMismatch.isSelected()) {
            statusLine.setForeground(red);
            statusLine.setText("Die Summen weichen ab: bitte im Tab „Prüfung“ bestätigen.");
            tabs.setTitleAt(CHECK_TAB, "Prüfung (!)");
            tabs.setForegroundAt(CHECK_TAB, red);
        } else {
            statusLine.setForeground(new Color(10, 107, 42));
            statusLine.setText("Alles ausgefüllt, bereit zum Erzeugen.");
            tabs.setTitleAt(CHECK_TAB, "Prüfung");
            tabs.setForegroundAt(CHECK_TAB, null);
        }
    }

    private void fill(InvoiceData d) {
        typeBox.setSelectedIndex(d.type() == DocumentType.CREDIT_NOTE ? 1 : 0);
        number.setText(d.number());
        issue.setText(fmt(d.issueDate()));
        delivery.setText(fmt(d.deliveryDate()));
        deliveryEnd.setText(fmt(d.deliveryPeriodEnd()));
        due.setText(fmt(d.dueDate()));
        terms.setText(d.paymentTerms());
        buyerRef.setText(d.buyerReference());
        currency.setText(d.currency() == null || d.currency().isBlank() ? "EUR" : d.currency());
        Party b = d.buyer();
        bName.setText(b.name());
        bStreet.setText(b.street());
        bZip.setText(b.zip());
        bCity.setText(b.city());
        bCountry.setText(b.country() == null || b.country().isBlank() ? "DE" : b.country());
        bVat.setText(b.vatId());
        bEmail.setText(b.email());
        for (LineItem i : d.items()) {
            items.addRow(new String[] {i.name(), num(i.quantity()), i.unit(), num(i.unitPrice()), num(i.vatPercent()),
                i.vatCategory(), i.exemptionReason()});
        }
    }

    private void refresh() {
        if (building) {
            return;
        }
        for (JTextField f : List.of(number, issue, delivery, deliveryEnd, due, bName)) {
            Ui.mark(f, false);
        }
        aiFilled.forEach(f -> f.setBackground(AI_COLOR));
        List<String> parseErrors = new ArrayList<>();
        InvoiceData d = build(parseErrors);
        List<String> all = new ArrayList<>(parseErrors);
        if (d != null) {
            all.addAll(InvoiceChecks.missingFields(d, format));
        }
        problems = all;
        current = Optional.ofNullable(d);
        problemsArea.setText(String.join("\n", problems));
        hasMismatch = false;

        if (d != null && !d.items().isEmpty()) {
            var t = InvoiceCalculator.compute(d.items());
            computedLabel.setText("Berechnet: Netto " + GermanFormats.formatAmount(t.netTotal()) + " € · Steuer "
                + GermanFormats.formatAmount(t.taxTotal()) + " € · Brutto " + GermanFormats.formatAmount(t.grossTotal()) + " €");
            var p = extraction.printed();
            printedLabel.setText("Im PDF gelesen: Netto " + show(p.net()) + " · Steuer " + show(p.tax()) + " · Brutto " + show(p.gross()));
            List<String> mismatch = InvoiceChecks.totalsMismatch(t, p);
            hasMismatch = !mismatch.isEmpty();
            boolean nothing = p.net() == null && p.tax() == null && p.gross() == null;
            if (nothing) {
                totalsText = "Im PDF wurden keine Summen erkannt. Bitte die berechneten Summen mit dem PDF vergleichen.";
                matchLabel.setForeground(Color.DARK_GRAY);
            } else if (mismatch.isEmpty()) {
                totalsText = "Summen stimmen überein.";
                matchLabel.setForeground(new Color(10, 107, 42));
            } else {
                totalsText = "Abweichung: " + String.join(" ", mismatch);
                matchLabel.setForeground(new Color(176, 0, 32));
            }
        } else {
            computedLabel.setText("Berechnet: –");
            printedLabel.setText(" ");
            totalsText = "Summenabgleich nicht möglich, solange Eingaben fehlen.";
            matchLabel.setForeground(Color.DARK_GRAY);
        }
        matchLabel.setText("<html>" + esc(totalsText) + "</html>");
        String key = hasMismatch ? totalsText : "";
        if (!key.equals(mismatchKey)) {
            mismatchKey = key;
            confirmMismatch.setSelected(false);
        }
        confirmMismatch.setVisible(hasMismatch);
        okButton.setEnabled(d != null && problems.isEmpty() && (!hasMismatch || confirmMismatch.isSelected()));
        updateStatusLine();

    }

    private InvoiceData build(List<String> errors) {
        LocalDate issueDate = date(issue, "Rechnungsdatum", errors);
        LocalDate deliveryDate = date(delivery, "Leistungsdatum", errors);
        LocalDate endDate = date(deliveryEnd, "Leistungszeitraum bis", errors);
        LocalDate dueDate = date(due, "Fälligkeitsdatum", errors);
        List<LineItem> lines = new ArrayList<>();
        for (int i = 0; i < items.getRowCount(); i++) {
            String p = "Position " + (i + 1);
            try {
                BigDecimal qty = GermanFormats.parseAmount(items.get(i, 1));
                BigDecimal price = GermanFormats.parseAmount(items.get(i, 3));
                BigDecimal vat = GermanFormats.parseAmount(items.get(i, 4));
                lines.add(new LineItem(items.get(i, 0).trim(), "", items.get(i, 2).trim().isEmpty() ? "C62" : items.get(i, 2).trim(),
                    qty, price, vat, items.get(i, 5).trim().isEmpty() ? "S" : items.get(i, 5).trim().toUpperCase(),
                    items.get(i, 6).trim()));
            } catch (IllegalArgumentException e) {
                errors.add(p + ": Menge, Einzelpreis oder Steuersatz ist nicht lesbar.");
            }
        }
        if (!errors.isEmpty()) {
            return null;
        }
        Mandant seller = sellerPanel.snapshot();
        DocumentType type = typeBox.getSelectedIndex() == 1 ? DocumentType.CREDIT_NOTE : DocumentType.INVOICE;
        Party buyer = new Party(bName.getText().trim(), bStreet.getText().trim(), bZip.getText().trim(), bCity.getText().trim(),
            bCountry.getText().trim().toUpperCase(), bVat.getText().replaceAll("\\s+", ""), "", bEmail.getText().trim(), "", "");
        return new InvoiceData(type, number.getText().trim(), issueDate, deliveryDate, endDate, dueDate,
            currency.getText().trim().toUpperCase(), buyerRef.getText().trim(), terms.getText().trim(),
            seller.toParty(), buyer, GermanFormats.normalizeIban(seller.iban()), nz(seller.bic()), lines);
    }

    private static LocalDate date(JTextField f, String label, List<String> errors) {
        String t = f.getText().trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return GermanFormats.parseDate(t);
        } catch (IllegalArgumentException e) {
            Ui.mark(f, true);
            errors.add(label + ": Datum nicht lesbar (Format TT.MM.JJJJ).");
            return null;
        }
    }

    public void accept() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        refresh();
        if (okButton.isEnabled() && current.isPresent()) {
            result = current;
            onAccept.run();
        }
    }

    /** Die ausdrücklichen Bestätigungen des Nutzers (gelesene Summen, Abweichung). Der Beleg-Status wird beim Archivieren nach Datum bestimmt. */
    public Confirmation confirmation() {
        var p = extraction.printed();
        return new Confirmation(p.net(), p.tax(), p.gross(), hasMismatch && confirmMismatch.isSelected(), null);
    }

    private void configureAiButton() {
        boolean usable = ai != null && ai.service().available();
        aiButton.setEnabled(usable);
        aiButton.setToolTipText(usable
            ? "Sendet den Text dieser Rechnung nach Ihrer Bestätigung an die konfigurierte KI und zeigt einen Vorschlag."
            : "Die KI ist nicht eingerichtet (Konfiguration → KI).");
        aiButton.addActionListener(e -> askAi());
    }

    private Map<String, JTextField> fieldMap() {
        Map<String, JTextField> m = new LinkedHashMap<>();
        m.put("number", number);
        m.put("issue", issue);
        m.put("delivery", delivery);
        m.put("deliveryEnd", deliveryEnd);
        m.put("due", due);
        m.put("terms", terms);
        m.put("buyerRef", buyerRef);
        m.put("currency", currency);
        m.put("bName", bName);
        m.put("bStreet", bStreet);
        m.put("bZip", bZip);
        m.put("bCity", bCity);
        m.put("bCountry", bCountry);
        m.put("bVat", bVat);
        m.put("bEmail", bEmail);
        return m;
    }

    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("number", "Rechnungsnummer"), Map.entry("issue", "Rechnungsdatum"), Map.entry("delivery", "Leistungsdatum"),
        Map.entry("deliveryEnd", "Leistungszeitraum bis"), Map.entry("due", "Fälligkeitsdatum"),
        Map.entry("terms", "Zahlungsbedingungen"), Map.entry("buyerRef", "Käuferreferenz"), Map.entry("currency", "Währung"),
        Map.entry("bName", "Käufer: Name"), Map.entry("bStreet", "Käufer: Straße"), Map.entry("bZip", "Käufer: PLZ"),
        Map.entry("bCity", "Käufer: Ort"), Map.entry("bCountry", "Käufer: Land"), Map.entry("bVat", "Käufer: USt-IdNr."),
        Map.entry("bEmail", "Käufer: E-Mail"), Map.entry("items", "Positionen"));

    /** Bestätigung einholen, KI fragen, Vorschlag feldweise anzeigen. */
    private void askAi() {
        if (ai == null || !ai.service().available()) {
            return;
        }
        AiFlow.start(this, ai.service(), ai.text(), "dieser einen Rechnung",
            cancel -> ai.service().ask(ai.text(), ai.fileName(), extraction.printed(), cancel), this::showSuggestion,
            busy -> {
                aiButton.setEnabled(!busy);
                aiButton.setText(busy ? "KI arbeitet …" : "Mit KI nachbessern");
            });
    }

    private void showSuggestion(AiSuggestion s) {
        List<AiSuggestionDialog.Row> rows = suggestionRows(s);
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Die KI bestätigt die eingelesenen Werte, es gibt nichts zu übernehmen.", "KI",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<AiSuggestionDialog.Row> chosen = new AiSuggestionDialog(SwingUtilities.getWindowAncestor(this), rows, s.notes()).showAndGet();
        applySuggestion(s, chosen);
    }

    List<AiSuggestionDialog.Row> suggestionRows(AiSuggestion s) {
        List<AiSuggestionDialog.Row> rows = new ArrayList<>();
        fieldMap().forEach((key, field) -> {
            String v = s.fields().get(key);
            if (v != null && !same(key, field.getText(), v)) {
                rows.add(new AiSuggestionDialog.Row(key, LABELS.get(key), field.getText().trim(), v, nz(s.warnings().get(key))));
            }
        });
        if (!s.items().isEmpty() && !sameItems(s.items())) {
            var t = InvoiceCalculator.compute(s.items());
            rows.add(new AiSuggestionDialog.Row("items", LABELS.get("items"), itemsSummary(),
                s.items().size() + " Positionen, netto " + GermanFormats.formatAmount(t.netTotal()) + " €",
                nz(s.warnings().get("items"))));
        }
        return rows;
    }

    private static boolean same(String key, String a, String b) {
        String x = a.trim();
        String y = b.trim();
        return key.equals("bVat") ? x.replaceAll("\\s+", "").equalsIgnoreCase(y.replaceAll("\\s+", "")) : x.equalsIgnoreCase(y);
    }

    private String itemsSummary() {
        List<String> errors = new ArrayList<>();
        InvoiceData d = build(errors);
        if (d == null || d.items().isEmpty()) {
            return items.getRowCount() + " Positionen";
        }
        return d.items().size() + " Positionen, netto "
            + GermanFormats.formatAmount(InvoiceCalculator.compute(d.items()).netTotal()) + " €";
    }

    private boolean sameItems(List<LineItem> proposed) {
        if (proposed.size() != items.getRowCount()) {
            return false;
        }
        for (int i = 0; i < proposed.size(); i++) {
            LineItem p = proposed.get(i);
            try {
                if (!items.get(i, 0).trim().equalsIgnoreCase(p.name())
                    || GermanFormats.parseAmount(items.get(i, 1)).compareTo(p.quantity()) != 0
                    || GermanFormats.parseAmount(items.get(i, 3)).compareTo(p.unitPrice()) != 0
                    || GermanFormats.parseAmount(items.get(i, 4)).compareTo(p.vatPercent()) != 0) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    void applySuggestion(AiSuggestion s, List<AiSuggestionDialog.Row> chosen) {
        if (chosen.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        Map<String, JTextField> fields = fieldMap();
        for (AiSuggestionDialog.Row r : chosen) {
            if (r.key().equals("items")) {
                items.clear();
                for (LineItem i : s.items()) {
                    items.addRow(new String[] {i.name(), num(i.quantity()), i.unit(), num(i.unitPrice()), num(i.vatPercent()),
                        i.vatCategory(), i.exemptionReason()});
                }
            } else {
                JTextField f = fields.get(r.key());
                f.setText(r.aiValue());
                aiFilled.add(f);
            }
            keys.add(r.key());
        }
        refresh();
        try {
            ai.service().recordApplied(ai.fileName(), keys);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Die Übernahme der KI-Werte konnte nicht protokolliert werden: " + e.getMessage(),
                "Protokoll", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void close() {
        if (preview != null) {
            preview.close();
        }
    }

    public Optional<InvoiceData> result() {
        return result;
    }

    public List<String> currentProblems() {
        return problems;
    }

    public boolean acceptEnabled() {
        return okButton.isEnabled();
    }

    public String totalsStatus() {
        return totalsText;
    }

    String okButtonText() {
        return okButton.getText();
    }

    MandantPanel sellerPanel() {
        return sellerPanel;
    }

    JButton aiButton() {
        return aiButton;
    }

    JCheckBox confirmMismatchBox() {
        return confirmMismatch;
    }

    JTable itemTable() {
        return table;
    }

    JTextField buyerEmailField() {
        return bEmail;
    }

    JTextField issueDateField() {
        return issue;
    }

    void setItemCell(int row, int col, String value) {
        items.setValueAt(value, row, col);
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    private static String num(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String show(BigDecimal v) {
        return v == null ? "–" : GermanFormats.formatAmount(v) + " €";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class ItemTableModel extends AbstractTableModel {
        private final List<String[]> rows = new ArrayList<>();

        void addRow(String[] row) {
            rows.add(row);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void clear() {
            int n = rows.size();
            if (n > 0) {
                rows.clear();
                fireTableRowsDeleted(0, n - 1);
            }
        }

        void removeRow(int index) {
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        String get(int row, int col) {
            String v = rows.get(row)[col];
            return v == null ? "" : v;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int col) {
            return get(row, col);
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            rows.get(row)[col] = value == null ? "" : value.toString();
            fireTableCellUpdated(row, col);
        }
    }
}
