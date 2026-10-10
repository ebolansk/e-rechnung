// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.archive.Confirmation;
import io.github.ebolansk.erechnung.extract.ExtractionResult;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.model.InvoiceData;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import javax.swing.JDialog;

public final class ReviewDialog extends JDialog {
    private final ReviewPanel panel;

    public ReviewDialog(Window owner, byte[] pdf, ExtractionResult extraction, Mandant mandant) {
        this(owner, pdf, extraction, mandant, null);
    }

    public ReviewDialog(Window owner, byte[] pdf, ExtractionResult extraction, Mandant mandant, AiHelp ai) {
        this(owner, pdf, extraction, mandant, OutputFormat.XRECHNUNG, ai);
    }

    public ReviewDialog(Window owner, byte[] pdf, ExtractionResult extraction, Mandant mandant, OutputFormat format, AiHelp ai) {
        super(owner, "Rechnung prüfen und bestätigen", ModalityType.APPLICATION_MODAL);
        this.panel = new ReviewPanel(pdf, extraction, mandant, format, ai, this::dispose, this::dispose);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        setPreferredSize(new Dimension(1200, 800));
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                panel.close();
            }
        });
    }

    public Confirmation confirmation() {
        return panel.confirmation();
    }

    public Optional<InvoiceData> showAndGet() {
        setVisible(true);
        return panel.result();
    }

    /** Die Stammdaten des Ausstellers aus dem Tab „Verkäufer“ (können vom Mandanten abweichen). */
    public Mandant mandant() {
        return panel.mandant();
    }

    public boolean mandantChanged() {
        return panel.mandantChanged();
    }
}
