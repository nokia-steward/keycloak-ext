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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.util.Enumeration;
import java.util.Locale;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.util.KeystoreUtil;
import org.keycloak.models.KeycloakSession;
import org.keycloak.truststore.JSSETruststoreConfigurator;

/**
 * Loader and immutable holder for the mTLS client keystore. Keystore is loaded at SPI
 * initialization based on the provided SPI configuration.
 *
 * <p>Builds an {@link SSLSocketFactory} on demand (when communication with SMTP server is
 * established) by combining the client key managers with Keycloak's server truststore.
 */
public final class MtlsKeystore {

    // SPI config key for the keystore file path
    public static final String CONFIG_KEY_STORE = "key-store";
    // SPI config key for the keystore password
    public static final String CONFIG_KEY_STORE_PASSWORD = "key-store-password";
    // SPI config key for the keystore type (PKCS12 / JKS / BCFKS)
    public static final String CONFIG_KEY_STORE_TYPE = "key-store-type";

    private static final Logger LOG = Logger.getLogger(MtlsKeystore.class);

    private final KeyManager[] keyManagers;
    private final String keystoreType;

    private MtlsKeystore(KeyManager[] keyManagers, String keystoreType) {
        this.keyManagers = keyManagers;
        this.keystoreType = keystoreType;
    }

    /** Returns the resolved keystore type. */
    public String getKeystoreType() {
        return keystoreType;
    }

    /**
     * Loads the keystore from SPI configuration.
     *
     * <p>Returns {@code null} when no keystore is provided by SPI configuration, which means the
     * email sender should behave like the default provider.
     *
     * <p>Throws an unchecked {@link IllegalStateException} when a keystore is configured but cannot
     * be loaded due to invalid path, password, or type.
     */
    public static MtlsKeystore from(Config.Scope config) {
        String path = trimToNull(config.get(CONFIG_KEY_STORE));
        if (path == null) {
            LOG.debug(
                    "No mTLS key-store configured. Email sender behaves like the default provider.");
            return null;
        }

        String keystorePassword = config.get(CONFIG_KEY_STORE_PASSWORD);
        String keystoreType =
                KeystoreUtil.getKeystoreType(
                        trimToNull(config.get(CONFIG_KEY_STORE_TYPE)),
                        path,
                        KeyStore.getDefaultType());

        KeyManager[] kms = loadKeyManagers(path, keystoreType, keystorePassword);
        LOG.info("mTLS client keystore loaded for SMTP connection");
        return new MtlsKeystore(kms, keystoreType);
    }

    private static KeyManager[] loadKeyManagers(
            String path, String keystoreType, String keystorePassword) {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "mTLS keystore not found (or not a regular file) at the configured '"
                            + CONFIG_KEY_STORE
                            + "' path: "
                            + path);
        }

        KeyStore keyStore;
        try {
            keyStore = newKeyStore(keystoreType);
        } catch (KeyStoreException | NoSuchProviderException e) {
            throw new IllegalStateException(
                    "Unsupported mTLS keystore type '"
                            + keystoreType
                            + "'. Only PKCS12, JKS, or BCFKS are supported.",
                    e);
        }

        char[] pw = keystorePassword == null ? null : keystorePassword.toCharArray();
        try (InputStream in = Files.newInputStream(file)) {
            keyStore.load(in, pw);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load the mTLS keystore at '"
                            + path
                            + "' as type '"
                            + keystoreType
                            + "'. Verify '"
                            + CONFIG_KEY_STORE_PASSWORD
                            + "' and that the file is a valid keystore.",
                    e);
        }

        try {
            boolean hasKeyEntry = false;
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements() && !hasKeyEntry) {
                hasKeyEntry = keyStore.isKeyEntry(aliases.nextElement());
            }
            if (!hasKeyEntry) {
                throw new IllegalStateException(
                        "The mTLS keystore at '" + path + "' contains no private key entry");
            }
        } catch (KeyStoreException e) {
            throw new IllegalStateException(
                    "Failed to inspect entries in the mTLS keystore at '" + path + "'.", e);
        }

        try {
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, pw);
            return kmf.getKeyManagers();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize the client key manager from the mTLS keystore at '"
                            + path
                            + "'. Ensure the keystore contains a private key entry that is unlocked by the correct password.",
                    e);
        }
    }

    /**
     * Prefers Keycloak's {@link CryptoIntegration} when initialised (correct BCFKS/FIPS provider).
     * Falls back to standard JCA otherwise (e.g. in unit tests).
     */
    private static KeyStore newKeyStore(String type)
            throws KeyStoreException, NoSuchProviderException {
        if (CryptoIntegration.isInitialised()) {
            try {
                KeystoreUtil.KeystoreFormat format =
                        KeystoreUtil.KeystoreFormat.valueOf(type.toUpperCase(Locale.ROOT));
                return CryptoIntegration.getProvider().getKeyStore(format);
            } catch (IllegalArgumentException notAKnownFormat) {
                // fall through to standard JCA
            }
        }
        return KeyStore.getInstance(type);
    }

    /**
     * Combines the loaded {@code KeyManagers} (client cert) with Keycloak's truststore {@code
     * TrustManagers} (server cert validation) into an {@link SSLContext}. Falls back to JVM default
     * trust when no Keycloak truststore is configured.
     */
    public SSLSocketFactory createSocketFactory(KeycloakSession session) {
        try {
            TrustManager[] trustManagers =
                    new JSSETruststoreConfigurator(session).getTrustManagers();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to build the mTLS SSL socket factory for SMTP.", e);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
