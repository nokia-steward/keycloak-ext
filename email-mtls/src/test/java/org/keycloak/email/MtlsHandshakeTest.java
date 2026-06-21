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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
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
 * End-to-end proof that the {@link MtlsKeystore} socket factory presents the client certificate
 * during a mutual TLS handshake.
 *
 * <p><b>Test Architecture:</b>
 *
 * <ul>
 *   <li><b>Client (Main thread):</b> Represents Keycloak. Constructed using a real {@link
 *       SSLSocket} provided by the production {@link MtlsKeystore} factory. The only mocked
 *       component is the {@link KeycloakSession}, which is stubbed to provide a truststore that
 *       validates the server.
 *   <li><b>Server (Background thread):</b> Represents the SMTP server. Constructed using a real
 *       {@link SSLServerSocket} initialized with its own keystore and truststore, and configured
 *       with {@code setNeedClientAuth(true)}.
 *   <li><b>Network:</b> A real TCP/TLS connection over the {@code localhost} loopback interface.
 * </ul>
 *
 * <p>The server thread captures the certificate presented by the client during the handshake, and
 * the main thread asserts it matches the exact client identity (Common Name) generated for the
 * test. This validates mTLS behavior for both SMTPS and STARTTLS, as both use the same socket
 * factory.
 */
public class MtlsHandshakeTest {

    private static final String CLIENT_CN = "smtp-client";
    private static final String SERVER_CN = "smtp-server";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final int TIMEOUT_MS = 15_000;
    private static final int TIMEOUT_SEC = TIMEOUT_MS / 1000;

    @Test
    public void presentsClientCertificateToServerRequiringClientAuth() throws Exception {
        // 1. Client (Keycloak) and Server (SMTP) identities. Each self-signed cert acts as a trust
        // anchor.
        KeyPair serverKeyPair = CertificateTestHelper.newKeyPair();
        X509Certificate serverCert = CertificateTestHelper.selfSigned(serverKeyPair, SERVER_CN);
        KeyPair clientKeyPair = CertificateTestHelper.newKeyPair();
        X509Certificate clientCert = CertificateTestHelper.selfSigned(clientKeyPair, CLIENT_CN);

        // 2. Client setup: Load the keystore and build the SSL socket factory.
        SSLSocketFactory clientFactory = setupClientFactory(clientKeyPair, clientCert, serverCert);

        // 3. Server setup: A minimal SSL server socket that requires client authentication.
        SSLContext serverContext = serverContext(serverKeyPair, serverCert, clientCert);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket serverSocket =
                (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0)) {
            serverSocket.setNeedClientAuth(true);
            serverSocket.setSoTimeout(TIMEOUT_MS);
            int port = serverSocket.getLocalPort();

            // 4. Network: Run the server accept loop on a background thread.
            Future<Certificate[]> presentedByClient =
                    executor.submit(acceptAndReadPeer(serverSocket));

            // Connect the client over the loopback interface on the main thread.
            try (SSLSocket clientSocket =
                    (SSLSocket)
                            clientFactory.createSocket(InetAddress.getLoopbackAddress(), port)) {
                clientSocket.setSoTimeout(TIMEOUT_MS);
                clientSocket.startHandshake();
                OutputStream out = clientSocket.getOutputStream();
                out.write(42);
                out.flush();
            }

            // 5. Assertion: Retrieve the certificate captured by the server and verify its
            // identity.
            Certificate[] peerChain = presentedByClient.get(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertTrue(peerChain.length > 0);
            X509Certificate presented = (X509Certificate) peerChain[0];
            assertTrue(presented.getSubjectX500Principal().getName().contains(CLIENT_CN));
        } finally {
            executor.shutdownNow();
        }
    }

    private SSLSocketFactory setupClientFactory(
            KeyPair clientKeyPair, X509Certificate clientCert, X509Certificate serverCert)
            throws Exception {
        // Load the keystore using the production Keycloak SPI loader.
        File clientKeystoreFile =
                CertificateTestHelper.createKeystoreFile(
                        KEYSTORE_TYPE, ".p12", clientKeyPair.getPrivate(), clientCert);
        Config.Scope config =
                MtlsTestMocks.keycloakConfigScope(
                        clientKeystoreFile.getAbsolutePath(),
                        CertificateTestHelper.PASSWORD_STRING,
                        null);
        MtlsKeystore mtls = MtlsKeystore.from(config);

        // The client trusts the server certificate via a mocked Keycloak session truststore.
        KeycloakSession session =
                MtlsTestMocks.sessionWithTruststore(
                        CertificateTestHelper.trustStoreWith(serverCert));
        return mtls.createSocketFactory(session);
    }

    /**
     * Background thread task: accepts the connection, performs the handshake, and captures the
     * client's certificate.
     */
    private static Callable<Certificate[]> acceptAndReadPeer(SSLServerSocket serverSocket) {
        return () -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.setSoTimeout(TIMEOUT_MS);
                socket.startHandshake();
                Certificate[] peer = socket.getSession().getPeerCertificates();
                // Drain the single byte the client sends so it can close cleanly.
                InputStream in = socket.getInputStream();
                in.read();
                return peer;
            }
        };
    }

    /**
     * Builds the SSLContext for the minimal SMTP server, trusting only the provided client
     * certificate.
     */
    private static SSLContext serverContext(
            KeyPair serverKeyPair, X509Certificate serverCert, X509Certificate trustedClientCert)
            throws Exception {
        KeyStore serverKeyStore =
                CertificateTestHelper.newKeyStore(
                        KEYSTORE_TYPE, "server", serverKeyPair.getPrivate(), serverCert);

        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, CertificateTestHelper.PASSWORD);

        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(CertificateTestHelper.trustStoreWith(trustedClientCert));

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }
}
