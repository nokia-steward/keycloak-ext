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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;

import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.Config;

/**
 * Unit tests for {@link MtlsClientKeystore}: keystore loading for PKCS12/JKS/BCFKS,
 * type auto-detection from the file extension, the FIPS (BCFKS) default, fail-fast
 * error handling with secret-free messages, and the "no keystore -> disabled" path.
 */
public class MtlsClientKeystoreTest {

    private static KeyPair keyPair;
    private static X509Certificate cert;

    @BeforeClass
    public static void generateKeyMaterial() throws Exception {
        CertificateTestSupport.ensureBouncyCastle();
        keyPair = CertificateTestSupport.newKeyPair();
        cert = CertificateTestSupport.selfSigned(keyPair, "client");
    }

    @Test
    public void loadsPkcs12Keystore() throws Exception {
        File ks = CertificateTestSupport.writeKeyStore("PKCS12", ".p12", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, null);

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.isEnabled(), is(true));
        assertThat(mtls.getResolvedType(), equalToIgnoringCase("PKCS12"));
        assertThat(mtls.createSocketFactory(MtlsTestMocks.sessionWithoutTruststore()), is(notNullValue()));
    }

    @Test
    public void loadsJksKeystore() throws Exception {
        File ks = CertificateTestSupport.writeKeyStore("JKS", ".jks", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, null);

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.isEnabled(), is(true));
        assertThat(mtls.getResolvedType(), equalToIgnoringCase("JKS"));
    }

    @Test
    public void loadsBcfksKeystore() throws Exception {
        File ks = CertificateTestSupport.writeKeyStore("BCFKS", ".bcfks", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, null);

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.isEnabled(), is(true));
        assertThat(mtls.getResolvedType(), equalToIgnoringCase("BCFKS"));
    }

    @Test
    public void autoDetectsTypeFromAlternatePkcs12Extension() throws Exception {
        // A PKCS12 keystore named ".pfx" must auto-detect as PKCS12 with no configured type.
        File ks = CertificateTestSupport.writeKeyStore("PKCS12", ".pfx", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, null);

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.getResolvedType(), equalToIgnoringCase("PKCS12"));
    }

    @Test
    public void configuredTypeOverridesExtension() throws Exception {
        // File content is PKCS12; extension is unrelated; configured type wins.
        File ks = CertificateTestSupport.writeKeyStore("PKCS12", ".store", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, "PKCS12");

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.getResolvedType(), is("PKCS12"));
    }

    @Test
    public void defaultsToPkcs12WhenExtensionUnknown() throws Exception {
        // No configured type and an unrecognized extension -> JVM default keystore type (pkcs12).
        File ks = CertificateTestSupport.writeKeyStore("PKCS12", ".store", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, null);

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.getResolvedType(), equalToIgnoringCase("PKCS12"));
    }

    @Test
    public void defaultsToBcfksUnderFipsStrict() throws Exception {
        // Keycloak's fips-mode=strict sets the JVM default keystore type to BCFKS. Simulate
        // that by overriding the security property, then assert the default resolves to BCFKS.
        String previous = Security.getProperty("keystore.type");
        try {
            Security.setProperty("keystore.type", "BCFKS");
            File ks = CertificateTestSupport.writeKeyStore("BCFKS", ".store", keyPair.getPrivate(), cert);
            Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                    CertificateTestSupport.PASSWORD_STRING, null);

            MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

            assertThat(mtls.getResolvedType(), equalToIgnoringCase("BCFKS"));
        } finally {
            Security.setProperty("keystore.type", previous == null ? "pkcs12" : previous);
        }
    }

    @Test
    public void blankKeyStoreYieldsDisabledInstance() {
        Config.Scope config = MtlsTestMocks.configScope("   ", null, null);

        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        assertThat(mtls.isEnabled(), is(false));
        assertThrows(IllegalStateException.class,
                () -> mtls.createSocketFactory(MtlsTestMocks.sessionWithoutTruststore()));
    }

    @Test
    public void missingFileFailsFastWithoutSecrets() {
        String missing = new File(System.getProperty("java.io.tmpdir"), "does-not-exist-12345.p12")
                .getAbsolutePath();
        Config.Scope config = MtlsTestMocks.configScope(missing, "topsecret", "PKCS12");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MtlsClientKeystore.from(config));

        assertThat(ex.getMessage(), containsString(missing));
        assertThat(ex.getMessage(), containsString(MtlsClientKeystore.CONFIG_KEY_STORE));
        assertNoSecret(ex);
    }

    @Test
    public void wrongPasswordFailsFastWithoutSecrets() throws Exception {
        File ks = CertificateTestSupport.writeKeyStore("PKCS12", ".p12", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(), "definitely-wrong-pw", "PKCS12");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MtlsClientKeystore.from(config));

        assertThat(ex.getMessage(), containsString(MtlsClientKeystore.CONFIG_KEY_STORE_PASSWORD));
        assertNoSecret(ex);
    }

    @Test
    public void unsupportedTypeFailsFast() throws Exception {
        File ks = CertificateTestSupport.writeKeyStore("PKCS12", ".store", keyPair.getPrivate(), cert);
        Config.Scope config = MtlsTestMocks.configScope(ks.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, "NOT_A_REAL_TYPE");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MtlsClientKeystore.from(config));

        assertThat(ex.getMessage(), containsString("NOT_A_REAL_TYPE"));
    }

    /** Ensures no configured password value leaks into the message or its cause chain. */
    private static void assertNoSecret(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String message = cur.getMessage();
            if (message != null) {
                assertThat(message, not(containsString("topsecret")));
                assertThat(message, not(containsString("definitely-wrong-pw")));
                assertThat(message, not(containsString(CertificateTestSupport.PASSWORD_STRING)));
            }
        }
    }
}
