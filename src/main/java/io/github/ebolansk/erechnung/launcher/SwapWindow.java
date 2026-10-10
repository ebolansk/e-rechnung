// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Kleines Fenster mit Ladebalken, solange der Launcher eine neue Version einspielt (oder zurückstellt). Nur JDK-Klassen, weil der
 * Launcher in einer eigenen JVM ohne die Programmbibliotheken läuft. Es darf den Tausch nie verhindern: Ohne Anzeige oder bei jedem
 * Fehler gibt open() null zurück, und der Tausch läuft still.
 */
final class SwapWindow implements Swap.Progress {
    /** Mindestens so lange bleibt das Fenster stehen, damit der Text lesbar ist, auch wenn der Tausch Sekundenbruchteile dauert. */
    private static final long MIN_VISIBLE_MS = 1500;

    private final JWindow window = new JWindow();
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel text = new JLabel(" ", SwingConstants.CENTER);
    private final long shownAt = System.currentTimeMillis();

    static SwapWindow open(String title) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        try {
            SwapWindow[] holder = new SwapWindow[1];
            SwingUtilities.invokeAndWait(() -> holder[0] = new SwapWindow(title));
            return holder[0];
        } catch (Exception | Error e) {
            return null;
        }
    }

    private SwapWindow(String title) {
        JLabel head = new JLabel("E-Rechnung-Tool", SwingConstants.CENTER);
        head.setFont(head.getFont().deriveFont(Font.BOLD, 20f));
        JLabel what = new JLabel(title, SwingConstants.CENTER);
        what.setFont(what.getFont().deriveFont(Font.PLAIN, 14f));
        bar.setStringPainted(true);
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(what, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        bottom.add(bar, BorderLayout.NORTH);
        bottom.add(text, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(120, 130, 150)),
            BorderFactory.createEmptyBorder(22, 28, 22, 28)));
        content.add(top, BorderLayout.NORTH);
        content.add(bottom, BorderLayout.SOUTH);
        window.setContentPane(content);
        window.setSize(440, 190);
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
        window.setVisible(true);
    }

    @Override
    public void step(int percent, String message) {
        SwingUtilities.invokeLater(() -> {
            bar.setValue(percent);
            text.setText(message);
        });
    }

    /** Wartet bei Bedarf bis zur Mindestanzeigedauer und schließt das Fenster. */
    void close() {
        try {
            long left = MIN_VISIBLE_MS - (System.currentTimeMillis() - shownAt);
            if (left > 0) {
                Thread.sleep(left);
            }
            SwingUtilities.invokeAndWait(window::dispose);
        } catch (Exception ignored) {
            // das Fenster ist reine Anzeige
        }
    }
}
