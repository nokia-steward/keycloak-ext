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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.mail.internet.MimeMessage;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.nokia.keycloak.email.mtls.MtlsClientKeystore;
import com.nokia.keycloak.email.mtls.MtlsTestMocks;

import org.junit.Test;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

/**
 * Proves behavior parity with {@link DefaultEmailSenderProvider} when no mTLS keystore
 * is configured (requirements section 18.1):
 * <ul>
 *   <li>{@link DefaultEmailSenderProvider#buildEmailProperties(Map, String)} produces
 *       identical SMTP properties for the same configuration; and</li>
 *   <li>a real message is delivered through an in-memory SMTP server (GreenMail).</li>
 * </ul>
 * This test lives in {@code org.keycloak.email} so it can call the package-private
 * {@code buildEmailProperties} on both providers.
 */
public class MtlsEmailSenderProviderParityTest {

    private static final String FROM = "noreply@example.com";

    private final KeycloakSession session = MtlsTestMocks.sessionWithoutTruststore();
    private final Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator> authenticators = authenticators();

    private static Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator> authenticators() {
        DefaultEmailSenderProviderFactory factory = new DefaultEmailSenderProviderFactory();
        factory.init(mock(Config.Scope.class));
        return factory.getEmailAuthenticators();
    }

    private DefaultEmailSenderProvider defaultProvider() {
        return new DefaultEmailSenderProvider(session, authenticators);
    }

    private MtlsEmailSenderProvider mtlsProviderWithoutKeystore() {
        return new MtlsEmailSenderProvider(session, authenticators, MtlsClientKeystore.disabled());
    }

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

    @Test
    public void disabledMtlsAddsNoSocketFactoryEvenWithTls() throws Exception {
        Map<String, String> config = plain();
        config.put("ssl", "true");

        Properties props = mtlsProviderWithoutKeystore().buildEmailProperties(config, FROM);

        assertThat(props.containsKey("mail.smtp.ssl.socketFactory"), is(false));
    }

    @Test
    public void deliversEmailThroughSmtpLikeDefault() throws Exception {
        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("host", "127.0.0.1");
            config.put("port", String.valueOf(greenMail.getSmtp().getPort()));
            config.put("from", FROM);

            mtlsProviderWithoutKeystore().send(config, "user@example.com",
                    "Parity subject", "Plain text body", "<p>HTML body</p>");

            assertThat(greenMail.waitForIncomingEmail(10_000, 1), is(true));
            MimeMessage[] received = greenMail.getReceivedMessages();
            assertEquals(1, received.length);
            assertEquals("Parity subject", received[0].getSubject());
            assertThat(received[0].getAllRecipients()[0].toString(), containsString("user@example.com"));
            assertThat(received[0].getFrom()[0].toString(), containsString(FROM));
        } finally {
            greenMail.stop();
        }
    }

    private void assertPropertiesParity(Map<String, String> config) throws EmailException {
        Properties expected = defaultProvider().buildEmailProperties(config, FROM);
        Properties actual = mtlsProviderWithoutKeystore().buildEmailProperties(config, FROM);
        assertEquals("SMTP properties must match the default provider exactly", expected, actual);
        // Sanity: with mTLS disabled there must be no client-cert socket factory.
        assertThat(actual.containsKey("mail.smtp.ssl.socketFactory"), is(not(true)));
    }

    private static Map<String, String> plain() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("host", "smtp.example.com");
        config.put("port", "587");
        config.put("from", FROM);
        return config;
    }
}
