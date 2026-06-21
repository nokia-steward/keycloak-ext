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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.Config;

/**
 * Unit tests for {@link MtlsKeystore}: keystore loading for PKCS12/JKS/BCFKS, type auto-detection
 * from the file extension, the FIPS (BCFKS) default, fail-fast error handling with secret-free
 * messages, and the "no keystore -> null" path.
 */
public class MtlsKeystoreTest {

    // Keycloak-side identity (private key + self-signed cert) presented to
    // the SMTP server during mTLS.
    private static KeyPair keyPair;
    private static X509Certificate cert;

    @BeforeClass
    public static void generateKeyMaterial() throws Exception {
        keyPair = CertificateTestHelper.newKeyPair();
        cert = CertificateTestHelper.selfSigned(keyPair, "client");
    }

    @Test
    public void loadsPkcs12Keystore() throws Exception {
        assertKeystoreLoads("PKCS12", ".p12");
    }

    @Test
    public void loadsJksKeystore() throws Exception {
        assertKeystoreLoads("JKS", ".jks");
    }

    @Test
    public void loadsBcfksKeystore() throws Exception {
        assertKeystoreLoads("BCFKS", ".bcfks");
    }

    /**
     * Loads a keystore of the given type from a temp file with the given extension (no explicit
     * type configured), and asserts it loads successfully and produces a usable socket factory.
     */
    private void assertKeystoreLoads(String keystoreType, String fileExtension) throws Exception {
        File ks =
                CertificateTestHelper.createKeystoreFile(
                        keystoreType, fileExtension, keyPair.getPrivate(), cert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        ks.getAbsolutePath(), CertificateTestHelper.PASSWORD_STRING, null);

        MtlsKeystore mtls = MtlsKeystore.from(config);

        assertNotNull(mtls);
        assertTrue(mtls.getKeystoreType().equalsIgnoreCase(keystoreType));
        assertNotNull(mtls.createSocketFactory(MtlsTestMocks.sessionWithoutTruststore()));
    }

    /**
     * ".pfx" is a Windows alias for PKCS12. Without an explicit type in the config, the loader must
     * recognise the extension and treat the file as PKCS12.
     */
    @Test
    public void autoDetectsTypeFromAlternatePkcs12Extension() throws Exception {
        File ks =
                CertificateTestHelper.createKeystoreFile(
                        "PKCS12", ".pfx", keyPair.getPrivate(), cert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        ks.getAbsolutePath(), CertificateTestHelper.PASSWORD_STRING, null);

        MtlsKeystore mtls = MtlsKeystore.from(config);

        assertTrue(mtls.getKeystoreType().equalsIgnoreCase("PKCS12"));
    }

    /**
     * When an explicit keystore type is set in the Keycloak config, it takes priority over the file
     * extension, allowing operators to use any filename regardless of extension.
     */
    @Test
    public void configuredTypeOverridesExtension() throws Exception {
        File ks =
                CertificateTestHelper.createKeystoreFile(
                        "PKCS12", ".store", keyPair.getPrivate(), cert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        ks.getAbsolutePath(), CertificateTestHelper.PASSWORD_STRING, "PKCS12");

        MtlsKeystore mtls = MtlsKeystore.from(config);

        assertEquals("PKCS12", mtls.getKeystoreType());
    }

    /**
     * No configured keystore type and an unrecognized extension: the loader falls back to the JVM
     * default keystore type, which is PKCS12 in a standard (non-FIPS) JVM.
     */
    @Test
    public void defaultsToPkcs12WhenExtensionUnknown() throws Exception {
        File ks =
                CertificateTestHelper.createKeystoreFile(
                        "PKCS12", ".store", keyPair.getPrivate(), cert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        ks.getAbsolutePath(), CertificateTestHelper.PASSWORD_STRING, null);

        MtlsKeystore mtls = MtlsKeystore.from(config);

        assertTrue(mtls.getKeystoreType().equalsIgnoreCase("PKCS12"));
    }

    /**
     * Keycloak's fips-mode=strict sets the JVM default keystore type to BCFKS. Simulates that by
     * overriding the security property, then asserts the loader resolves the default to BCFKS.
     */
    @Test
    public void defaultsToBcfksUnderFipsStrict() throws Exception {
        String previous = Security.getProperty("keystore.type");
        try {
            Security.setProperty("keystore.type", "BCFKS");
            File ks =
                    CertificateTestHelper.createKeystoreFile(
                            "BCFKS", ".store", keyPair.getPrivate(), cert);
            Config.Scope config =
                    MtlsTestMocks.keycloakConfigScope(
                            ks.getAbsolutePath(), CertificateTestHelper.PASSWORD_STRING, null);

            MtlsKeystore mtls = MtlsKeystore.from(config);

            assertTrue(mtls.getKeystoreType().equalsIgnoreCase("BCFKS"));
        } finally {
            Security.setProperty("keystore.type", previous == null ? "pkcs12" : previous);
        }
    }

    @Test
    public void returnsNullForBlankKeyStorePath() {
        Config.Scope config = MtlsTestMocks.keycloakConfigScope("   ", null, null);

        assertNull(MtlsKeystore.from(config));
    }

    @Test
    public void throwsWhenKeystoreFileDoesNotExist() {
        String password = "topsecret";
        String missing =
                new File(System.getProperty("java.io.tmpdir"), "does-not-exist-12345.p12")
                        .getAbsolutePath();
        Config.Scope config = MtlsTestMocks.keycloakConfigScope(missing, password, "PKCS12");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> MtlsKeystore.from(config));

        assertTrue(ex.getMessage().contains(missing));
        assertTrue(ex.getMessage().contains(MtlsKeystore.CONFIG_KEY_STORE));
        assertNoPasswordInErrorMessage(ex, password);
    }

    @Test
    public void throwsWhenKeystorePasswordIsWrong() throws Exception {
        String wrongPassword = "definitely-wrong-pw";
        File ks =
                CertificateTestHelper.createKeystoreFile(
                        "PKCS12", ".p12", keyPair.getPrivate(), cert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(ks.getAbsolutePath(), wrongPassword, "PKCS12");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> MtlsKeystore.from(config));

        assertTrue(ex.getMessage().contains(MtlsKeystore.CONFIG_KEY_STORE_PASSWORD));
        assertNoPasswordInErrorMessage(ex, wrongPassword);
    }

    /**
     * A valid keystore that contains no private key entry should fail fast rather than silently
     * presenting no certificate during the TLS handshake.
     */
    @Test
    public void throwsWhenKeystoreHasNoPrivateKeyEntry() throws Exception {
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);
        File ks = CertificateTestHelper.store(empty, ".p12");
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        ks.getAbsolutePath(), CertificateTestHelper.PASSWORD_STRING, "PKCS12");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> MtlsKeystore.from(config));

        assertTrue(ex.getMessage().contains("private key"));
    }

    /**
     * A bogus keystore type that the JCA does not recognise should fail fast with an error message
     * that names the invalid type, so the operator knows exactly what to correct.
     */
    @Test
    public void unsupportedTypeFailsFast() throws Exception {
        File ks =
                CertificateTestHelper.createKeystoreFile(
                        "PKCS12", ".store", keyPair.getPrivate(), cert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        ks.getAbsolutePath(),
                        CertificateTestHelper.PASSWORD_STRING,
                        "NOT_A_REAL_TYPE");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> MtlsKeystore.from(config));

        assertTrue(ex.getMessage().contains("NOT_A_REAL_TYPE"));
    }

    /** Ensures the given password value does not leak into the message or its cause chain. */
    private static void assertNoPasswordInErrorMessage(Throwable t, String password) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String message = cur.getMessage();
            if (message != null) {
                assertFalse(message.contains(password));
            }
        }
    }
}
