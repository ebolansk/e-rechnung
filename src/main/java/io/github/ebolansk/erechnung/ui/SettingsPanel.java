// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.archive.ArchiveLayout;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.model.OutputFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

/** Konfiguration: Allgemein (mit dem Abschnitt „Updates“), dazu KI als weiterer Tab. */
public final class SettingsPanel extends JPanel {
    private final Path home;
    private final ConfigStore configStore;

    private final JTextField archiveRoot = new JTextField(36);
    private final JTextField template = new JTextField(36);
    private final JLabel preview = new JLabel(" ");
    private final JRadioButton xr = new JRadioButton("XRechnung (reines XML)");
    private final JRadioButton zf = new JRadioButton("ZUGFeRD (PDF/A-3 mit eingebettetem XML, Profil EN 16931)");
    private final JLabel saved = new JLabel(" ");

    private final JPanel updateRow;

    public SettingsPanel(Path home, ConfigStore configStore) {
        this(home, configStore, null);
    }

    /** updateRow (optional): die Einstellung „Beim Start nach Updates suchen“, erscheint in „Allgemein“. */
    public SettingsPanel(Path home, ConfigStore configStore, JPanel updateRow) {
        super(new BorderLayout());
        this.home = home;
        this.configStore = configStore;
        this.updateRow = updateRow;
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Allgemein", scroll(general()));
        add(tabs, BorderLayout.CENTER);
        load();
    }

    /** Fügt einen weiteren Konfigurations-Tab an (KI). */
    public void addTab(String title, JPanel content) {
        ((JTabbedPane) getComponent(0)).addTab(title, scroll(content));
    }

    private static JScrollPane scroll(JPanel content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JPanel general() {
        ButtonGroup g = new ButtonGroup();
        g.add(xr);
        g.add(zf);
        JButton browse = new JButton("Durchsuchen…");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(archiveRoot.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                archiveRoot.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        JPanel rootRow = new JPanel(new BorderLayout(6, 0));
        rootRow.add(archiveRoot, BorderLayout.CENTER);
        rootRow.add(browse, BorderLayout.EAST);
        JButton save = new JButton("Speichern");
        save.addActionListener(e -> save());
        JPanel radios = new JPanel(new java.awt.GridLayout(2, 1));
        radios.add(xr);
        radios.add(zf);
        Ui.onChange(template, this::updatePreview);
        Ui.onChange(archiveRoot, this::updatePreview);
        JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        savePanel.add(save);
        savePanel.add(saved);
        Ui.Form form = new Ui.Form();
        form.section("Ausgabeformat")
            .wide(radios)
            .section("Archiv")
            .row("Archivordner", rootRow)
            .row("Ordnervorlage", template)
            .wide(new JLabel("<html><div style='width:420px'>Platzhalter: {Mandant}, {Jahr}, {Monat}, {Rechnungsnummer}. "
                + "Mit <b>/</b> trennen Sie Ordnerebenen. {Rechnungsnummer} ist Pflicht.</div></html>"))
            .row("Beispiel", preview);
        if (updateRow != null) {
            form.section("Updates").wide(updateRow);
        }
        return form.wide(savePanel).build();
    }

    private void load() {
        try {
            AppConfig c = configStore.load(home);
            archiveRoot.setText(c.archiveRoot());
            template.setText(c.folderTemplate());
            (c.outputFormat() == OutputFormat.ZUGFERD ? zf : xr).setSelected(true);
        } catch (IOException e) {
            error(e);
        }
        updatePreview();
    }

    private void updatePreview() {
        try {
            Path p = ArchiveLayout.resolve(Path.of(archiveRoot.getText()), template.getText(), "Muster GmbH",
                LocalDate.of(2027, 1, 15), "RE-2027-0001");
            preview.setForeground(Color.DARK_GRAY);
            preview.setText(p.toString());
        } catch (RuntimeException e) {
            preview.setForeground(new Color(176, 0, 32));
            preview.setText(e.getMessage());
        }
    }

    /** Speichert die Einstellungen. Bei ungültigen Eingaben erscheint eine Meldung. */
    public boolean save() {
        try {
            LocalDate cutoffDate = configStore.load(home).cutoffDate();
            configStore.save(new AppConfig(archiveRoot.getText().trim(), template.getText().trim(),
                zf.isSelected() ? OutputFormat.ZUGFERD : OutputFormat.XRECHNUNG, cutoffDate));
            saved.setForeground(new Color(10, 107, 42));
            saved.setText("Gespeichert.");
            return true;
        } catch (IllegalArgumentException | IOException e) {
            saved.setForeground(new Color(176, 0, 32));
            saved.setText(e.getMessage());
            return false;
        }
    }

    private void error(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
