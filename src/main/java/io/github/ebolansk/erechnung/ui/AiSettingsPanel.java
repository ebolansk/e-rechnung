// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.ai.AiProvider;
import io.github.ebolansk.erechnung.ai.AiSettings;
import io.github.ebolansk.erechnung.ai.AiSettingsStore;
import io.github.ebolansk.erechnung.ai.ModelLister;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/** Konfigurations-Tab „KI“: Standard ist Aus. */
public final class AiSettingsPanel extends JPanel {
    private final AiSettingsStore store;
    private final JComboBox<AiProvider> provider = new JComboBox<>(AiProvider.values());
    private final JTextField url = new JTextField(36);
    private final JComboBox<String> model = new JComboBox<>();
    private final JButton loadModels = new JButton("Modelle laden");
    private final JLabel modelStatus = new JLabel(" ");
    private int modelRequest;
    private final JPasswordField key = new JPasswordField(36);
    private final JCheckBox saveKey = new JCheckBox("API-Key verschlüsselt speichern (Windows-Benutzerkonto)");
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
            .row("Anbieter", provider)
            .row("Adresse", url)
            .row("API-Key", key)
            .row("Modell", modelRow())
            .wide(modelStatus)
            .wide(saveKey)
            .wide(savePanel)
            .build(), java.awt.BorderLayout.CENTER);
        AiSettings s = store.load();
        if (!store.loadWarning().isEmpty()) {
            status.setForeground(new Color(176, 0, 32));
            status.setText("<html>" + store.loadWarning().replace("&", "&amp;").replace("<", "&lt;") + "</html>");
        }
        key.setText(s.apiKey());
        saveKey.setSelected(s.saveKey());
        if (!store.canEncrypt()) {
            saveKey.setSelected(false);
            saveKey.setToolTipText("Das verschlüsselte Speichern gibt es nur unter Windows; der Key bleibt hier nur bis zum Schließen im Arbeitsspeicher.");
        }
        model.setEditable(true);
        model.setSelectedItem(s.model());
        provider.setSelectedItem(s.provider());
        applyProvider(s.url());
        provider.addActionListener(e -> applyProvider(""));
        loadModels.addActionListener(e -> loadModels());
    }

    private JPanel modelRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(model, BorderLayout.CENTER);
        row.add(loadModels, BorderLayout.EAST);
        return row;
    }

    /** Claude: feste Adresse, nicht änderbar. OpenAI-kompatibel: Adresse eingeben (Standard vorbelegt). Aus: alles gesperrt. */
    private void applyProvider(String storedUrl) {
        AiProvider p = (AiProvider) provider.getSelectedItem();
        boolean off = p == AiProvider.OFF;
        if (p == AiProvider.CLAUDE) {
            url.setText(AiSettings.CLAUDE_URL);
            url.setEditable(false);
        } else if (p == AiProvider.OPENAI) {
            String shown = storedUrl.isBlank() || storedUrl.equals(AiSettings.CLAUDE_URL) ? AiSettings.OPENAI_URL : storedUrl;
            url.setText(url.getText().isBlank() || url.getText().equals(AiSettings.CLAUDE_URL) ? shown : url.getText());
            url.setEditable(true);
        } else {
            url.setText("");
            url.setEditable(false);
        }
        url.setEnabled(!off);
        key.setEnabled(!off);
        model.setEnabled(!off);
        loadModels.setEnabled(!off);
        saveKey.setEnabled(!off && store.canEncrypt());
        modelStatus.setText(" ");
    }

    /** Fragt auf Klick „Modelle laden“ im Hintergrund die Modelle ab (nie automatisch); Fehler erscheinen nur als Hinweis. */
    private void loadModels() {
        AiProvider p = (AiProvider) provider.getSelectedItem();
        if (p == AiProvider.OFF) {
            return;
        }
        if (p == AiProvider.CLAUDE && key.getPassword().length == 0) {
            modelStatus.setForeground(new Color(176, 0, 32));
            modelStatus.setText("Bitte zuerst den API-Key eintragen.");
            return;
        }
        AiSettings probe = new AiSettings(p, p == AiProvider.CLAUDE ? "" : url.getText(), "", new String(key.getPassword()), false);
        if (probe.endpoint().isBlank()) {
            modelStatus.setForeground(new Color(176, 0, 32));
            modelStatus.setText("Bitte zuerst die Adresse eintragen.");
            return;
        }
        int request = ++modelRequest;
        modelStatus.setForeground(Color.DARK_GRAY);
        modelStatus.setText("Modelle werden abgefragt …");
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return new ModelLister().list(probe);
            }

            @Override
            protected void done() {
                if (request != modelRequest) {
                    return;
                }
                try {
                    List<String> ids = get();
                    String current = currentModel();
                    model.removeAllItems();
                    ids.forEach(model::addItem);
                    model.setSelectedItem(current.isBlank() ? ids.get(0) : current);
                    modelStatus.setForeground(new Color(10, 107, 42));
                    modelStatus.setText(ids.size() + " Modelle gefunden.");
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    modelStatus.setForeground(new Color(176, 0, 32));
                    modelStatus.setText("Modelle konnten nicht abgefragt werden: " + cause.getMessage() + " Das Modell lässt sich auch von Hand eintragen.");
                }
            }
        }.execute();
    }

    private String currentModel() {
        Object item = model.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    JButton loadModelsButton() {
        return loadModels;
    }

    JComboBox<AiProvider> providerBox() {
        return provider;
    }

    JTextField urlField() {
        return url;
    }

    private void save() {
        AiProvider p = (AiProvider) provider.getSelectedItem();
        AiSettings s = new AiSettings(p, p == AiProvider.OPENAI ? url.getText() : "", currentModel(),
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
