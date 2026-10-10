// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/** Ein Hauptmenü ohne Untermenü: Der Klick auf den Menünamen führt die Aktion direkt aus (zum Beispiel einen Dialog öffnen). */
final class DirectMenu {
    private DirectMenu() {
    }

    static JMenu of(String text, Icon icon, Runnable action) {
        JMenu menu = new JMenu(text);
        menu.setIcon(icon);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                // Erst die Menüauswahl löschen (sonst bleibt der Menüname hervorgehoben), dann die Aktion starten.
                SwingUtilities.invokeLater(() -> {
                    MenuSelectionManager.defaultManager().clearSelectedPath();
                    action.run();
                });
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return menu;
    }
}
