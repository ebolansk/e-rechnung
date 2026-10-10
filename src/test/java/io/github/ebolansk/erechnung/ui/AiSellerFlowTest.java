// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.ebolansk.erechnung.ai.AiProvider;
import io.github.ebolansk.erechnung.ai.AiSettings;
import io.github.ebolansk.erechnung.ai.AiSettingsStore;
import io.github.ebolansk.erechnung.ai.SecretProtector;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.extract.MandantDraft;
import io.github.ebolansk.erechnung.service.AiAssistService;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Der KI-Ablauf beim Anlegen eines Rechnungsausstellers im echten Fenster: Bestätigen, Fortschritt, Vorschlag übernehmen. */
class AiSellerFlowTest {
    private static JButton findButton(Component c, String text) {
        if (c instanceof JButton b && text.equals(b.getText())) {
            return b;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                JButton b = findButton(child, text);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    private static JButton awaitButtonInAnyWindow(String text) throws Exception {
        JButton[] found = new JButton[1];
        long end = System.currentTimeMillis() + 20_000;
        while (found[0] == null) {
            for (Window w : Window.getWindows()) {
                if (w.isVisible()) {
                    JButton b = findButton(w, text);
                    if (b != null && b.isShowing()) {
                        found[0] = b;
                    }
                }
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Zeitüberschreitung beim Warten auf den Knopf: " + text);
            }
            Thread.sleep(50);
        }
        return found[0];
    }

    private static void await(BooleanSupplier condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Zeitüberschreitung beim Warten auf: " + what);
            }
            Thread.sleep(50);
        }
    }

    @Test
    void aiFillsTheNewSellerAfterConfirmationAndOnlyChosenValuesAreApplied(@TempDir Path dir) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "braucht eine Anzeige");
        var store = new AiSettingsStore(dir.resolve("ki.json"), SecretProtector.UNAVAILABLE);
        store.save(new AiSettings(AiProvider.OPENAI, "http://127.0.0.1:1/v1", "testmodell", "", false));
        String[] sentText = new String[1];
        var service = new AiAssistService(store, new AuditLog(dir.resolve("p.jsonl"), Clock.systemUTC(), "t"), settings -> (system, user) -> {
            sentText[0] = user;
            return "{\"name\":\"Nordlicht Werbetechnik GmbH\",\"street\":\"Lindenweg 12\",\"zip\":\"70173\",\"city\":\"Stuttgart\","
                + "\"country\":\"DE\",\"vatId\":\"DE811234567\",\"email\":\"info@nordlicht.example\",\"phone\":\"+49 711 5550123\","
                + "\"contactName\":\"Erika Beispiel\",\"iban\":\"DE89370400440532013000\",\"bic\":\"COBADEFFXXX\"}";
        });
        var draft = new MandantDraft("", "", "", "", "", "", "", "", "", "");
        MandantPanel[] panel = new MandantPanel[1];
        JFrame[] frame = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new MandantPanel(draft, new MandantAi(service, "Nordlicht Werbetechnik GmbH Lindenweg 12 70173 Stuttgart", "rechnung.pdf"));
            frame[0] = new JFrame("Test");
            frame[0].add(panel[0]);
            frame[0].pack();
            frame[0].setVisible(true);
        });
        try {
            JButton ai = findButton(panel[0], "Mit KI aus einer Rechnung ausfüllen …");
            assertThat(ai.isEnabled()).isTrue();
            SwingUtilities.invokeLater(ai::doClick);

            // 1. Bestätigung: ohne „Senden“ geht nichts raus.
            assertThat(sentText[0]).isNull();
            SwingUtilities.invokeAndWait(() -> { });
            JButton send = awaitButtonInAnyWindow("Senden");
            assertThat(sentText[0]).as("vor der Bestätigung nichts gesendet").isNull();
            SwingUtilities.invokeAndWait(send::doClick);

            // 2. Vorschlag erscheint; Ansprechpartner abwählen, den Rest übernehmen.
            JButton apply = awaitButtonInAnyWindow("Ausgewählte übernehmen");
            assertThat(sentText[0]).contains("Nordlicht Werbetechnik GmbH");
            JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(apply);
            javax.swing.JTable table = findTable(dialog);
            int contactRow = -1;
            for (int r = 0; r < table.getRowCount(); r++) {
                if ("Ansprechpartner".equals(table.getValueAt(r, 1))) {
                    contactRow = r;
                }
            }
            assertThat(contactRow).isGreaterThanOrEqualTo(0);
            int row = contactRow;
            SwingUtilities.invokeAndWait(() -> table.setValueAt(false, row, 0));
            SwingUtilities.invokeAndWait(apply::doClick);

            await(() -> "Nordlicht Werbetechnik GmbH".equals(panel[0].value("name")), "übernommener Name");
            assertThat(panel[0].value("vatId")).isEqualTo("DE811234567");
            assertThat(panel[0].value("iban")).isEqualTo("DE89370400440532013000");
            assertThat(panel[0].value("contactName")).as("abgewählt: nicht übernommen").isEmpty();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    w.dispose();
                }
            });
        }
    }

    private static javax.swing.JTable findTable(Component c) {
        if (c instanceof javax.swing.JTable t) {
            return t;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                javax.swing.JTable t = findTable(child);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }
}
