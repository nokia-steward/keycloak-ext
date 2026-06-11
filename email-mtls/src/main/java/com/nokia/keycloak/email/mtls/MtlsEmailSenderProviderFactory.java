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

import org.keycloak.Config;
import org.keycloak.email.DefaultEmailSenderProviderFactory;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.email.MtlsEmailSenderProvider;
import org.keycloak.models.KeycloakSession;

/**
 * Factory for the {@code email-mtls} email sender provider.
 *
 * <p>It extends Keycloak's {@link DefaultEmailSenderProviderFactory} so the built-in
 * SMTP authenticators (NONE / BASIC password / TOKEN XOAUTH2), and any future ones,
 * are reused verbatim via the public {@code getEmailAuthenticators()} accessor. The
 * only additions are: reading the optional mTLS keystore configuration, creating the
 * mTLS-capable provider, and returning the {@code email-mtls} provider id.</p>
 */
public class MtlsEmailSenderProviderFactory extends DefaultEmailSenderProviderFactory {

    /** The provider id selected via {@code --spi-email-sender--provider=email-mtls}. */
    public static final String PROVIDER_ID = "email-mtls";

    private volatile MtlsClientKeystore mtls = MtlsClientKeystore.disabled();

    @Override
    public void init(Config.Scope config) {
        // Registers the email authenticators (NONE/BASIC/TOKEN) on the superclass.
        super.init(config);

        // The keystore options are runtime options (read at provider startup), so the
        // password is never persisted into Keycloak's plaintext build state. Loading
        // here also makes startup fail fast on invalid keystore configuration.
        this.mtls = MtlsClientKeystore.from(config);
    }

    @Override
    public EmailSenderProvider create(KeycloakSession session) {
        return new MtlsEmailSenderProvider(session, getEmailAuthenticators(), mtls);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
