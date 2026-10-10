// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.TransferHandler;

/** Nimmt abgelegte Dateien an (einzeln oder mehrere). Wird auf Fenster, Tabelle und Drop-Zone gesetzt, damit das ganze Fenster Ablageziel ist. */
final class FileDropHandler extends TransferHandler {
    private final Consumer<List<File>> onFiles;

    FileDropHandler(Consumer<List<File>> onFiles) {
        this.onFiles = onFiles;
    }

    @Override
    public boolean canImport(TransferSupport s) {
        return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport s) {
        if (!canImport(s)) {
            return false;
        }
        try {
            onFiles.accept((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
