// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import io.github.ebolansk.erechnung.audit.AuditTrail;
import io.github.ebolansk.erechnung.update.GithubReleaseClient;
import io.github.ebolansk.erechnung.update.ReleaseInfo;
import io.github.ebolansk.erechnung.update.UpdateInstaller;
import io.github.ebolansk.erechnung.update.UpdateSettings;
import io.github.ebolansk.erechnung.update.UpdateSettingsStore;
import io.github.ebolansk.erechnung.update.UpdateSignature;
import io.github.ebolansk.erechnung.update.VersionCompare;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** „Nach Updates suchen“: auf Klick, auf Wunsch zusätzlich einmal täglich beim Start. Eingespielt wird erst beim nächsten Start. */
public final class UpdateService {
    private final Path home;
    private final UpdateSettingsStore store;
    private final AuditTrail audit;
    private final String currentVersion;
    private final Function<UpdateSettings, GithubReleaseClient> clients;

    public UpdateService(Path home, UpdateSettingsStore store, AuditTrail audit, String currentVersion) {
        this(home, store, audit, currentVersion, GithubReleaseClient::new);
    }

    public UpdateService(Path home, UpdateSettingsStore store, AuditTrail audit, String currentVersion,
                         Function<UpdateSettings, GithubReleaseClient> clients) {
        this.home = home;
        this.store = store;
        this.audit = audit;
        this.currentVersion = currentVersion;
        this.clients = clients;
    }

    public boolean configured() {
        return store.load().usable();
    }

    /**
     * Automatische Prüfung beim Start: nur wenn eingeschaltet und die letzte Abfrage mindestens einen Tag her ist. Fehler
     * (kein Netz, Limit) bleiben still; der Zeitpunkt wird vor der Abfrage notiert, damit ein Fehler nicht jeden Start wiederholt.
     */
    public Optional<ReleaseInfo> checkOnStart(Instant now) {
        UpdateSettings s = store.load();
        if (!s.checkOnStart() || !s.usable()) {
            return Optional.empty();
        }
        Path stamp = home.resolve("daten").resolve("update-letzte-pruefung.txt");
        try {
            if (Files.exists(stamp) && Duration.between(Instant.parse(Files.readString(stamp).trim()), now).compareTo(Duration.ofDays(1)) < 0) {
                return Optional.empty();
            }
        } catch (IOException | RuntimeException e) {
            // unlesbarer Zeitstempel: wie „noch nie geprüft“ behandeln
        }
        try {
            Files.createDirectories(stamp.getParent());
            Files.writeString(stamp, now.toString());
            Optional<ReleaseInfo> r = check();
            audit.append("update-pruefung-start", Map.of("ergebnis", r.isPresent() ? "neue-version-" + r.get().version() : "aktuell"));
            return r;
        } catch (IOException | RuntimeException e) {
            try {
                audit.append("update-pruefung-start", Map.of("ergebnis", "fehlgeschlagen"));
            } catch (IOException ignored) {
                // Protokoll nicht schreibbar: die Prüfung bleibt trotzdem still
            }
            return Optional.empty();
        }
    }

    /** Das neueste Release, wenn es neuer ist als die laufende Version. */
    public Optional<ReleaseInfo> check() throws IOException {
        UpdateSettings s = store.load();
        if (!s.usable()) {
            throw new IOException("Die Update-Quelle ist nicht eingerichtet (daten/update.json: Eintrag repo im Format Name/Repo).");
        }
        return clients.apply(s).latest().filter(r -> VersionCompare.isNewer(r.version(), currentVersion));
    }

    /** Lädt das Paket, prüft SHA-256-Summe und Signatur und bereitet den Austausch vor. */
    public void prepare(ReleaseInfo release) throws IOException {
        if (release.signature() == null || release.signature().isBlank()) {
            throw new IOException("Das Release " + release.tag() + " ist nicht signiert. Es wird aus Sicherheitsgründen nicht heruntergeladen.");
        }
        UpdateSettings s = store.load();
        Path zip = UpdateInstaller.updateDir(home).resolve("download").resolve(release.packageName());
        try {
            clients.apply(s).download(release, zip);
            String keyText = s.signingKey();
            UpdateInstaller.stage(home, zip, release.sha256(), release.version(), release.signature(),
                keyText.isBlank() ? UpdateSignature.embeddedKey() : UpdateSignature.parsePublic(keyText));
        } finally {
            Files.deleteIfExists(zip);
        }
        audit.append("update-vorbereitet", Map.of("von", currentVersion, "nach", release.version(), "sha256", release.sha256()));
    }

    public String pendingVersion() {
        return UpdateInstaller.pendingVersion(home);
    }
}
