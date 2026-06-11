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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

/**
 * Deterministic proof that the {@link MtlsClientKeystore} socket factory presents the
 * client certificate during a TLS handshake against a server that <em>requires</em>
 * client authentication ({@code setNeedClientAuth(true)}).
 *
 * <p>The same {@code mail.smtp.ssl.socketFactory} is used by Jakarta Mail for both
 * implicit SMTPS and the STARTTLS upgrade, so this single handshake validates the mTLS
 * behavior for both transport modes (requirements section 18.2-18.3). GreenMail does
 * not natively enforce client-certificate authentication, which is why the mTLS proof
 * is done at the JSSE layer here; see the README test notes.</p>
 */
public class MtlsHandshakeTest {

    @Test
    public void presentsClientCertificateToServerRequiringClientAuth() throws Exception {
        CertificateTestSupport.ensureBouncyCastle();

        // Server and client identities, cross-trusted (each self-signed cert is a trust anchor).
        KeyPair serverKeyPair = CertificateTestSupport.newKeyPair();
        X509Certificate serverCert = CertificateTestSupport.selfSigned(serverKeyPair, "smtp-server");
        KeyPair clientKeyPair = CertificateTestSupport.newKeyPair();
        X509Certificate clientCert = CertificateTestSupport.selfSigned(clientKeyPair, "smtp-client");

        // Client keystore loaded through the production code path.
        File clientKeystoreFile =
                CertificateTestSupport.writeKeyStore("PKCS12", ".p12", clientKeyPair.getPrivate(), clientCert);
        Config.Scope config = MtlsTestMocks.configScope(clientKeystoreFile.getAbsolutePath(),
                CertificateTestSupport.PASSWORD_STRING, null);
        MtlsClientKeystore mtls = MtlsClientKeystore.from(config);

        // The client trusts the server certificate via Keycloak's (mocked) truststore.
        KeycloakSession session =
                MtlsTestMocks.sessionWithTruststore(CertificateTestSupport.trustStoreWith(serverCert));
        SSLSocketFactory clientFactory = mtls.createSocketFactory(session);

        // Server that requires a client certificate signed/trusted by clientCert.
        SSLContext serverContext = serverContext(serverKeyPair, serverCert, clientCert);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket serverSocket =
                     (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0)) {
            serverSocket.setNeedClientAuth(true);
            serverSocket.setSoTimeout(15_000);
            int port = serverSocket.getLocalPort();

            Future<Certificate[]> presentedByClient = executor.submit(acceptAndReadPeer(serverSocket));

            try (SSLSocket clientSocket = (SSLSocket) clientFactory.createSocket("localhost", port)) {
                clientSocket.setSoTimeout(15_000);
                clientSocket.startHandshake();
                OutputStream out = clientSocket.getOutputStream();
                out.write(42);
                out.flush();
            }

            Certificate[] peerChain = presentedByClient.get(15, TimeUnit.SECONDS);
            assertThat(peerChain.length > 0, is(true));
            X509Certificate presented = (X509Certificate) peerChain[0];
            assertThat(presented.getSubjectX500Principal().getName(), containsString("smtp-client"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Certificate[]> acceptAndReadPeer(SSLServerSocket serverSocket) {
        return () -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.setSoTimeout(15_000);
                socket.startHandshake();
                Certificate[] peer = socket.getSession().getPeerCertificates();
                // Drain the single byte the client sends so it can close cleanly.
                InputStream in = socket.getInputStream();
                in.read();
                return peer;
            }
        };
    }

    private static SSLContext serverContext(KeyPair serverKeyPair, X509Certificate serverCert,
                                            X509Certificate trustedClientCert) throws Exception {
        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(null, null);
        serverKeyStore.setKeyEntry("server", serverKeyPair.getPrivate(),
                CertificateTestSupport.PASSWORD, new Certificate[]{serverCert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, CertificateTestSupport.PASSWORD);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(CertificateTestSupport.trustStoreWith(trustedClientCert));

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }
}
