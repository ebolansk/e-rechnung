// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

import com.fasterxml.jackson.databind.JsonNode;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragt auf Klick das neueste Release des Repositories ab (öffentlich ohne Token, privat mit Token) und lädt das Paket. Das Token geht nur an api.github.com;
 * der Weiterleitung auf den Datei-Speicher folgt der Client selbst und ohne Token.
 */
public final class GithubReleaseClient {
    public static final String API = "https://api.github.com";
    private static final Pattern HASH_LINE = Pattern.compile("(?i)sha-?256\\s*[:=]?\\s*([0-9a-f]{64})");
    private static final Pattern HASH_ONLY = Pattern.compile("(?i)\\b([0-9a-f]{64})\\b");
    private static final long MAX_PACKAGE = 600L * 1024 * 1024;

    private final String apiBase;
    private final UpdateSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public GithubReleaseClient(String apiBase, UpdateSettings settings) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.settings = settings;
    }

    public GithubReleaseClient(UpdateSettings settings) {
        this(API, settings);
    }

    /** Das neueste veröffentlichte Release mit Paket. Leer, wenn es noch keines gibt. */
    public Optional<ReleaseInfo> latest() throws IOException {
        HttpResponse<byte[]> res = send(apiBase + "/repos/" + settings.repo() + "/releases/latest", "application/vnd.github+json", true);
        if (res.statusCode() == 404) {
            throw new IOException("Release oder Repository nicht gefunden. Bitte das Repository unter Konfiguration → Update prüfen. "
                + "Gibt es noch kein veröffentlichtes Release, ist das ebenfalls die Ursache"
                + (settings.token().isBlank() ? " (ein privates Repository braucht ein Token)." : "."));
        }
        check(res, "Release-Abfrage");
        JsonNode root = Json.mapper().readTree(res.body());
        String tag = root.path("tag_name").asText("");
        String version = VersionCompare.normalize(tag);
        String expected = "E-Rechnung-update-" + version + ".zip";
        JsonNode pkg = null;
        JsonNode hashAsset = null;
        for (JsonNode a : root.path("assets")) {
            String name = a.path("name").asText("");
            if (name.equals(expected)) {
                pkg = a;
            } else if (name.equals(expected + ".sha256")) {
                hashAsset = a;
            }
        }
        if (pkg == null) {
            return Optional.empty();
        }
        String notes = root.path("body").asText("");
        String sha = null;
        if (hashAsset != null) {
            HttpResponse<byte[]> h = send(hashAsset.path("url").asText(), "application/octet-stream", true);
            h = follow(h);
            check(h, "Prüfsumme");
            Matcher m = HASH_ONLY.matcher(new String(h.body(), StandardCharsets.UTF_8));
            sha = m.find() ? m.group(1).toLowerCase() : null;
        }
        if (sha == null) {
            Matcher m = HASH_LINE.matcher(notes);
            sha = m.find() ? m.group(1).toLowerCase() : null;
        }
        if (sha == null) {
            throw new IOException("Das Release " + tag + " enthält keine SHA-256-Prüfsumme. Es wird nicht heruntergeladen.");
        }
        return Optional.of(new ReleaseInfo(tag, version, notes, expected, pkg.path("url").asText(), pkg.path("size").asLong(0), sha));
    }

    /** Lädt das Paket nach target. Bricht ab, wenn es größer ist als erlaubt. Die Prüfsumme prüft der Aufrufer. */
    public void download(ReleaseInfo release, Path target) throws IOException {
        HttpResponse<InputStream> res = sendStream(release.packageUrl(), true);
        res = followStream(res);
        if (res.statusCode() / 100 != 2) {
            try (InputStream in = res.body()) {
                throw new IOException("Der Download schlug fehl (Status " + res.statusCode() + ").");
            }
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = res.body()) {
            long copied = 0;
            try (var out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    copied += n;
                    if (copied > MAX_PACKAGE) {
                        throw new IOException("Das Paket ist größer als erlaubt und wird verworfen.");
                    }
                    out.write(buf, 0, n);
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private HttpResponse<byte[]> send(String url, String accept, boolean auth) throws IOException {
        try {
            return http.send(request(url, accept, auth), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Die Anfrage wurde unterbrochen.", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige Adresse: " + url, e);
        }
    }

    private HttpResponse<InputStream> sendStream(String url, boolean auth) throws IOException {
        try {
            return http.send(request(url, "application/octet-stream", auth), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Der Download wurde unterbrochen.", e);
        }
    }

    private HttpRequest request(String url, String accept, boolean auth) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10))
            .header("Accept", accept).header("User-Agent", "e-rechnung-tool").header("X-GitHub-Api-Version", "2022-11-28");
        if (auth && !settings.token().isBlank()) {
            b.header("Authorization", "Bearer " + settings.token());
        }
        return b.GET().build();
    }

    /** Ein Redirect auf den Datei-Speicher: ohne Token weiterverfolgen (der Speicher lehnt fremde Autorisierung ab). */
    private HttpResponse<byte[]> follow(HttpResponse<byte[]> res) throws IOException {
        for (int i = 0; i < 3 && res.statusCode() / 100 == 3; i++) {
            String loc = res.headers().firstValue("Location").orElseThrow(() -> new IOException("Weiterleitung ohne Ziel."));
            res = send(URI.create(res.uri().toString()).resolve(loc).toString(), "application/octet-stream", false);
        }
        return res;
    }

    private HttpResponse<InputStream> followStream(HttpResponse<InputStream> res) throws IOException {
        for (int i = 0; i < 3 && res.statusCode() / 100 == 3; i++) {
            String loc = res.headers().firstValue("Location").orElseThrow(() -> new IOException("Weiterleitung ohne Ziel."));
            res.body().close();
            res = sendStream(URI.create(res.uri().toString()).resolve(loc).toString(), false);
        }
        return res;
    }

    private static void check(HttpResponse<byte[]> res, String what) throws IOException {
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new IOException(what + ": GitHub verweigert den Zugriff (Status " + res.statusCode()
                + "). Bei hinterlegtem Token ist es ungültig, abgelaufen oder ohne Leserecht; ohne Token ist meist das Abfragelimit "
                + "von GitHub erreicht, bitte später erneut versuchen.");
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException(what + " schlug fehl (Status " + res.statusCode() + ").");
        }
    }
}
