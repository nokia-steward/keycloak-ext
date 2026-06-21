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

import java.util.Map;
import java.util.Properties;
import org.keycloak.models.KeycloakSession;

/**
 * Extends Keycloak {@link DefaultEmailSenderProvider} to inject a client certificate on the SMTP
 * TLS connection. Only {@code buildEmailProperties} is overridden. All other send/auth logic is
 * unchanged.
 */
public class MtlsEmailSenderProvider extends DefaultEmailSenderProvider {

    private final KeycloakSession session;
    private final MtlsKeystore mtlsKeystore;

    public MtlsEmailSenderProvider(
            KeycloakSession session,
            Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator> authenticators,
            MtlsKeystore mtlsKeystore) {
        super(session, authenticators);
        this.session = session;
        this.mtlsKeystore = mtlsKeystore;
    }

    /**
     * Builds the Jakarta Mail properties from Keycloak's SMTP configuration map, for example {@code
     * host -> mail.smtp.host} and {@code ssl -> mail.smtp.ssl.enable}.
     *
     * <p>Overrides Keycloak default provider method only to additinally inject the mTLS socket
     * factory for SMTP when TLS is active and an mTLS keystore is present.
     */
    @Override
    Properties buildEmailProperties(Map<String, String> config, String from) throws EmailException {
        Properties props = super.buildEmailProperties(config, from);

        boolean tlsEnabled =
                "true".equals(config.get("ssl")) || "true".equals(config.get("starttls"));
        if (mtlsKeystore != null && tlsEnabled) {
            props.put("mail.smtp.ssl.socketFactory", mtlsKeystore.createSocketFactory(session));
        }

        return props;
    }
}
