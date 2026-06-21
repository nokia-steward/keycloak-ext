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
package org.keycloak.email;

import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Test helpers to generate X.509 certificates and PKCS12/JKS/BCFKS keystores using BouncyCastle.
 * Used by the keystore-loading unit tests and the mTLS handshake test.
 */
final class CertificateTestHelper {

    static final String PASSWORD_STRING = "changeit";
    static final char[] PASSWORD = PASSWORD_STRING.toCharArray();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertificateTestHelper() {}

    static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /** Creates a self-signed certificate usable both as an identity and as a trust anchor. */
    static X509Certificate selfSigned(KeyPair keyPair, String commonName) throws Exception {
        return issue(keyPair.getPublic(), commonName, keyPair.getPrivate(), commonName);
    }

    private static X509Certificate issue(
            PublicKey subjectPublicKey, String subjectCn, PrivateKey issuerKey, String issuerCn)
            throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 3_600_000L);
        X500Name issuer = new X500Name("CN=" + issuerCn);
        X500Name subject = new X500Name("CN=" + subjectCn);
        BigInteger serial = BigInteger.valueOf(now);

        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        issuer, serial, notBefore, notAfter, subject, subjectPublicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        ContentSigner signer =
                new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(issuerKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    /** Creates a CA key pair and self-signed CA certificate (basicConstraints CA=true). */
    static X509Certificate caCert(KeyPair caKeyPair, String cn) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 24 * 3_600_000L);
        X500Name name = new X500Name("CN=" + cn);
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        name,
                        BigInteger.valueOf(now),
                        notBefore,
                        notAfter,
                        name,
                        caKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        ContentSigner signer =
                new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(caKeyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    /**
     * Issues a CA-signed leaf certificate with a DNS SAN and the given EKU.
     *
     * @param dnsName value for the DNS SAN (e.g. "smtp" or "localhost")
     * @param ekuServer true -> serverAuth EKU, false -> clientAuth EKU
     */
    static X509Certificate issueLeaf(
            KeyPair leafKeyPair,
            String cn,
            String dnsName,
            X509Certificate caCert,
            PrivateKey caKey,
            boolean ekuServer)
            throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 24 * 3_600_000L);
        X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
        X500Name subject = new X500Name("CN=" + cn);
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        issuer,
                        BigInteger.valueOf(now + (ekuServer ? 1 : 2)),
                        notBefore,
                        notAfter,
                        subject,
                        leafKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        GeneralNames sans = new GeneralNames(new GeneralName(GeneralName.dNSName, dnsName));
        builder.addExtension(Extension.subjectAlternativeName, false, sans);
        KeyPurposeId eku =
                ekuServer ? KeyPurposeId.id_kp_serverAuth : KeyPurposeId.id_kp_clientAuth;
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(eku));
        ContentSigner signer =
                new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(caKey);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    /** Writes one or more PEM objects (certs, keys, etc.) to a fresh temp file. */
    static File writePem(String extension, Object... objects) throws Exception {
        File file = Files.createTempFile("mtls-pem-", extension).toFile();
        file.deleteOnExit();
        try (Writer fw = Files.newBufferedWriter(file.toPath(), StandardCharsets.US_ASCII);
                JcaPEMWriter writer = new JcaPEMWriter(fw)) {
            for (Object obj : objects) {
                writer.writeObject(obj);
            }
        }
        return file;
    }

    /** Returns the PEM encoding of one object as a String. */
    static String toPem(Object obj) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(obj);
        }
        return sw.toString();
    }

    /** Creates an in-memory keystore of the given type with a single private-key entry. */
    static KeyStore newKeyStore(
            String keystoreType, String alias, PrivateKey key, X509Certificate cert)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        keyStore.load(null, null);
        keyStore.setKeyEntry(alias, key, PASSWORD, new Certificate[] {cert});
        return keyStore;
    }

    /**
     * Writes a keystore of the given type containing a single private-key entry to a fresh temp
     * file with the given extension.
     *
     * @param keystoreType keystore type, e.g. PKCS12, JKS, BCFKS
     * @param fileExtension file extension including the dot, e.g. ".p12", ".jks", ".bcfks",
     *     ".store"
     */
    static File createKeystoreFile(
            String keystoreType, String fileExtension, PrivateKey key, X509Certificate cert)
            throws Exception {
        return store(newKeyStore(keystoreType, "client", key, cert), fileExtension);
    }

    /** Writes an existing keystore to a fresh temp file with the given extension. */
    static File store(KeyStore keyStore, String extension) throws Exception {
        File file = Files.createTempFile("mtls-test-", extension).toFile();
        file.deleteOnExit();
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            keyStore.store(out, PASSWORD);
        }
        return file;
    }

    /** Builds an in-memory PKCS12 truststore that trusts the given certificates. */
    static KeyStore trustStoreWith(X509Certificate... certs) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        for (int i = 0; i < certs.length; i++) {
            trustStore.setCertificateEntry("anchor-" + i, certs[i]);
        }
        return trustStore;
    }
}
