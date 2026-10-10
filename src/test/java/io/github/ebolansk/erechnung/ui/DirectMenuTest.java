// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.Test;

class DirectMenuTest {
    @Test
    void clickingTheMenuRunsTheActionDirectlyAndHasNoSubmenu() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        JMenu menu = DirectMenu.of("Mandanten", MenuIcons.of(MenuIcons.Kind.PEOPLE), runs::incrementAndGet);
        assertThat(menu.getMenuComponentCount()).isZero();
        assertThat(menu.getText()).isEqualTo("Mandanten");
        assertThat(menu.getIcon()).isNotNull();
        for (var l : menu.getMenuListeners()) {
            l.menuSelected(new MenuEvent(menu));
        }
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(runs).hasValue(1);
    }
}
