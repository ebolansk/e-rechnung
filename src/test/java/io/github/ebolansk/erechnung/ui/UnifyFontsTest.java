// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;

class UnifyFontsTest {
    @Test
    void allComponentFontsGetTheMenuFontSizeAndKeepTheirStyle() {
        Object oldLabel = UIManager.get("Label.font");
        Object oldTable = UIManager.get("Table.font");
        Object oldTitle = UIManager.get("TitledBorder.font");
        Object oldMessage = UIManager.get("OptionPane.messageFont");
        try {
            UIManager.put("Label.font", new FontUIResource("Serif", Font.PLAIN, 9));
            UIManager.put("Table.font", new FontUIResource("Monospaced", Font.PLAIN, 21));
            UIManager.put("TitledBorder.font", new FontUIResource("Serif", Font.BOLD, 8));
            UIManager.put("OptionPane.messageFont", new FontUIResource("Serif", Font.PLAIN, 30));
            Font menu = UIManager.getFont("Menu.font");
            Ui.unifyFonts();
            for (String key : new String[] {"Label.font", "Table.font", "OptionPane.messageFont", "TextField.font", "Button.font"}) {
                assertThat(UIManager.getFont(key).getSize()).as(key).isEqualTo(menu.getSize());
                assertThat(UIManager.getFont(key).getFamily()).as(key).isEqualTo(menu.getFamily());
            }
            assertThat(UIManager.getFont("TitledBorder.font").isBold()).isTrue();
            assertThat(UIManager.getFont("Menu.font").getSize()).isEqualTo(menu.getSize());
        } finally {
            UIManager.put("Label.font", oldLabel);
            UIManager.put("Table.font", oldTable);
            UIManager.put("TitledBorder.font", oldTitle);
            UIManager.put("OptionPane.messageFont", oldMessage);
        }
    }
}
