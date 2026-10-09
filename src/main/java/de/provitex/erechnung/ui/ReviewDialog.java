// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.archive.Confirmation;
import de.provitex.erechnung.extract.ExtractionResult;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.model.InvoiceData;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.util.Optional;
import javax.swing.JDialog;

public final class ReviewDialog extends JDialog {
    private final ReviewPanel panel;

    public ReviewDialog(Window owner, byte[] pdf, ExtractionResult extraction, Mandant mandant, LocalDate cutoff) {
        this(owner, pdf, extraction, mandant, cutoff, null);
    }

    public ReviewDialog(Window owner, byte[] pdf, ExtractionResult extraction, Mandant mandant, LocalDate cutoff, AiHelp ai) {
        super(owner, "Rechnung prüfen und bestätigen", ModalityType.APPLICATION_MODAL);
        this.panel = new ReviewPanel(pdf, extraction, mandant, cutoff, ai, this::dispose, this::dispose);
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
}
