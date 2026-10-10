// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwapTest {
    private static void jar(Path dir, String content) throws Exception {
        Files.createDirectories(dir.resolve("lib"));
        Files.writeString(dir.resolve("lib/e-rechnung.jar"), content);
    }

    /** Inhalt der Jar der Version, die laut Zeiger startet. */
    private static String active(Path home) throws Exception {
        return Files.readString(Swap.activeDir(home).resolve("lib/e-rechnung.jar"));
    }

    private static void stage(Path home, String version, String content) throws Exception {
        jar(home.resolve("daten/update/neu/app"), content);
        Files.writeString(home.resolve("daten/update/neu/version.txt"), version);
    }

    private static String pointer(Path home, String name) throws Exception {
        return Files.readString(home.resolve("versionen/" + name + ".txt")).trim();
    }

    private static String result(Path home) throws Exception {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(home.resolve("daten/update/ergebnis.properties"))) {
            p.load(r);
        }
        return p.getProperty("aktion");
    }

    @Test
    void nothingToDoLeavesEverythingAlone(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(active(home)).isEqualTo("v1");
        assertThat(home.resolve("versionen")).doesNotExist();
    }

    @Test
    void stagedUpdateGetsItsOwnFolderAndOnlyThePointerMoves(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "0.2.0", "v2");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.UPDATE);
        assertThat(active(home)).isEqualTo("v2");
        assertThat(pointer(home, "aktuell")).isEqualTo("versionen/0.2.0");
        assertThat(pointer(home, "vorher")).isEqualTo("app");
        assertThat(Files.readString(home.resolve("app/lib/e-rechnung.jar"))).as("die bisherige Version bleibt unberührt").isEqualTo("v1");
        assertThat(home.resolve("daten/update/probation.flag")).exists();
        assertThat(home.resolve("daten/update/neu")).doesNotExist();
        assertThat(result(home)).isEqualTo("UPDATE");
        assertThat(Swap.run(home)).as("zweiter Start ohne Bestätigung gilt als gescheitert").isEqualTo(Swap.Action.ROLLBACK_AUTOMATIC);
    }

    @Test
    void newVersionThatNeverConfirmedIsRolledBackByPointerAndRemoved(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "0.2.0", "v2");
        Swap.run(home);
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.ROLLBACK_AUTOMATIC);
        assertThat(active(home)).isEqualTo("v1");
        assertThat(pointer(home, "aktuell")).isEqualTo("app");
        assertThat(home.resolve("versionen/vorher.txt")).doesNotExist();
        assertThat(home.resolve("versionen/0.2.0")).as("die gescheiterte Version wird aufgeräumt").doesNotExist();
        assertThat(home.resolve("daten/update/probation.flag")).doesNotExist();
        assertThat(result(home)).isEqualTo("ROLLBACK_AUTOMATIC");
        assertThat(Swap.run(home)).as("danach ist Ruhe").isEqualTo(Swap.Action.NONE);
    }

    @Test
    void confirmedUpdateStaysAndOldRollbackFlagIsIgnored(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "0.2.0", "v2");
        Swap.run(home);
        Files.delete(home.resolve("daten/update/probation.flag"));
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(active(home)).isEqualTo("v2");

        Files.writeString(home.resolve("daten/update/rollback.flag"), "x");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(active(home)).isEqualTo("v2");
    }

    @Test
    void rollbackWithoutPreviousVersionFailsAndKeepsTheActiveOne(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        Files.createDirectories(home.resolve("daten/update"));
        Files.writeString(home.resolve("daten/update/probation.flag"), "x");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.FAILED);
        assertThat(active(home)).isEqualTo("v1");
        assertThat(result(home)).isEqualTo("FAILED");
    }

    @Test
    void secondUpdateKeepsOnlyCurrentAndPreviousVersion(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "0.2.0", "v2");
        Swap.run(home);
        Files.delete(home.resolve("daten/update/probation.flag"));
        assertThat(home.resolve("app")).as("die Vorversion bleibt für den Rollback").exists();
        stage(home, "0.3.0", "v3");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.UPDATE);
        assertThat(active(home)).isEqualTo("v3");
        assertThat(pointer(home, "vorher")).isEqualTo("versionen/0.2.0");
        assertThat(home.resolve("versionen/0.2.0/lib/e-rechnung.jar")).hasContent("v2");
        assertThat(home.resolve("app")).as("der Altbestand ist nun zwei Versionen alt und wird aufgeräumt").doesNotExist();
    }

    @Test
    void leftoverFolderOfTheSameVersionNeverBlocksTheUpdate(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        jar(home.resolve("versionen/0.2.0"), "rest-einer-frueheren-version");
        stage(home, "0.2.0", "v2");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.UPDATE);
        assertThat(active(home)).isEqualTo("v2");
        assertThat(pointer(home, "aktuell")).startsWith("versionen/0.2.0-");
    }

    @Test
    void stagedPackageWithoutAValidVersionIsDiscardedAndNothingChanges(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        for (String bad : new String[] {"../../boese", "abc", "0.2.0-beta", ""}) {
            stage(home, bad, "v2");
            assertThat(Swap.run(home)).as("Version '%s'", bad).isEqualTo(Swap.Action.FAILED);
            assertThat(active(home)).isEqualTo("v1");
            assertThat(home.resolve("daten/update/neu")).doesNotExist();
            assertThat(home.resolve("versionen")).doesNotExist();
        }
        // Paket ohne version.txt (Rest aus einer älteren Programmversion)
        jar(home.resolve("daten/update/neu/app"), "v2");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.FAILED);
        assertThat(active(home)).isEqualTo("v1");
        assertThat(home.resolve("daten/update/neu")).doesNotExist();
        assertThat(result(home)).isEqualTo("FAILED");
    }

    @Test
    void stagedPackageThatIsNotNewerThanTheRunningVersionIsDiscarded(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "0.2.0", "v2");
        Swap.run(home);
        Files.delete(home.resolve("daten/update/probation.flag"));

        for (String old : new String[] {"0.2.0", "0.1.9", "0.1"}) {
            stage(home, old, "alt");
            assertThat(Swap.run(home)).as("Version %s", old).isEqualTo(Swap.Action.FAILED);
            assertThat(active(home)).isEqualTo("v2");
            assertThat(home.resolve("daten/update/neu")).doesNotExist();
        }
        stage(home, "0.10.0", "v3");
        assertThat(Swap.run(home)).as("0.10.0 ist neuer als 0.2.0").isEqualTo(Swap.Action.UPDATE);
        assertThat(active(home)).isEqualTo("v3");
    }

    @Test
    void versionsAreComparedNumerically() {
        assertThat(Swap.compareVersions("0.1.10", "0.1.9")).isPositive();
        assertThat(Swap.compareVersions("0.2", "0.2.0")).isZero();
        assertThat(Swap.compareVersions("1.0.0", "0.9.9")).isPositive();
        assertThat(Swap.compareVersions("0.1.3", "0.1.4")).isNegative();
    }

    @Test
    void manipulatedPointerFallsBackToTheLegacyFolder(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        Files.createDirectories(home.resolve("versionen"));
        Files.writeString(home.resolve("versionen/aktuell.txt"), "../../etc");
        assertThat(Swap.activeDir(home)).isEqualTo(home.resolve("app"));
    }

    @Test
    void incompleteStagingWithoutJarIsIgnored(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        Files.createDirectories(home.resolve("daten/update/neu/app/lib"));
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(active(home)).isEqualTo("v1");
    }

    @Test
    void cleanupRemovesOldLayoutLeftoversButNothingWithoutAValidActiveVersion(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("app.alt/lib"));
        jar(home.resolve("versionen/irgendwas"), "x");
        Swap.cleanup(home);
        assertThat(home.resolve("app.alt")).as("ohne startfähige Version wird nichts angefasst").exists();
        assertThat(home.resolve("versionen/irgendwas")).exists();

        jar(home.resolve("app"), "v1");
        Swap.cleanup(home);
        assertThat(home.resolve("app.alt")).doesNotExist();
        assertThat(home.resolve("versionen/irgendwas")).doesNotExist();
        assertThat(active(home)).isEqualTo("v1");
    }

    @Test
    void updateAndRollbackNeverTouchTheArchiveOrTheUserData(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        Files.createDirectories(home.resolve("archiv/Muster GmbH/2027/01/RE-1"));
        Files.writeString(home.resolve("archiv/Muster GmbH/2027/01/RE-1/ausgabe-xrechnung.xml"), "<Invoice/>");
        Files.createDirectories(home.resolve("daten"));
        Files.writeString(home.resolve("daten/mandanten.json"), "[{\"id\":\"m1\"}]");
        Files.writeString(home.resolve("daten/protokoll.jsonl"), "{\"seq\":1}\n");
        stage(home, "0.2.0", "v2");

        Swap.run(home);
        assertThat(active(home)).isEqualTo("v2");
        assertThat(Files.readString(home.resolve("archiv/Muster GmbH/2027/01/RE-1/ausgabe-xrechnung.xml"))).isEqualTo("<Invoice/>");
        assertThat(Files.readString(home.resolve("daten/mandanten.json"))).isEqualTo("[{\"id\":\"m1\"}]");
        assertThat(Files.readString(home.resolve("daten/protokoll.jsonl"))).isEqualTo("{\"seq\":1}\n");

        // Startet die neue Version nicht, kehrt der nächste Start zur alten zurück; Archiv und Daten bleiben auch dabei unberührt.
        Swap.run(home);
        assertThat(active(home)).isEqualTo("v1");
        assertThat(Files.readString(home.resolve("archiv/Muster GmbH/2027/01/RE-1/ausgabe-xrechnung.xml"))).isEqualTo("<Invoice/>");
        assertThat(Files.readString(home.resolve("daten/mandanten.json"))).isEqualTo("[{\"id\":\"m1\"}]");
    }

    @Test
    void progressIsReportedInOrderUpToOneHundredForUpdateAndRollback(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "0.2.0", "v2");
        var steps = new java.util.ArrayList<Integer>();
        var texts = new java.util.ArrayList<String>();
        Swap.run(home, (percent, text) -> {
            steps.add(percent);
            texts.add(text);
        });
        assertThat(steps).isSorted();
        assertThat(steps.get(0)).isLessThan(20);
        assertThat(steps).last().isEqualTo(100);
        assertThat(texts).first().isEqualTo("Update wird eingespielt …");

        steps.clear();
        Swap.run(home, (percent, text) -> steps.add(percent));
        assertThat(steps).as("Rollback meldet ebenfalls Fortschritt").isSorted().last().isEqualTo(100);
    }

    @Test
    void windowIsOnlyOpenedWhereThereIsADisplay() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            assertThat(SwapWindow.open("Update wird eingespielt …")).isNull();
            return;
        }
        SwapWindow w = SwapWindow.open("Update wird eingespielt …");
        assertThat(w).isNotNull();
        w.step(60, "Neue Version wird aktiviert …");
        Thread.sleep(300);
        w.close();
    }
}
