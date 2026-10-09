// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.ai.AiProvider;
import de.provitex.erechnung.ai.AiSettings;
import de.provitex.erechnung.ai.AiSettingsStore;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Konfigurations-Tab „KI“: Standard ist Aus. */
public final class AiSettingsPanel extends JPanel {
    private final AiSettingsStore store;
    private final JComboBox<AiProvider> provider = new JComboBox<>(AiProvider.values());
    private final JTextField url = new JTextField(36);
    private final JTextField model = new JTextField(36);
    private final JPasswordField key = new JPasswordField(36);
    private final JCheckBox saveKey = new JCheckBox("API-Key in daten/ki.json speichern (Klartext)");
    private final JLabel status = new JLabel(" ");

    public AiSettingsPanel(AiSettingsStore store) {
        super(new java.awt.BorderLayout());
        this.store = store;
        JButton save = new JButton("Speichern");
        save.addActionListener(e -> save());
        JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        savePanel.add(save);
        savePanel.add(status);
        add(new Ui.Form()
            .section("KI-Helfer")
            .wide(new JLabel("<html><div style='width:460px'>Die KI ist ein Helfer für Rechnungen, die sich mit den Regeln nicht sauber "
                + "einlesen lassen. Sie wird <b>nur nach Ihrer ausdrücklichen Bestätigung</b> pro Rechnung angesprochen und sendet "
                + "dabei den Text dieser einen Rechnung (nicht die PDF-Datei). Ergebnisse sind Vorschläge, die Sie feldweise übernehmen. "
                + "Aus: es wird nie etwas gesendet.</div></html>"))
            .row("Anbieter", provider)
            .row("Adresse", url)
            .wide(new JLabel("<html><div style='width:460px'>Leer lassen für den Standard: Claude API "
                + AiSettings.CLAUDE_URL + ". Bei „OpenAI-kompatibel“ die Basisadresse (z. B. " + AiSettings.OPENAI_URL
                + " oder ein eigener Server); „/chat/completions“ wird ergänzt.</div></html>"))
            .row("Modell", model)
            .row("API-Key", key)
            .wide(saveKey)
            .wide(new JLabel("<html><div style='width:460px'>Ohne Häkchen bleibt der Key nur bis zum Schließen des Programms im "
                + "Arbeitsspeicher und muss danach neu eingegeben werden. Gespeichert liegt er unverschlüsselt im Programmordner.</div></html>"))
            .wide(savePanel)
            .build(), java.awt.BorderLayout.CENTER);
        AiSettings s = store.load();
        provider.setSelectedItem(s.provider());
        url.setText(s.url());
        model.setText(s.model());
        key.setText(s.apiKey());
        saveKey.setSelected(s.saveKey());
    }

    private void save() {
        AiSettings s = new AiSettings((AiProvider) provider.getSelectedItem(), url.getText(), model.getText(),
            new String(key.getPassword()), saveKey.isSelected());
        try {
            store.save(s);
            status.setForeground(new Color(10, 107, 42));
            if (s.provider() == AiProvider.OFF) {
                status.setText("Gespeichert. Die KI ist aus.");
            } else if (s.usable()) {
                status.setText("Gespeichert. Die KI steht in der Prüfmaske als „Mit KI nachbessern“ bereit.");
            } else {
                status.setForeground(new Color(176, 0, 32));
                status.setText("Gespeichert, aber unvollständig (Modell und bei Claude der Key sind nötig).");
            }
        } catch (IOException e) {
            status.setForeground(new Color(176, 0, 32));
            status.setText(e.getMessage());
        }
    }
}
