// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileIntakeTest {
    @Test
    void acceptsPdfsByNameAndRejectsOthers(@TempDir Path dir) throws Exception {
        File pdf = Files.writeString(dir.resolve("a.PDF"), "x").toFile();
        File notYetLocal = dir.resolve("nur-online.pdf").toFile();
        File txt = Files.writeString(dir.resolve("b.txt"), "x").toFile();
        File folder = Files.createDirectory(dir.resolve("ordner.pdf")).toFile();
        var intake = FileIntake.sort(List.of(pdf, notYetLocal, txt, folder));
        assertThat(intake.accepted()).containsExactly(pdf, notYetLocal);
        assertThat(intake.rejected()).containsExactly("b.txt", "ordner.pdf");
    }
}
