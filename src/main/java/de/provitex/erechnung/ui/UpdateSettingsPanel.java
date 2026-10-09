// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.update.UpdateSettings;
import de.provitex.erechnung.update.UpdateSettingsStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Konfigurations-Tab „Update“: Repository (und optional ein Token) für „Hilfe → Nach Updates suchen“. */
public final class UpdateSettingsPanel extends JPanel {
    private final UpdateSettingsStore store;
    private final JTextField repo = new JTextField(36);
    private final JPasswordField token = new JPasswordField(36);
    private final JCheckBox onStart = new JCheckBox("Beim Start nach Updates suchen (höchstens einmal täglich)");
    private final JLabel status = new JLabel(" ");

    public UpdateSettingsPanel(UpdateSettingsStore store) {
        super(new BorderLayout());
        this.store = store;
        JButton save = new JButton("Speichern");
        save.addActionListener(e -> save());
        JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        savePanel.add(save);
        savePanel.add(status);
        add(new Ui.Form()
            .section("Updates über GitHub")
            .wide(new JLabel("<html><div style='width:460px'>Das Programm sucht <b>nur auf Ihren Klick</b> (Hilfe → Nach Updates suchen), auf Wunsch zusätzlich beim Start, "
                + "nach einer neuen Version und lädt sie erst nach Ihrer Bestätigung. Eingespielt wird beim nächsten Start; die bisherige "
                + "Version bleibt zum Zurückwechseln liegen. Archivierte Rechnungen werden nie verändert.</div></html>"))
            .wide(onStart)
            .wide(new JLabel("<html><div style='width:460px'>Standard: aus. Eingeschaltet fragt das Programm beim Start im Hintergrund bei GitHub "
                + "nach der neuesten Version (dabei sieht GitHub Ihre IP-Adresse) und weist nur darauf hin; installiert wird nichts "
                + "ohne Ihre Bestätigung.</div></html>"))
            .row("Repository", repo)
            .row("Zugriffs-Token (optional)", token)
            .wide(new JLabel("<html><div style='width:460px'>Das Standard-Repository ist öffentlich, dafür brauchen Sie <b>kein Token</b>. "
                + "Nur für ein privates Repository (zum Beispiel einen eigenen Fork): auf GitHub unter Settings → Developer settings → "
                + "Fine-grained tokens ein Token nur für dieses Repository mit dem Recht „Contents: Read-only“ erzeugen. "
                + "Das Token liegt unverschlüsselt in daten/update.json; wer den Ordner kopiert, kann das Repository lesen.</div></html>"))
            .wide(savePanel)
            .build(), BorderLayout.CENTER);
        UpdateSettings s = store.load();
        repo.setText(s.repo());
        token.setText(s.token());
        onStart.setSelected(s.checkOnStart());
    }

    private void save() {
        UpdateSettings s = new UpdateSettings(repo.getText(), new String(token.getPassword()), onStart.isSelected());
        try {
            store.save(s);
            boolean ok = s.usable();
            status.setForeground(ok ? new Color(10, 107, 42) : new Color(176, 0, 32));
            status.setText(ok ? "Gespeichert." : "Gespeichert, aber das Repository muss im Format Name/Repo angegeben werden.");
        } catch (IOException e) {
            status.setForeground(new Color(176, 0, 32));
            status.setText(e.getMessage());
        }
    }
}
