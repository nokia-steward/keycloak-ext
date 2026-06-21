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
import static org.junit.Assert.assertTrue;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.keycloak.models.KeycloakSession;

/**
 * Smoke test for the real email send path when mTLS is disabled.
 *
 * <p>The test starts an in-memory GreenMail SMTP server, sends an email through {@link
 * MtlsEmailSenderProvider} with no mTLS keystore configured, and verifies that GreenMail receives
 * the expected message. This proves the extension still supports normal non-mTLS email delivery.
 */
public class MtlsEmailProviderSendTest {

    private static final String FROM = "noreply@example.com";

    private final KeycloakSession session = MtlsTestMocks.sessionWithoutTruststore();
    private final Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator> authenticators =
            MtlsTestMocks.emailAuthenticators();

    @Test
    public void deliversEmailThroughSmtpLikeDefault() throws Exception {
        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("host", "127.0.0.1");
            config.put("port", String.valueOf(greenMail.getSmtp().getPort()));
            config.put("from", FROM);

            mtlsProviderWithoutKeystore()
                    .send(
                            config,
                            "user@example.com",
                            "Send subject",
                            "Plain text body",
                            "<p>HTML body</p>");

            assertTrue(greenMail.waitForIncomingEmail(10_000, 1));
            MimeMessage[] received = greenMail.getReceivedMessages();
            assertEquals(1, received.length);
            assertEquals("Send subject", received[0].getSubject());
            assertTrue(received[0].getAllRecipients()[0].toString().contains("user@example.com"));
            assertTrue(received[0].getFrom()[0].toString().contains(FROM));
        } finally {
            greenMail.stop();
        }
    }

    private MtlsEmailSenderProvider mtlsProviderWithoutKeystore() {
        return new MtlsEmailSenderProvider(session, authenticators, null);
    }
}
