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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyStore;
import java.util.Map;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.truststore.TruststoreProvider;
import org.keycloak.truststore.TruststoreProviderFactory;

/**
 * Lightweight Mockito stubs shared by the test classes. Keycloak itself uses Mockito, so this stays
 * within the "tools Keycloak uses" testing constraint.
 */
public final class MtlsTestMocks {

    private MtlsTestMocks() {}

    /** Returns a map of email authenticators initialized from the default provider factory. */
    public static Map<EmailAuthenticator.AuthenticatorType, EmailAuthenticator>
            emailAuthenticators() {
        DefaultEmailSenderProviderFactory factory = new DefaultEmailSenderProviderFactory();
        factory.init(mock(Config.Scope.class));
        return factory.getEmailAuthenticators();
    }

    /** A {@link Config.Scope} returning the given mTLS keystore options */
    public static Config.Scope keycloakConfigScope(String keyStore, String password, String type) {
        Config.Scope scope = mock(Config.Scope.class);
        when(scope.get(MtlsKeystore.CONFIG_KEY_STORE)).thenReturn(keyStore);
        when(scope.get(MtlsKeystore.CONFIG_KEY_STORE_PASSWORD)).thenReturn(password);
        when(scope.get(MtlsKeystore.CONFIG_KEY_STORE_TYPE)).thenReturn(type);
        return scope;
    }

    /** A session whose Keycloak truststore is not configured (TrustManagers resolve to null). */
    public static KeycloakSession sessionWithoutTruststore() {
        return session(null);
    }

    /** A session whose Keycloak truststore trusts the certificates in {@code truststore}. */
    public static KeycloakSession sessionWithTruststore(KeyStore truststore) {
        return session(truststore);
    }

    private static KeycloakSession session(KeyStore truststore) {
        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakSessionFactory sessionFactory = mock(KeycloakSessionFactory.class);
        TruststoreProviderFactory truststoreFactory = mock(TruststoreProviderFactory.class);

        when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getProviderFactory(eq(TruststoreProvider.class), eq("file")))
                .thenReturn(truststoreFactory);

        if (truststore == null) {
            when(truststoreFactory.create(session)).thenReturn(null);
        } else {
            TruststoreProvider provider = mock(TruststoreProvider.class);
            when(truststoreFactory.create(session)).thenReturn(provider);
            when(provider.getTruststore()).thenReturn(truststore);
        }
        return session;
    }
}
