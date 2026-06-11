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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
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
 * Holds the optional mTLS client keystore. The keystore is loaded once at provider
 * startup (factory {@code init}) and, when configured, is used to build the
 * {@link SSLSocketFactory} that presents the client certificate on the SMTP TLS
 * connection.
 *
 * <p>Configuration keys, read from the {@code email-mtls} email-sender SPI scope:</p>
 * <ul>
 *   <li>{@code key-store} - path to the keystore file (absence disables mTLS)</li>
 *   <li>{@code key-store-password} - keystore password</li>
 *   <li>{@code key-store-type} - {@code PKCS12} | {@code JKS} | {@code BCFKS}
 *       (optional; auto-detected from the file extension, otherwise the JVM default
 *       which is PKCS12, or BCFKS when Keycloak runs in {@code fips-mode=strict})</li>
 * </ul>
 *
 * <p>Security: the password is never logged and never included in error messages.
 * Keystore configuration problems fail fast with a clear, actionable message.</p>
 */
public final class MtlsClientKeystore {

    /** Config property name (segment after {@code spi-email-sender--email-mtls--}). */
    public static final String CONFIG_KEY_STORE = "key-store";
    /** Config property name for the keystore password. */
    public static final String CONFIG_KEY_STORE_PASSWORD = "key-store-password";
    /** Config property name for the keystore type. */
    public static final String CONFIG_KEY_STORE_TYPE = "key-store-type";

    private static final Logger LOG = Logger.getLogger(MtlsClientKeystore.class);

    private final boolean enabled;
    private final KeyManager[] keyManagers;
    private final String resolvedType;

    private MtlsClientKeystore(boolean enabled, KeyManager[] keyManagers, String resolvedType) {
        this.enabled = enabled;
        this.keyManagers = keyManagers;
        this.resolvedType = resolvedType;
    }

    /** @return a disabled instance; the provider then behaves exactly like the default. */
    public static MtlsClientKeystore disabled() {
        return new MtlsClientKeystore(false, null, null);
    }

    /** @return {@code true} if a client keystore is configured and loaded. */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return the resolved keystore type (for diagnostics), or {@code null} when disabled. */
    public String getResolvedType() {
        return resolvedType;
    }

    /**
     * Builds an instance from the SPI configuration scope. If no {@code key-store} path
     * is configured, returns a {@linkplain #disabled() disabled} instance. Otherwise the
     * keystore is loaded eagerly and this method fails fast (throwing
     * {@link IllegalStateException} with a secret-free message) if the path, password, or
     * type is invalid.
     *
     * @param config the {@code email-mtls} email-sender SPI configuration scope
     * @return a configured (enabled) or disabled instance
     */
    public static MtlsClientKeystore from(Config.Scope config) {
        String path = trimToNull(config.get(CONFIG_KEY_STORE));
        if (path == null) {
            LOG.debug("No mTLS key-store configured; email sender behaves like the default provider.");
            return disabled();
        }

        String password = config.get(CONFIG_KEY_STORE_PASSWORD); // may be null; never logged
        String configuredType = trimToNull(config.get(CONFIG_KEY_STORE_TYPE));

        // Mirror Keycloak's own resolution: configured type wins, then the file
        // extension, then the JVM default keystore type (pkcs12 normally, BCFKS when
        // Keycloak runs in fips-mode=strict and sets the default keystore type).
        String type = KeystoreUtil.getKeystoreType(configuredType, path, KeyStore.getDefaultType());

        KeyManager[] kms = loadKeyManagers(path, type, password);
        LOG.infof("mTLS client keystore loaded for SMTP (type=%s); a client certificate "
                + "will be presented on TLS connections.", type);
        return new MtlsClientKeystore(true, kms, type);
    }

    private static KeyManager[] loadKeyManagers(String path, String type, String password) {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "mTLS keystore not found (or not a regular file) at the configured '"
                            + CONFIG_KEY_STORE + "' path: " + path);
        }

        KeyStore keyStore;
        try {
            keyStore = newKeyStore(type);
        } catch (KeyStoreException | NoSuchProviderException e) {
            throw new IllegalStateException(
                    "Unsupported mTLS keystore type '" + type + "'. Set '" + CONFIG_KEY_STORE_TYPE
                            + "' to one of PKCS12, JKS, or BCFKS (BCFKS requires the BouncyCastle "
                            + "provider that Keycloak ships).", e);
        }

        char[] pw = password == null ? null : password.toCharArray();
        try (InputStream in = Files.newInputStream(file)) {
            keyStore.load(in, pw);
        } catch (IOException e) {
            // A wrong password surfaces here (typically wrapped UnrecoverableKeyException).
            throw new IllegalStateException(
                    "Failed to open the mTLS keystore at '" + path + "' as type '" + type + "'. Verify '"
                            + CONFIG_KEY_STORE_PASSWORD + "' and '" + CONFIG_KEY_STORE_TYPE
                            + "', and that the file is a valid keystore.", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load the mTLS keystore at '" + path + "' as type '" + type + "'.", e);
        }

        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, pw);
            return kmf.getKeyManagers();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize the client key manager from the mTLS keystore at '" + path
                            + "'. Ensure the keystore contains a private key entry that is unlocked by '"
                            + CONFIG_KEY_STORE_PASSWORD + "'.", e);
        }
    }

    /**
     * Creates a {@link KeyStore} instance for the given type. When Keycloak's crypto
     * provider is initialised it is preferred (correctly selecting BouncyCastle/BCFIPS
     * for BCFKS in both FIPS and non-FIPS modes); otherwise the standard JCA lookup is
     * used (e.g. in plain unit tests).
     */
    private static KeyStore newKeyStore(String type) throws KeyStoreException, NoSuchProviderException {
        if (CryptoIntegration.isInitialised()) {
            try {
                KeystoreUtil.KeystoreFormat format =
                        KeystoreUtil.KeystoreFormat.valueOf(type.toUpperCase(Locale.ROOT));
                return CryptoIntegration.getProvider().getKeyStore(format);
            } catch (IllegalArgumentException notAKnownFormat) {
                // Not one of the KeystoreFormat enum values; fall back to standard JCA below.
            }
        }
        return KeyStore.getInstance(type);
    }

    /**
     * Builds an {@link SSLSocketFactory} that presents the client certificate and
     * validates the server using Keycloak's standard truststore. The truststore is
     * reused (never re-implemented) via {@link JSSETruststoreConfigurator}; when no
     * Keycloak truststore is configured the JVM default trust is used, matching
     * {@code DefaultEmailSenderProvider} behavior.
     *
     * @param session the current Keycloak session (used only to read the truststore)
     * @return the combined client-auth + server-trust socket factory
     * @throws IllegalStateException if this keystore is disabled or the factory cannot be built
     */
    public SSLSocketFactory createSocketFactory(KeycloakSession session) {
        if (!enabled) {
            throw new IllegalStateException("mTLS client keystore is not enabled");
        }
        try {
            TrustManager[] trustManagers = new JSSETruststoreConfigurator(session).getTrustManagers();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build the mTLS SSL socket factory for SMTP.", e);
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
