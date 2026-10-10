// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.ai.AiCancel;
import io.github.ebolansk.erechnung.ai.AiCancelledException;
import io.github.ebolansk.erechnung.service.AiAssistService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Gemeinsamer Ablauf jeder KI-Anfrage: Bestätigung mit dem Text, der gesendet wird, dann das Fortschrittsfenster (Laufzeit,
 * Live-Antwort, Abbrechen), dann das Ergebnis. Ohne ausdrückliche Bestätigung wird nichts gesendet.
 */
final class AiFlow {
    @FunctionalInterface
    interface Task<T> {
        T run(AiCancel cancel) throws IOException;
    }

    private AiFlow() {
    }

    /** Anbieter, Modell und Adresse in getrennten Zeilen (HTML, maskiert). */
    static String details(io.github.ebolansk.erechnung.ai.AiSettings s) {
        return "<b>Anbieter:</b> " + esc(s.provider().label()) + "<br><b>Modell:</b> " + esc(s.model()) + "<br><b>Adresse:</b> " + esc(s.endpoint());
    }

    private static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * @param what   kurze Beschreibung des Textes in der Bestätigung (zum Beispiel „dieser einen Rechnung“)
     * @param busy   wird mit true gerufen, sobald die Anfrage läuft, und mit false, wenn sie beendet ist (Knöpfe sperren)
     */
    static <T> void start(Component parent, AiAssistService service, String text, String what, Task<T> task, Consumer<T> onResult,
                          Consumer<Boolean> busy) {
        var settings = service.settings();
        JTextArea sent = new JTextArea(text, 18, 80);
        sent.setEditable(false);
        sent.setLineWrap(true);
        sent.setWrapStyleWord(true);
        sent.setCaretPosition(0);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.add(new JLabel("<html><div style='width:640px'>Der folgende Text " + what + " wird gesendet an:<br><br>"
            + details(settings) + "<br><br>Die PDF-Datei selbst wird nicht übertragen.<br>Ohne Ihre Bestätigung wird nichts gesendet.</div></html>"),
            BorderLayout.NORTH);
        box.add(new JScrollPane(sent), BorderLayout.CENTER);
        int choice = JOptionPane.showOptionDialog(parent, box, "Daten an die KI senden?", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, new Object[] {"Senden", "Abbrechen"}, "Abbrechen");
        if (choice != 0) {
            return;
        }
        busy.accept(true);
        AiCancel cancel = new AiCancel();
        AiProgressDialog progress = new AiProgressDialog(SwingUtilities.getWindowAncestor(parent), details(settings), cancel);
        progress.setVisible(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run(cancel);
            }

            @Override
            protected void done() {
                progress.finish();
                busy.accept(false);
                try {
                    onResult.accept(get());
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof AiCancelledException) {
                        return;
                    }
                    JOptionPane.showMessageDialog(parent, "Die KI ist nicht erreichbar oder hat nicht brauchbar geantwortet:\n"
                        + ex.getCause().getMessage() + "\n\nSie können mit den eingelesenen Werten oder manuell weiterarbeiten.",
                        "KI", JOptionPane.WARNING_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
