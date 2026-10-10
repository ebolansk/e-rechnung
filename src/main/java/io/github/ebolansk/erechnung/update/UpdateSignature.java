// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519-Signatur der Update-Pakete. Die SHA-256-Summe steht im selben Release wie das Paket und schützt nur vor einem
 * beschädigten Download; die Signatur beweist, dass das Paket vom Besitzer des privaten Schlüssels stammt. Der öffentliche
 * Schlüssel liegt in der Anwendung (Ressource update-public-key.txt) oder, für einen Fork, in daten/update.json.
 *
 * <p>Kommandozeile für den Release-Prozess: {@code keygen <Ordner>}, {@code sign <privater-Schlüssel> <Datei>} (gibt die
 * Signatur auf der Standardausgabe aus) und {@code verify <Datei> <Signaturdatei>} (prüft gegen den eingebauten Schlüssel).
 */
public final class UpdateSignature {
    private static final String ALGORITHM = "Ed25519";

    private UpdateSignature() {
    }

    /** Der in die Anwendung eingebaute öffentliche Schlüssel. */
    public static PublicKey embeddedKey() throws IOException {
        try (InputStream in = UpdateSignature.class.getResourceAsStream("/update-public-key.txt")) {
            if (in == null) {
                throw new IOException("Der eingebaute Update-Schlüssel fehlt.");
            }
            return parsePublic(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static PublicKey parsePublic(String base64) throws IOException {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Der öffentliche Update-Schlüssel ist ungültig.", e);
        }
    }

    public static PrivateKey parsePrivate(String base64) throws IOException {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Der private Update-Schlüssel ist ungültig.", e);
        }
    }

    public static String sign(byte[] data, PrivateKey key) throws IOException {
        try {
            Signature s = Signature.getInstance(ALGORITHM);
            s.initSign(key);
            s.update(data);
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IOException("Signieren fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Wahr nur bei einer gültigen Signatur; jede Art von Fehler (kaputtes Format, falscher Schlüssel) ist „nicht gültig“. */
    public static boolean verify(byte[] data, String signatureBase64, PublicKey key) {
        try {
            Signature s = Signature.getInstance(ALGORITHM);
            s.initVerify(key);
            s.update(data);
            return s.verify(decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] decode(String base64) {
        return Base64.getMimeDecoder().decode(base64.trim());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("keygen")) {
            Path dir = Path.of(args[1]);
            Files.createDirectories(dir);
            KeyPair kp = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            Path priv = dir.resolve("update-signing.key");
            if (Files.exists(priv)) {
                throw new IOException("Es gibt schon einen Schlüssel: " + priv + " (wird nicht überschrieben).");
            }
            Files.writeString(priv, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n");
            try {
                Files.setPosixFilePermissions(priv, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // kein POSIX-Dateisystem
            }
            Files.writeString(dir.resolve("update-public-key.txt"), Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n");
            System.out.println("Privater Schlüssel: " + priv + " (geheim halten, sichern)");
            System.out.println("Öffentlicher Schlüssel: " + dir.resolve("update-public-key.txt")
                + " (nach src/main/resources/update-public-key.txt kopieren)");
        } else if (args.length == 3 && args[0].equals("sign")) {
            PrivateKey key = parsePrivate(Files.readString(Path.of(args[1])));
            System.out.println(sign(Files.readAllBytes(Path.of(args[2])), key));
        } else if (args.length == 3 && args[0].equals("verify")) {
            boolean ok = verify(Files.readAllBytes(Path.of(args[1])), Files.readString(Path.of(args[2])), embeddedKey());
            System.out.println(ok ? "Signatur gültig" : "Signatur UNGÜLTIG");
            System.exit(ok ? 0 : 1);
        } else {
            System.err.println("Aufruf: keygen <Ordner> | sign <Schlüsseldatei> <Datei> | verify <Datei> <Signaturdatei>");
            System.exit(2);
        }
    }
}
