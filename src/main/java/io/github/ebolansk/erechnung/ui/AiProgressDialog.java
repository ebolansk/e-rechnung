// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.ai.AiCancel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;

/**
 * Fenster „KI arbeitet“: zeigt die Laufzeit, damit man sieht, dass es läuft, und bietet Abbrechen. Es ist nicht modal; die
 * Maske dahinter bleibt bedienbar, der KI-Knopf dort ist solange gesperrt.
 */
final class AiProgressDialog extends JDialog {
    private final JLabel elapsed = new JLabel("Laufzeit: 0 s");
    private final Timer timer;
    private final long start = System.currentTimeMillis();

    /** detailsHtml: Anbieter, Modell und Adresse als HTML-Zeilen (siehe {@code AiFlow.details}). */
    AiProgressDialog(Window owner, String detailsHtml, AiCancel cancel) {
        super(owner, "KI arbeitet …", ModalityType.MODELESS);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        content.add(new JLabel("<html><div style='width:420px'><b>Die KI liest den Rechnungstext.</b><br><br>" + detailsHtml
            + "<br><br>Je nach Modell kann das eine Weile dauern.<br>Sie können jederzeit abbrechen.</div></html>"), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel middle = new JPanel(new BorderLayout(0, 4));
        middle.add(bar, BorderLayout.CENTER);
        middle.add(elapsed, BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);
        JButton abort = new JButton("Abbrechen");
        abort.addActionListener(e -> cancel.cancel());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(abort);
        content.add(buttons, BorderLayout.SOUTH);
        add(content);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel.cancel();
            }
        });
        timer = new Timer(500, e -> elapsed.setText("Laufzeit: " + (System.currentTimeMillis() - start) / 1000 + " s"));
        timer.start();
        pack();
        setLocationRelativeTo(owner);
    }

    void finish() {
        timer.stop();
        dispose();
    }
}
