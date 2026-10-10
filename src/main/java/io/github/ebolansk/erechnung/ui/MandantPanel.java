// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.extract.MandantDraft;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.util.GermanFormats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formular für Stammdaten eines Rechnungsausstellers (Mandant). */
public final class MandantPanel extends JPanel {
    private final String id;
    private final JTextField name = new JTextField(28);
    private final JTextField street = new JTextField();
    private final JTextField zip = new JTextField();
    private final JTextField city = new JTextField();
    private final JTextField country = new JTextField("DE");
    private final JTextField vatId = new JTextField();
    private final JTextField taxNumber = new JTextField();
    private final JTextField email = new JTextField();
    private final JTextField contactName = new JTextField();
    private final JTextField contactPhone = new JTextField();
    private final JTextField iban = new JTextField();
    private final JTextField bic = new JTextField();
    private final JTextField paymentTerms = new JTextField();
    private final JLabel errors = new JLabel(" ");
    private String errorText = "";
    private final JButton aiButton = new JButton("Mit KI aus einer Rechnung ausfüllen …");
    private MandantAi ai;

    /** Neuer Aussteller, vorbelegt aus dem PDF-Text; mit KI-Hilfe, wenn ai gesetzt ist. */
    MandantPanel(MandantDraft d, MandantAi ai) {
        this(d);
        enableAi(ai);
    }

    /** Vorhandener Aussteller, mit KI-Hilfe, wenn ai gesetzt ist. */
    MandantPanel(Mandant m, MandantAi ai) {
        this(m);
        enableAi(ai);
    }

    public MandantPanel(MandantDraft d) {
        this("", d.name(), d.street(), d.zip(), d.city(), "DE", d.vatId(), d.taxNumber(), d.email(), "", d.phone(),
            d.iban(), d.bic(), "");
    }

    public MandantPanel(Mandant m) {
        this(m.id(), m.name(), m.street(), m.zip(), m.city(), m.country(), m.vatId(), m.taxNumber(), m.email(),
            m.contactName(), m.contactPhone(), m.iban(), m.bic(), m.paymentTerms());
    }

    private MandantPanel(String id, String n, String st, String z, String c, String co, String vat, String tax,
                         String mail, String cn, String cp, String ib, String bi, String pt) {
        super(new BorderLayout());
        this.id = id == null ? "" : id;
        name.setText(n);
        street.setText(st);
        zip.setText(z);
        city.setText(c);
        country.setText(co == null || co.isBlank() ? "DE" : co);
        vatId.setText(vat);
        taxNumber.setText(tax);
        email.setText(mail);
        contactName.setText(cn);
        contactPhone.setText(cp);
        iban.setText(ib);
        bic.setText(bi);
        paymentTerms.setText(pt);
        errors.setForeground(new Color(176, 0, 32));
        JPanel form = new Ui.Form()
            .section("Rechnungsaussteller")
            .row("Name *", name).row("Straße *", street).row("PLZ *", zip).row("Ort *", city).row("Land (ISO) *", country)
            .section("Steuerliche Kennung (eine genügt)")
            .row("USt-IdNr.", vatId).row("Steuernummer", taxNumber)
            .section("Kontakt und Zahlung")
            .row("E-Mail * (BT-34)", email).row("Ansprechpartner (XRechnung: Pflicht)", contactName).row("Telefon (XRechnung: Pflicht)", contactPhone)
            .row("IBAN *", iban).row("BIC", bic).row("Zahlungsbedingungen", paymentTerms)
            .wide(errors)
            .build();
        add(form, BorderLayout.CENTER);
    }

    /** Stellt die KI-Hilfe bereit (Knopf unten im Formular); ohne Einrichtung der KI bleibt der Knopf gesperrt. */
    private void enableAi(MandantAi ai) {
        this.ai = ai;
        if (ai == null) {
            return;
        }
        boolean usable = ai.service().available();
        aiButton.setEnabled(usable);
        aiButton.setToolTipText(usable
            ? "Liest die Stammdaten aus einer Rechnung dieses Ausstellers und schlägt sie feldweise vor (nach Ihrer Bestätigung)."
            : "Die KI ist nicht eingerichtet (Konfiguration → KI).");
        aiButton.addActionListener(e -> askAi());
        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        south.add(aiButton);
        add(south, BorderLayout.SOUTH);
    }

    private void askAi() {
        String text = ai.text();
        String file = ai.fileName();
        if (text == null) {
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            fc.setDialogTitle("Rechnung dieses Ausstellers wählen");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF-Dateien", "pdf"));
            if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                byte[] pdf = java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath());
                text = io.github.ebolansk.erechnung.extract.PdfText.extract(pdf).text();
                file = fc.getSelectedFile().getName();
            } catch (java.io.IOException | RuntimeException ex) {
                javax.swing.JOptionPane.showMessageDialog(this, "Die PDF kann nicht gelesen werden:\n" + ex.getMessage(), "Fehler",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        String invoiceText = text;
        String fileName = file;
        AiFlow.start(this, ai.service(), invoiceText, "dieser einen Rechnung (zum Lesen des Rechnungsausstellers)",
            cancel -> ai.service().askSeller(invoiceText, fileName, cancel), this::showSuggestion, busy -> {
                aiButton.setEnabled(!busy);
                aiButton.setText(busy ? "KI arbeitet …" : "Mit KI aus einer Rechnung ausfüllen …");
            });
    }

    private static final String[][] LABELS = {{"name", "Name"}, {"street", "Straße"}, {"zip", "PLZ"}, {"city", "Ort"},
        {"country", "Land (ISO)"}, {"vatId", "USt-IdNr."}, {"taxNumber", "Steuernummer"}, {"email", "E-Mail"},
        {"contactName", "Ansprechpartner"}, {"phone", "Telefon"}, {"iban", "IBAN"}, {"bic", "BIC"}};

    private void showSuggestion(io.github.ebolansk.erechnung.ai.SellerSuggestion s) {
        List<AiSuggestionDialog.Row> rows = new ArrayList<>();
        for (String[] l : LABELS) {
            String proposed = s.fields().get(l[0]);
            String current = value(l[0]);
            if (proposed != null && !proposed.equals(current)) {
                rows.add(new AiSuggestionDialog.Row(l[0], l[1], current, proposed, s.warnings().getOrDefault(l[0], "")));
            }
        }
        if (rows.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Die KI bestätigt die vorhandenen Angaben, es gibt nichts zu übernehmen.", "KI",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<AiSuggestionDialog.Row> chosen = new AiSuggestionDialog(javax.swing.SwingUtilities.getWindowAncestor(this), rows, s.notes()).showAndGet();
        applyValues(chosen.stream().collect(java.util.stream.Collectors.toMap(AiSuggestionDialog.Row::key, AiSuggestionDialog.Row::aiValue)));
    }

    private JTextField fieldOf(String key) {
        return switch (key) {
            case "name" -> name;
            case "street" -> street;
            case "zip" -> zip;
            case "city" -> city;
            case "country" -> country;
            case "vatId" -> vatId;
            case "taxNumber" -> taxNumber;
            case "email" -> email;
            case "contactName" -> contactName;
            case "phone" -> contactPhone;
            case "iban" -> iban;
            case "bic" -> bic;
            default -> null;
        };
    }

    String value(String key) {
        JTextField f = fieldOf(key);
        return f == null ? "" : f.getText().trim();
    }

    /** Trägt die Werte in die Felder ein (Schlüssel wie im KI-Vorschlag: name, street, zip, city, country, vatId, taxNumber, email, contactName, phone, iban, bic). */
    void applyValues(java.util.Map<String, String> values) {
        values.forEach((k, v) -> {
            JTextField f = fieldOf(k);
            if (f != null) {
                f.setText(v);
            }
        });
    }

    /** Die aktuellen Eingaben als Mandant, ohne Pflichtangaben zu prüfen (die prüft die Prüfmaske beim Erzeugen). */
    Mandant snapshot() {
        return new Mandant(id, name.getText().trim(), street.getText().trim(), zip.getText().trim(), city.getText().trim(),
            country.getText().trim().toUpperCase(), vatId.getText().replaceAll("\\s+", ""), taxNumber.getText().trim(),
            email.getText().trim(), contactName.getText().trim(), contactPhone.getText().trim(),
            GermanFormats.normalizeIban(iban.getText()), bic.getText().replaceAll("\\s+", ""), paymentTerms.getText().trim());
    }

    /** Ruft r bei jeder Änderung eines Feldes auf. */
    void onChange(Runnable r) {
        for (JTextField f : List.of(name, street, zip, city, country, vatId, taxNumber, email, contactName, contactPhone, iban, bic,
            paymentTerms)) {
            Ui.onChange(f, r);
        }
    }

    public JTextField contactNameField() {
        return contactName;
    }

    public String errorText() {
        return errorText;
    }

    /** Prüft die Eingaben. Bei Fehlern wird die Meldung angezeigt und ein leeres Optional geliefert. */
    public Optional<Mandant> collect() {
        List<String> e = new ArrayList<>();
        if (blank(name)) {
            e.add("Name fehlt.");
        }
        if (blank(street) || blank(zip) || blank(city) || blank(country)) {
            e.add("Anschrift ist unvollständig.");
        }
        if (blank(vatId) && blank(taxNumber)) {
            e.add("USt-IdNr. oder Steuernummer fehlt.");
        }
        if (blank(email) || !email.getText().contains("@")) {
            e.add("Gültige E-Mail-Adresse fehlt.");
        }
        if (!GermanFormats.isValidIban(iban.getText())) {
            e.add("IBAN fehlt oder hat eine falsche Prüfsumme.");
        }
        errorText = String.join("\n", e);
        errors.setText(e.isEmpty() ? " " : "<html>" + String.join("<br>", e) + "</html>");
        if (!e.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Mandant(id, name.getText().trim(), street.getText().trim(), zip.getText().trim(),
            city.getText().trim(), country.getText().trim().toUpperCase(), vatId.getText().replaceAll("\\s+", ""),
            taxNumber.getText().trim(), email.getText().trim(), contactName.getText().trim(),
            contactPhone.getText().trim(), GermanFormats.normalizeIban(iban.getText()), bic.getText().replaceAll("\\s+", ""),
            paymentTerms.getText().trim()));
    }

    private static boolean blank(JTextField f) {
        return f.getText() == null || f.getText().isBlank();
    }
}
