// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.service.ArchiveService;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.swing.JDialog;

public final class ArchiveDialog extends JDialog {
    public ArchiveDialog(Window owner, ArchiveService service) {
        super(owner, "Archiv", ModalityType.MODELESS);
        setLayout(new BorderLayout());
        add(new ArchivePanel(service, p -> {
            try {
                if (!Desktop.isDesktopSupported()) {
                    throw new UnsupportedOperationException("Dieses System kann Dateien nicht mit dem Standardprogramm öffnen.");
                }
                Desktop.getDesktop().open(p.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, this), BorderLayout.CENTER);
        setPreferredSize(new Dimension(1100, 620));
        pack();
        setLocationRelativeTo(owner);
    }
}
