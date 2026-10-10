// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Schlüsselpaar nur für Tests: signiert Pakete wie der Release-Prozess, ohne den echten Schlüssel zu brauchen. */
public final class TestSigning {
    public static final KeyPair PAIR = generate();

    private TestSigning() {
    }

    private static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sign(byte[] data) throws IOException {
        return UpdateSignature.sign(data, PAIR.getPrivate());
    }

    /** Der öffentliche Schlüssel im Format von daten/update.json (signingKey). */
    public static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(PAIR.getPublic().getEncoded());
    }
}
