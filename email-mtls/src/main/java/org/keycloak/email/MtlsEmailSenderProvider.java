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

import com.nokia.keycloak.email.mtls.MtlsClientKeystore;

/**
 * An {@link EmailSenderProvider} that behaves exactly like Keycloak's built-in
 * {@link DefaultEmailSenderProvider} but, when an mTLS client keystore is
 * configured, presents a client certificate on the SMTP TLS connection. This
 * works for both STARTTLS (typically port 587) and implicit TLS / SMTPS
 * (typically port 465).
 *
 * <p><strong>Why this class is in package {@code org.keycloak.email}.</strong>
 * The only seam in {@link DefaultEmailSenderProvider} for injecting an
 * {@code SSLSocketFactory} into the Jakarta Mail {@code Session} is the
 * <em>package-private</em> method
 * {@link DefaultEmailSenderProvider#buildEmailProperties(Map, String)}. A
 * package-private method can only be overridden from the same package, so this
 * subclass deliberately lives here. Everything mTLS-specific lives in the
 * {@code com.nokia.keycloak.email.mtls} package. Keeping the override this small
 * honors the minimal-override maintenance principle: future changes to the
 * default provider's SMTP/message handling are absorbed automatically.</p>
 */
public class MtlsEmailSenderProvider extends DefaultEmailSenderProvider {

    private final KeycloakSession session;
    private final MtlsClientKeystore mtls;

    /**
     * @param session       the current Keycloak session
     * @param authenticators the SMTP authenticators reused from the default factory
     * @param mtls          the loaded mTLS client keystore; may be a disabled instance,
     *                      in which case this provider behaves exactly like the default
     */
    public MtlsEmailSenderProvider(KeycloakSession session,
                                   Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator> authenticators,
                                   MtlsClientKeystore mtls) {
        super(session, authenticators);
        this.session = session;
        this.mtls = mtls;
    }

    /**
     * Delegates to the default implementation and, only when mTLS is configured and
     * TLS is actually in use, replaces Keycloak's truststore-only SSL socket factory
     * with one that additionally presents the client certificate. Server-certificate
     * validation continues to use Keycloak's standard truststore (reused inside
     * {@link MtlsClientKeystore#createSocketFactory(KeycloakSession)}).
     */
    @Override
    Properties buildEmailProperties(Map<String, String> config, String from) throws EmailException {
        Properties props = super.buildEmailProperties(config, from);

        // A client certificate can only be presented once a TLS handshake happens,
        // i.e. with implicit SMTPS (ssl) or with STARTTLS.
        boolean tlsEnabled = "true".equals(config.get("ssl")) || "true".equals(config.get("starttls"));

        if (mtls != null && mtls.isEnabled() && tlsEnabled) {
            props.put("mail.smtp.ssl.socketFactory", mtls.createSocketFactory(session));
        }

        return props;
    }
}
