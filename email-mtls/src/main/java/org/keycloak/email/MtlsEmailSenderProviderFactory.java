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

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

/** SPI factory for the email mTLS enabled provider */
public class MtlsEmailSenderProviderFactory extends DefaultEmailSenderProviderFactory {

    public static final String PROVIDER_ID = "email-mtls";

    private MtlsKeystore mtlsKeystore;

    @Override
    public void init(Config.Scope config) {
        super.init(config);
        // On any errors of loading the mTLS keystore, from() throws an
        // unchecked IllegalStateException that prevents Keycloak startup
        this.mtlsKeystore = MtlsKeystore.from(config);
    }

    @Override
    public EmailSenderProvider create(KeycloakSession session) {
        return new MtlsEmailSenderProvider(session, getEmailAuthenticators(), mtlsKeystore);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
