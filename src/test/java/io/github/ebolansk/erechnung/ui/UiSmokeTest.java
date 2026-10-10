// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.TestData;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.extract.RuleBasedExtractor;
import io.github.ebolansk.erechnung.extract.RuleBasedExtractorTest;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiSmokeTest {
    static void snapshot(JComponent c, Dimension size, Path file) throws Exception {
        c.setSize(size);
        layoutDeep(c);
        BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        c.paint(img.getGraphics());
        Files.createDirectories(file.getParent());
        ImageIO.write(img, "png", file.toFile());
    }

    static void layoutDeep(Component c) {
        c.doLayout();
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                layoutDeep(child);
            }
        }
    }

    private static ReviewPanel review() throws Exception {
        var extraction = RuleBasedExtractor.extract(RuleBasedExtractorTest.TEXT, RuleBasedExtractorTest.mandant());
        byte[] pdf = TestData.embeddedFontPdf("Rechnung RE-2027-0001");
        return new ReviewPanel(pdf, extraction, RuleBasedExtractorTest.mandant(), () -> { }, () -> { });
    }

    @Test
    void reviewPanelShowsMissingBuyerEmailUntilFilledAndMatchesTotals() throws Exception {
        ReviewPanel p = review();
        assertThat(p.currentProblems()).anyMatch(m -> m.contains("Käufer") && m.contains("E-Mail"));
        assertThat(p.acceptEnabled()).isFalse();
        assertThat(p.totalsStatus()).contains("stimmen überein");
        p.buyerEmailField().setText("einkauf@kunde.example");
        assertThat(p.currentProblems()).isEmpty();
        assertThat(p.acceptEnabled()).isTrue();
        assertThat(p.result()).isEmpty();
        p.accept();
        assertThat(p.result()).isPresent();
        assertThat(p.result().get().number()).isEqualTo("RE-2027-0001");
        assertThat(p.result().get().items()).hasSize(2);
        p.close();
    }

    @Test
    void reviewPanelFlagsTotalsMismatchAndUnparsableInput() throws Exception {
        ReviewPanel p = review();
        p.buyerEmailField().setText("einkauf@kunde.example");
        p.issueDateField().setText("kein Datum");
        assertThat(p.acceptEnabled()).isFalse();
        assertThat(p.currentProblems()).anyMatch(m -> m.contains("Rechnungsdatum"));
        p.issueDateField().setText("15.01.2027");
        p.setItemCell(0, 1, "4");
        assertThat(p.totalsStatus()).contains("im PDF").contains("berechnet");
        p.close();
    }

    @Test
    void openCellEditIsCommittedWhenAccepting() throws Exception {
        ReviewPanel p = review();
        p.buyerEmailField().setText("einkauf@kunde.example");
        javax.swing.JTable t = p.itemTable();
        t.editCellAt(0, 0);
        ((javax.swing.JTextField) t.getEditorComponent()).setText("Beratung neu");
        p.accept();
        assertThat(p.result()).isPresent();
        assertThat(p.result().get().items().get(0).name()).isEqualTo("Beratung neu");
        p.close();
    }

    @Test
    void totalsMismatchNeedsExplicitConfirmationAndIsReturned() throws Exception {
        ReviewPanel p = review();
        p.buyerEmailField().setText("einkauf@kunde.example");
        assertThat(p.acceptEnabled()).isTrue();
        assertThat(p.confirmMismatchBox().isVisible()).isFalse();
        p.setItemCell(0, 1, "4");
        assertThat(p.confirmMismatchBox().isVisible()).isTrue();
        assertThat(p.acceptEnabled()).isFalse();
        p.confirmMismatchBox().doClick();
        assertThat(p.acceptEnabled()).isTrue();
        p.accept();
        var c = p.confirmation();
        assertThat(c.totalsMismatchConfirmed()).isTrue();
        assertThat(c.printedGross()).isEqualByComparingTo("3333.19");
        p.close();
    }

    @Test
    void mandantPanelValidatesRequiredFields() {
        MandantPanel mp = new MandantPanel(RuleBasedExtractor.draftMandant(RuleBasedExtractorTest.TEXT));
        // Ansprechpartner und Telefon sind im Dialog optional (nur die XRechnung verlangt sie, geprüft in der Prüfmaske).
        var m = mp.collect();
        assertThat(mp.errorText()).doesNotContain("Ansprechpartner").doesNotContain("Telefon");
        assertThat(m).isPresent();
        assertThat(m.get().vatId()).isEqualTo("DE123456789");
        assertThat(m.get().iban()).isEqualTo("DE02120300000000202051");
    }

    @Test
    void sellerTabEditsFlowIntoTheInvoiceAndAreReportedAsMandantChange() throws Exception {
        ReviewPanel p = review();
        assertThat(p.mandantChanged()).isFalse();
        p.buyerEmailField().setText("einkauf@kunde.example");
        p.sellerPanel().contactNameField().setText("Neue Ansprechperson");
        assertThat(p.mandantChanged()).isTrue();
        assertThat(p.mandant().contactName()).isEqualTo("Neue Ansprechperson");
        p.accept();
        assertThat(p.result()).isPresent();
        assertThat(p.result().get().seller().contactName()).isEqualTo("Neue Ansprechperson");
        p.close();
    }

    @Test
    void emptyContactInTheSellerTabIsReportedForXrechnungOnly() throws Exception {
        ReviewPanel p = review();
        p.buyerEmailField().setText("einkauf@kunde.example");
        p.sellerPanel().contactNameField().setText("");
        assertThat(p.currentProblems()).anyMatch(m -> m.contains("BT-41"));
        p.close();
    }

    @Test
    void panelsRenderToImages(@TempDir Path dir) throws Exception {
        Path out = Path.of("target/ui-snapshots");
        ReviewPanel r = review();
        snapshot(r, new Dimension(1200, 800), out.resolve("pruefmaske.png"));
        r.close();
        snapshot(new MandantPanel(RuleBasedExtractor.draftMandant(RuleBasedExtractorTest.TEXT)), new Dimension(520, 520),
            out.resolve("mandant.png"));
        var settings = new SettingsPanel(dir, new ConfigStore(dir.resolve("k.json")),
            new UpdateSettingsPanel(new io.github.ebolansk.erechnung.update.UpdateSettingsStore(dir.resolve("u.json"))));
        snapshot(settings, new Dimension(640, 360), out.resolve("einstellungen.png"));
        var mandantStore = new MandantStore(dir.resolve("m.json"));
        snapshot(new MandantListPanel(mandantStore, null), new Dimension(620, 340), out.resolve("mandanten.png"));
        for (String n : new String[] {"pruefmaske.png", "mandant.png", "einstellungen.png", "mandanten.png"}) {
            assertThat(out.resolve(n)).exists().isNotEmptyFile();
        }
    }

    @Test
    void aiButtonIsDisabledWithoutConfigurationAndAppliesOnlyChosenFields() throws Exception {
        var extraction = RuleBasedExtractor.extract(RuleBasedExtractorTest.TEXT, RuleBasedExtractorTest.mandant());
        byte[] pdf = TestData.embeddedFontPdf("Rechnung RE-2027-0001");
        Path dir = Files.createTempDirectory("ki");
        var store = new io.github.ebolansk.erechnung.ai.AiSettingsStore(dir.resolve("ki.json"));
        java.util.List<String> logged = new java.util.ArrayList<>();
        var service = new io.github.ebolansk.erechnung.service.AiAssistService(store, (a, d) -> {
            logged.add(a + d);
            return null;
        }, s -> (sys, user) -> "{}");
        var help = new AiHelp(service, RuleBasedExtractorTest.TEXT, "x.pdf");
        ReviewPanel off = new ReviewPanel(pdf, extraction, RuleBasedExtractorTest.mandant(), help, () -> { }, () -> { });
        assertThat(off.aiButton().isEnabled()).isFalse();
        off.close();

        store.save(new io.github.ebolansk.erechnung.ai.AiSettings(io.github.ebolansk.erechnung.ai.AiProvider.OPENAI, "http://x", "m", "", false));
        ReviewPanel p = new ReviewPanel(pdf, extraction, RuleBasedExtractorTest.mandant(), help, () -> { }, () -> { });
        assertThat(p.aiButton().isEnabled()).isTrue();
        var suggestion = new io.github.ebolansk.erechnung.ai.AiSuggestion(
            new java.util.LinkedHashMap<>(java.util.Map.of("bEmail", "einkauf@kunde.example", "number", "RE-2027-0001",
                "bName", "Kunde Aktiengesellschaft")),
            java.util.List.of(), null, null, null, "", java.util.Map.of(), java.util.List.of());
        var rows = p.suggestionRows(suggestion);
        assertThat(rows).extracting(AiSuggestionDialog.Row::key).containsExactlyInAnyOrder("bEmail", "bName");
        assertThat(p.acceptEnabled()).isFalse();
        p.applySuggestion(suggestion, rows.stream().filter(r -> r.key().equals("bEmail")).toList());
        assertThat(p.buyerEmailField().getText()).isEqualTo("einkauf@kunde.example");
        assertThat(p.acceptEnabled()).isTrue();
        p.accept();
        assertThat(p.result().get().buyer().name()).isEqualTo("Kunde AG");
        assertThat(logged).hasSize(1).first().asString().contains("ki-uebernommen").contains("bEmail").doesNotContain("bName");
        p.close();
    }

    @Test
    void aiSettingsPanelSnapshot(@TempDir Path dir) throws Exception {
        var panel = new AiSettingsPanel(new io.github.ebolansk.erechnung.ai.AiSettingsStore(dir.resolve("ki.json")));
        panel.providerBox().setSelectedItem(io.github.ebolansk.erechnung.ai.AiProvider.CLAUDE);
        assertThat(panel.urlField().getText()).isEqualTo(io.github.ebolansk.erechnung.ai.AiSettings.CLAUDE_URL);
        assertThat(panel.urlField().isEditable()).isFalse();
        panel.providerBox().setSelectedItem(io.github.ebolansk.erechnung.ai.AiProvider.OPENAI);
        assertThat(panel.urlField().getText()).isEqualTo(io.github.ebolansk.erechnung.ai.AiSettings.OPENAI_URL);
        assertThat(panel.urlField().isEditable()).isTrue();
        snapshot(panel, new Dimension(620, 480), Path.of("target/ui-snapshots/ki.png"));
        assertThat(Files.exists(Path.of("target/ui-snapshots/ki.png"))).isTrue();
    }

    @Test
    void updateSettingsPanelSnapshot(@TempDir Path dir) throws Exception {
        var store = new io.github.ebolansk.erechnung.update.UpdateSettingsStore(dir.resolve("update.json"));
        store.save(new io.github.ebolansk.erechnung.update.UpdateSettings("o/fork", "geheim"));
        var panel = new UpdateSettingsPanel(store);
        assertThat(panel.checkOnStartSelected()).isFalse();
        snapshot(panel, new Dimension(620, 120), Path.of("target/ui-snapshots/update.png"));
        assertThat(Files.exists(Path.of("target/ui-snapshots/update.png"))).isTrue();

        javax.swing.JCheckBox box = (javax.swing.JCheckBox) ((java.awt.Container) panel.getComponent(0)).getComponent(0);
        box.doClick();
        var saved = store.load();
        assertThat(saved.checkOnStart()).isTrue();
        assertThat(saved.repo()).isEqualTo("o/fork");
        assertThat(saved.token()).isEqualTo("geheim");
        assertThat(new UpdateSettingsPanel(store).checkOnStartSelected()).isTrue();
    }
}
