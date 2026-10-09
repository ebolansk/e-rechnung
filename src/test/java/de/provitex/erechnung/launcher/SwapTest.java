// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.launcher;

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

    private static String jarOf(Path home, String folder) throws Exception {
        return Files.readString(home.resolve(folder).resolve("lib/e-rechnung.jar"));
    }

    private static void stage(Path home, String content) throws Exception {
        jar(home.resolve("daten/update/neu/app"), content);
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
        assertThat(jarOf(home, "app")).isEqualTo("v1");
        assertThat(home.resolve("app.alt")).doesNotExist();
    }

    @Test
    void stagedUpdateIsSwappedInAndPreviousVersionKept(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "v2");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.UPDATE);
        assertThat(jarOf(home, "app")).isEqualTo("v2");
        assertThat(jarOf(home, "app.alt")).isEqualTo("v1");
        assertThat(home.resolve("daten/update/probation.flag")).exists();
        assertThat(home.resolve("daten/update/neu")).doesNotExist();
        assertThat(result(home)).isEqualTo("UPDATE");
        assertThat(Swap.run(home)).as("zweiter Start ohne Bestätigung gilt als gescheitert").isEqualTo(Swap.Action.ROLLBACK_AUTOMATIC);
    }

    @Test
    void newVersionThatNeverConfirmedIsRolledBackAutomatically(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "v2");
        Swap.run(home);
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.ROLLBACK_AUTOMATIC);
        assertThat(jarOf(home, "app")).isEqualTo("v1");
        assertThat(home.resolve("app.alt")).doesNotExist();
        assertThat(home.resolve("app.defekt")).doesNotExist();
        assertThat(home.resolve("daten/update/probation.flag")).doesNotExist();
        assertThat(result(home)).isEqualTo("ROLLBACK_AUTOMATIC");
        assertThat(Swap.run(home)).as("danach ist Ruhe").isEqualTo(Swap.Action.NONE);
    }

    @Test
    void confirmedUpdateStaysAndManualRollbackWorks(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "v2");
        Swap.run(home);
        Files.delete(home.resolve("daten/update/probation.flag"));
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(jarOf(home, "app")).isEqualTo("v2");

        Files.writeString(home.resolve("daten/update/rollback.flag"), "x");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.ROLLBACK_MANUAL);
        assertThat(jarOf(home, "app")).isEqualTo("v1");
        assertThat(home.resolve("daten/update/rollback.flag")).doesNotExist();
    }

    @Test
    void rollbackWithoutPreviousVersionFailsAndKeepsApp(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        Files.createDirectories(home.resolve("daten/update"));
        Files.writeString(home.resolve("daten/update/rollback.flag"), "x");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.FAILED);
        assertThat(jarOf(home, "app")).isEqualTo("v1");
        assertThat(result(home)).isEqualTo("FAILED");
    }

    @Test
    void secondUpdateReplacesTheOlderBackup(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        stage(home, "v2");
        Swap.run(home);
        Files.delete(home.resolve("daten/update/probation.flag"));
        stage(home, "v3");
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.UPDATE);
        assertThat(jarOf(home, "app")).isEqualTo("v3");
        assertThat(jarOf(home, "app.alt")).isEqualTo("v2");
    }

    @Test
    void incompleteStagingWithoutJarIsIgnored(@TempDir Path home) throws Exception {
        jar(home.resolve("app"), "v1");
        Files.createDirectories(home.resolve("daten/update/neu/app/lib"));
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(jarOf(home, "app")).isEqualTo("v1");
    }
}
