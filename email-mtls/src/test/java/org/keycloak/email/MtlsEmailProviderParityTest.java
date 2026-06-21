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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import org.keycloak.models.KeycloakSession;

/**
 * Proves backward compatibility and behavior parity with Keycloak's built-in {@link
 * DefaultEmailSenderProvider} when no mTLS keystore is configured.
 *
 * <p>Parity Strategy: It feeds the exact same Keycloak configuration into both providers and
 * asserts that they build the exact same Jakarta Mail {@link Properties} object content. Identical
 * properties guarantee that Jakarta Mail receives the same SMTP configuration and behaves the same
 * for both providers.
 *
 * <p>This guarantees that replacing the default provider won't break standard email functionality
 * for Realms that do not require mTLS.
 */
public class MtlsEmailProviderParityTest {

    private static final String FROM = "noreply@example.com";

    private final KeycloakSession session = MtlsTestMocks.sessionWithoutTruststore();
    private final Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator> authenticators =
            MtlsTestMocks.emailAuthenticators();

    @Test
    public void emailPropertiesMatchDefaultForPlainConfig() throws Exception {
        assertPropertiesParity(plain());
    }

    @Test
    public void emailPropertiesMatchDefaultForSsl() throws Exception {
        Map<String, String> config = plain();
        config.put("ssl", "true");
        assertPropertiesParity(config);
    }

    @Test
    public void emailPropertiesMatchDefaultForStarttls() throws Exception {
        Map<String, String> config = plain();
        config.put("starttls", "true");
        assertPropertiesParity(config);
    }

    @Test
    public void emailPropertiesMatchDefaultForBasicAuth() throws Exception {
        Map<String, String> config = plain();
        config.put("starttls", "true");
        config.put("auth", "true");
        config.put("authType", "basic");
        config.put("user", "smtp-user");
        config.put("password", "smtp-pass");
        assertPropertiesParity(config);
    }

    @Test
    public void emailPropertiesMatchDefaultForTokenAuth() throws Exception {
        Map<String, String> config = plain();
        config.put("ssl", "true");
        config.put("auth", "true");
        config.put("authType", "token");
        config.put("user", "smtp-user");
        config.put("password", "smtp-token");
        assertPropertiesParity(config);
    }

    @Test
    public void emailPropertiesMatchDefaultForExtendedConfig() throws Exception {
        Map<String, String> config = plain();
        config.put("starttls", "true");
        config.put(EmailSenderProvider.CONFIG_ALLOW_UTF8, "true");
        config.put("replyTo", "reply@example.com");
        config.put("replyToDisplayName", "Reply Desk");
        config.put("fromDisplayName", "Example");
        config.put("envelopeFrom", "bounce@example.com");
        config.put("timeout", "20000");
        config.put("connectionTimeout", "15000");
        config.put("writeTimeout", "25000");
        assertPropertiesParity(config);
    }

    private DefaultEmailSenderProvider defaultKeycloakEmailSenderProvider() {
        return new DefaultEmailSenderProvider(session, authenticators);
    }

    private MtlsEmailSenderProvider mtlsProviderWithoutKeystore() {
        return new MtlsEmailSenderProvider(session, authenticators, null);
    }

    private void assertPropertiesParity(Map<String, String> config) throws EmailException {
        Properties expected =
                defaultKeycloakEmailSenderProvider().buildEmailProperties(config, FROM);
        Properties actual = mtlsProviderWithoutKeystore().buildEmailProperties(config, FROM);
        assertEquals("SMTP properties must match the default provider exactly", expected, actual);
        // Sanity: with mTLS disabled there must be no client-cert socket factory.
        assertFalse(actual.containsKey("mail.smtp.ssl.socketFactory"));
    }

    private static Map<String, String> plain() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("host", "smtp.example.com");
        config.put("port", "587");
        config.put("from", FROM);
        return config;
    }
}
