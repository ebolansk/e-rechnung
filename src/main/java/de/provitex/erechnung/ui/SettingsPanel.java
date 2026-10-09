// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.archive.ArchiveIndex;
import de.provitex.erechnung.archive.ArchiveLayout;
import de.provitex.erechnung.config.AppConfig;
import de.provitex.erechnung.config.ConfigStore;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.mandant.MandantStore;
import de.provitex.erechnung.model.OutputFormat;
import de.provitex.erechnung.util.GermanFormats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Konfiguration: Allgemein, Mandanten, Archiv. */
public final class SettingsPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final Path home;
    private final ConfigStore configStore;
    private final MandantStore mandantStore;
    private final Component dialogParent;

    private final JTextField archiveRoot = new JTextField(36);
    private final JTextField template = new JTextField(36);
    private final JLabel preview = new JLabel(" ");
    private final JRadioButton xr = new JRadioButton("XRechnung (reines XML)");
    private final JRadioButton zf = new JRadioButton("ZUGFeRD (PDF/A-3 mit eingebettetem XML, Profil EN 16931)");
    private final JTextField cutoff = new JTextField(10);
    private final JLabel saved = new JLabel(" ");
    private final DefaultListModel<Mandant> mandantModel = new DefaultListModel<>();
    private final JLabel archiveInfo = new JLabel(" ");

    public SettingsPanel(Path home, ConfigStore configStore, MandantStore mandantStore, Component dialogParent) {
        super(new BorderLayout());
        this.home = home;
        this.configStore = configStore;
        this.mandantStore = mandantStore;
        this.dialogParent = dialogParent;
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Allgemein", scroll(general()));
        tabs.addTab("Mandanten", scroll(mandanten()));
        tabs.addTab("Archiv", scroll(archive()));
        add(tabs, BorderLayout.CENTER);
        load();
    }

    /** Fügt einen weiteren Konfigurations-Tab an (KI, Update). */
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
        return new Ui.Form()
            .section("Archiv")
            .row("Archivordner", rootRow)
            .row("Ordnervorlage", template)
            .wide(new JLabel("<html><div style='width:420px'>Platzhalter: {Mandant}, {Jahr}, {Monat}, {Rechnungsnummer}. "
                + "Mit <b>/</b> trennen Sie Ordnerebenen. {Rechnungsnummer} ist Pflicht.</div></html>"))
            .row("Beispiel", preview)
            .section("Ausgabeformat")
            .wide(radios)
            .section("Stichtag")
            .row("E-Rechnung ist Original ab", cutoff)
            .wide(new JLabel("<html><div style='width:420px'>Ab diesem Datum erhält der Kunde nur noch die E-Rechnung "
                + "(Beleg-Status). Format TT.MM.JJJJ.</div></html>"))
            .wide(savePanel)
            .build();
    }

    private JPanel mandanten() {
        JList<Mandant> list = new JList<>(mandantModel);
        list.setCellRenderer((l, m, i, sel, focus) -> {
            JLabel c = new JLabel(m.name() + "  (" + (m.vatId().isBlank() ? "St.-Nr. " + m.taxNumber() : m.vatId()) + ")");
            c.setOpaque(true);
            c.setBackground(sel ? l.getSelectionBackground() : l.getBackground());
            c.setForeground(sel ? l.getSelectionForeground() : l.getForeground());
            return c;
        });
        JButton add = new JButton("Anlegen…");
        JButton edit = new JButton("Bearbeiten…");
        JButton remove = new JButton("Entfernen…");
        add.addActionListener(e -> {
            var draft = new de.provitex.erechnung.extract.MandantDraft("", "", "", "", "", "", "", "", "", "");
            new MandantDialog(owner(), draft).showAndGet().ifPresent(m -> {
                try {
                    mandantStore.add(m);
                    reloadMandanten();
                } catch (IOException ex) {
                    error(ex);
                }
            });
        });
        edit.addActionListener(e -> {
            Mandant sel = list.getSelectedValue();
            if (sel != null) {
                new MandantDialog(owner(), sel).showAndGet().ifPresent(m -> {
                    try {
                        mandantStore.update(m);
                        reloadMandanten();
                    } catch (IOException ex) {
                        error(ex);
                    }
                });
            }
        });
        remove.addActionListener(e -> {
            Mandant sel = list.getSelectedValue();
            if (sel != null && JOptionPane.showConfirmDialog(this,
                "Mandant „" + sel.name() + "“ entfernen?\nArchivierte Rechnungen bleiben unverändert erhalten.",
                "Mandant entfernen", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    mandantStore.remove(sel.id());
                    reloadMandanten();
                } catch (IOException ex) {
                    error(ex);
                }
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(edit);
        buttons.add(remove);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel("Rechnungsaussteller. Neue Aussteller werden beim Verarbeiten erkannt und nach Bestätigung angelegt."),
            BorderLayout.NORTH);
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private JPanel archive() {
        JButton refresh = new JButton("Aktualisieren");
        refresh.addActionListener(e -> updateArchiveInfo());
        return new Ui.Form()
            .section("Archiv")
            .wide(archiveInfo)
            .wide(new JLabel("<html><div style='width:420px'>Suche und Integritätsprüfung stehen im Menü „Archiv“ des Hauptfensters. "
                + "Die Integrität wird außerdem bei jedem Start geprüft.</div></html>"))
            .wide(refresh)
            .build();
    }

    private void load() {
        try {
            AppConfig c = configStore.load(home);
            archiveRoot.setText(c.archiveRoot());
            template.setText(c.folderTemplate());
            (c.outputFormat() == OutputFormat.ZUGFERD ? zf : xr).setSelected(true);
            cutoff.setText(DATE.format(c.cutoffDate()));
        } catch (IOException e) {
            error(e);
        }
        reloadMandanten();
        updatePreview();
        updateArchiveInfo();
    }

    private void reloadMandanten() {
        mandantModel.clear();
        mandantStore.all().forEach(mandantModel::addElement);
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

    private void updateArchiveInfo() {
        try {
            int n = ArchiveIndex.scan(Path.of(archiveRoot.getText())).size();
            archiveInfo.setText("<html>Archivordner: " + archiveRoot.getText() + "<br>Archivierte Rechnungen: " + n + "</html>");
        } catch (IOException | RuntimeException e) {
            archiveInfo.setText("Der Archivordner kann nicht gelesen werden: " + e.getMessage());
        }
    }

    /** Speichert die Einstellungen. Bei ungültigen Eingaben erscheint eine Meldung. */
    public boolean save() {
        try {
            LocalDate d = GermanFormats.parseDate(cutoff.getText().trim());
            configStore.save(new AppConfig(archiveRoot.getText().trim(), template.getText().trim(),
                zf.isSelected() ? OutputFormat.ZUGFERD : OutputFormat.XRECHNUNG, d));
            saved.setForeground(new Color(10, 107, 42));
            saved.setText("Gespeichert.");
            updateArchiveInfo();
            return true;
        } catch (IllegalArgumentException | IOException e) {
            saved.setForeground(new Color(176, 0, 32));
            saved.setText(e.getMessage());
            return false;
        }
    }

    private Window owner() {
        return SwingUtilities.getWindowAncestor(dialogParent != null ? dialogParent : this);
    }

    private void error(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
