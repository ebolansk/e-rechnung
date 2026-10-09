// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.extract.MandantDraft;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.util.GermanFormats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            .row("E-Mail * (BT-34)", email).row("Ansprechpartner *", contactName).row("Telefon *", contactPhone)
            .row("IBAN *", iban).row("BIC", bic).row("Zahlungsbedingungen", paymentTerms)
            .wide(errors)
            .build();
        add(form, BorderLayout.CENTER);
    }

    public JTextField contactNameField() {
        return contactName;
    }

    public JTextField contactPhoneField() {
        return contactPhone;
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
        if (blank(contactName)) {
            e.add("Ansprechpartner fehlt (BT-41, die XRechnung verlangt ihn).");
        }
        if (blank(contactPhone)) {
            e.add("Telefon des Ansprechpartners fehlt (BT-42, die XRechnung verlangt es).");
        }
        if (GermanFormats.normalizeIban(iban.getText()).length() < 15) {
            e.add("IBAN fehlt oder ist zu kurz.");
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
