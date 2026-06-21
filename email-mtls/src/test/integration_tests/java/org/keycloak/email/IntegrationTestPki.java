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

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

/**
 * Generates and holds the PKI certificates needed for mTLS integration testing. All certificates
 * are written to temporary files that are automatically deleted when the JVM exits.
 */
final class IntegrationTestPki {

    /** CN/SAN of the Keycloak client certificate; also surfaces in the SMTP server logs. */
    static final String CLIENT_CN = "smtp-client";

    // References to the temporary PKI files written by generate().
    private final File caCertPem;
    private final File serverCertPem;
    private final File serverKeyPem;
    private final File clientKeystore;

    private IntegrationTestPki(
            File caCertPem, File serverCertPem, File serverKeyPem, File clientKeystore) {
        this.caCertPem = caCertPem;
        this.serverCertPem = serverCertPem;
        this.serverKeyPem = serverKeyPem;
        this.clientKeystore = clientKeystore;
    }

    /**
     * Generates a self-signed root certificate, then uses it to sign a server certificate (CN/SAN =
     * {@code serverDnsName}) and a client certificate (CN/SAN = {@link #CLIENT_CN}). Both Keycloak
     * and the SMTP server are configured to trust this root, making the mTLS handshake possible
     * without a real Certificate Authority.
     *
     * @param serverDnsName the SMTP server's network alias, used as its cert CN and DNS SAN
     */
    static IntegrationTestPki generate(String serverDnsName) throws Exception {
        KeyPair caKp = CertificateTestHelper.newKeyPair();
        X509Certificate caCert = CertificateTestHelper.caCert(caKp, "test-ca");

        KeyPair serverKp = CertificateTestHelper.newKeyPair();
        X509Certificate serverCert =
                CertificateTestHelper.issueLeaf(
                        serverKp, serverDnsName, serverDnsName, caCert, caKp.getPrivate(), true);

        KeyPair clientKp = CertificateTestHelper.newKeyPair();
        X509Certificate clientCert =
                CertificateTestHelper.issueLeaf(
                        clientKp, CLIENT_CN, CLIENT_CN, caCert, caKp.getPrivate(), false);

        File caCertPem = CertificateTestHelper.writePem(".crt", caCert);
        File serverCertPem = CertificateTestHelper.writePem(".pem", serverCert);
        File serverKeyPem = CertificateTestHelper.writePem(".key", serverKp.getPrivate());
        File clientKeystore =
                CertificateTestHelper.createKeystoreFile(
                        "PKCS12", ".p12", clientKp.getPrivate(), clientCert);

        return new IntegrationTestPki(caCertPem, serverCertPem, serverKeyPem, clientKeystore);
    }

    /** CA root certificate (PEM): trusted by both the SMTP server and Keycloak. */
    File getCaCertPem() {
        return caCertPem;
    }

    /** SMTP server certificate chain (PEM). */
    File getServerCertPem() {
        return serverCertPem;
    }

    /** SMTP server private key (PEM). */
    File getServerKeyPem() {
        return serverKeyPem;
    }

    /** PKCS12 keystore holding the Keycloak client key and certificate. */
    File getClientKeystore() {
        return clientKeystore;
    }

    /** Password protecting {@link #getClientKeystore()}. */
    String getClientKeystorePassword() {
        return CertificateTestHelper.PASSWORD_STRING;
    }
}
