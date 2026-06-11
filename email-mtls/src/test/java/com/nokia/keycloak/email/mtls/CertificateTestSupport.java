/*
 * Copyright 2026 Nokia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nokia.keycloak.email.mtls;

import java.io.File;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Test-only helpers to generate X.509 certificates and PKCS12/JKS/BCFKS keystores
 * using BouncyCastle. Used by the keystore-loading unit tests and the mTLS handshake
 * test. Nothing here is part of the shipped artifact.
 */
final class CertificateTestSupport {

    static final char[] PASSWORD = "changeit".toCharArray();
    static final String PASSWORD_STRING = "changeit";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertificateTestSupport() {
    }

    static void ensureBouncyCastle() {
        // Triggers the static initializer above.
    }

    static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /** Creates a self-signed certificate usable both as an identity and as a trust anchor. */
    static X509Certificate selfSigned(KeyPair keyPair, String commonName) throws Exception {
        return issue(keyPair.getPublic(), commonName, keyPair.getPrivate(), commonName);
    }

    private static X509Certificate issue(PublicKey subjectPublicKey, String subjectCn,
                                         PrivateKey issuerKey, String issuerCn) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 3_600_000L);
        X500Name issuer = new X500Name("CN=" + issuerCn);
        X500Name subject = new X500Name("CN=" + subjectCn);
        BigInteger serial = BigInteger.valueOf(now);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, subjectPublicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(issuerKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    /**
     * Writes a keystore of the given type containing a single private-key entry to a
     * fresh temp file with the given extension (so the loader can auto-detect the type).
     *
     * @param type      keystore type, e.g. PKCS12, JKS, BCFKS
     * @param extension file extension including the dot, e.g. ".p12", ".jks", ".bcfks", ".store"
     */
    static File writeKeyStore(String type, String extension, PrivateKey key, X509Certificate cert)
            throws Exception {
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance(type);
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", key, PASSWORD, new Certificate[]{cert});
        return store(keyStore, extension);
    }

    /** Writes an existing keystore to a fresh temp file with the given extension. */
    static File store(java.security.KeyStore keyStore, String extension) throws Exception {
        File file = Files.createTempFile("mtls-test-", extension).toFile();
        file.deleteOnExit();
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            keyStore.store(out, PASSWORD);
        }
        return file;
    }

    /** Builds an in-memory PKCS12 truststore that trusts the given certificates. */
    static java.security.KeyStore trustStoreWith(X509Certificate... certs) throws Exception {
        java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        for (int i = 0; i < certs.length; i++) {
            trustStore.setCertificateEntry("anchor-" + i, certs[i]);
        }
        return trustStore;
    }
}
