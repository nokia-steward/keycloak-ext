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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end integration test that proves the {@code email-mtls} provider works correctly on a real
 * server setup using Keycloak and Postfix SMTP Docker containers.
 *
 * <p>Runs two mTLS tests: a positive case (client keystore configured → connection succeeds) and a
 * negative case (no keystore → connection fails). Requires Docker to run.
 *
 * <p>The SMTP server container is shared between both tests; Keycloak is recreated per test because
 * its mTLS keystore is read only at startup.
 */
public class EmailMtlsEndToEndIT {

    // Keycloak Docker image. Version is injected from <keycloak.version> in pom.xml.
    private static final String KEYCLOAK_DOCKER_IMAGE =
            "quay.io/keycloak/keycloak:"
                    + Objects.requireNonNull(
                            System.getProperty("keycloak.version"),
                            "System property 'keycloak.version' is not set — run with Maven -Pit profile");
    // Keycloak admin user and password
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin";
    // SMTP server alias and port
    private static final String SMTP_ALIAS = "smtp";
    private static final int SMTP_PORT = 25;

    // PKI material (CA + SMTP server cert + Keycloak client cert), generated once for all tests.
    private static IntegrationTestPki pki;

    // Test container elements shared across tests.
    private static Network network;
    private static GenericContainer<?> smtp;

    @BeforeClass
    public static void beforeAll() throws Exception {
        pki = IntegrationTestPki.generate(SMTP_ALIAS);
        network = Network.newNetwork();
        smtp = buildSmtpContainer();
        smtp.start();
    }

    @AfterClass
    public static void afterAll() {
        try {
            if (smtp != null) smtp.stop();
        } finally {
            if (network != null) network.close();
        }
    }

    //
    // Tests
    //

    @Test
    public void positiveCase_withClientKeystore_trustedTlsConnectionEstablished() throws Exception {
        int smtpLogLengthBeforeTest = smtp.getLogs().length();

        try (GenericContainer<?> keycloak = startKeycloak(true)) {
            KeycloakAdminClient admin = new KeycloakAdminClient(keycloak.getMappedPort(8080));
            String token = admin.getAdminToken(ADMIN_USER, ADMIN_PASS);
            admin.setUserEmail(token, ADMIN_USER, "admin@test.local");

            int status = admin.triggerTestSmtpConnection(token, SMTP_ALIAS, SMTP_PORT);
            assertEquals(
                    "testSMTPConnection must return 204 when client keystore is configured",
                    204,
                    status);
        }
        // Keycloak container is stopped and removed by this moment

        // Wait for a moment to allow the SMTP server to flush the log lines
        Thread.sleep(2_000);
        String newLogs = smtp.getLogs().substring(smtpLogLengthBeforeTest);
        assertTrue(
                "SMTP server must log a trusted TLS connection with the client cert",
                newLogs.contains("Trusted TLS connection established"));
        assertTrue(
                "SMTP server must record the client cert CN",
                newLogs.contains(IntegrationTestPki.CLIENT_CN));
    }

    /**
     * Verifies that the SMTP connection fails when no client keystore is configured. A TLS
     * handshake failure is the expected and successful outcome of this test
     */
    @Test
    public void negativeCase_noClientKeystore_tlsHandshakeFails() throws Exception {
        int smtpLogLengthBeforeTest = smtp.getLogs().length();

        int status = -1;
        try (GenericContainer<?> keycloak = startKeycloak(false)) {
            KeycloakAdminClient admin = new KeycloakAdminClient(keycloak.getMappedPort(8080));
            String token = admin.getAdminToken(ADMIN_USER, ADMIN_PASS);
            admin.setUserEmail(token, ADMIN_USER, "admin@test.local");

            status = admin.triggerTestSmtpConnection(token, SMTP_ALIAS, SMTP_PORT);
        } catch (Exception e) {
            // A transport-layer exception is also an expected outcome: the connection did not
            // succeed.
        }

        assertFalse("testSMTPConnection must NOT succeed without a client keystore", status == 204);

        Thread.sleep(2_000);
        String newLogs = smtp.getLogs().substring(smtpLogLengthBeforeTest);
        assertFalse(
                "SMTP server must NOT log a trusted TLS connection without a client cert",
                newLogs.contains("Trusted TLS connection established"));
    }

    //
    // SMTP server container
    //

    /**
     * Builds a Postfix SMTP container image from {@code debian:12.14-slim}, installing Postfix and
     * configuring it to require STARTTLS with a client certificate ({@code
     * smtpd_tls_req_ccert=yes}). The test CA certificate, server certificate, and server key from
     * {@link IntegrationTestPki} are copied into the image at runtime so Postfix can validate the
     * Keycloak client certificate.
     */
    private static GenericContainer<?> buildSmtpContainer() throws IOException {
        File mainCf = writeSmtpMainCf();

        ImageFromDockerfile smtpImage =
                new ImageFromDockerfile("email-mtls-smtp-test", false)
                        .withDockerfileFromBuilder(
                                b ->
                                        b.from("debian:12.14-slim")
                                                .run(
                                                        "DEBIAN_FRONTEND=noninteractive apt-get update"
                                                                + " && apt-get install -y --no-install-recommends postfix"
                                                                + " && rm -rf /var/lib/apt/lists/*"
                                                                + " && mkdir -p /etc/postfix/tls"
                                                                + " && newaliases"
                                                                // Disable chroot for smtpd so it
                                                                // can read /etc/postfix/tls/
                                                                // from outside the chroot jail
                                                                // (/var/spool/postfix).
                                                                + " && sed -i -E"
                                                                + " 's/^(smtp[[:space:]]+inet[[:space:]]+[^[:space:]]+[[:space:]]+[^[:space:]]+[[:space:]]+)y/\\1n/'"
                                                                + " /etc/postfix/master.cf")
                                                .expose(SMTP_PORT)
                                                // start-fg keeps the master in the foreground (no
                                                // daemonization),
                                                // so maillog_file=/dev/stdout reaches Docker's log
                                                // capture.
                                                .cmd(
                                                        "sh",
                                                        "-c",
                                                        "chmod 600 /etc/postfix/tls/server.key"
                                                                + " && exec postfix start-fg")
                                                .build());

        return new GenericContainer<>(smtpImage)
                .withNetwork(network)
                .withNetworkAliases(SMTP_ALIAS)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(mainCf.toPath()), "/etc/postfix/main.cf")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(pki.getCaCertPem().toPath()),
                        "/etc/postfix/tls/ca.crt")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(pki.getServerCertPem().toPath()),
                        "/etc/postfix/tls/server.pem")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(pki.getServerKeyPem().toPath()),
                        "/etc/postfix/tls/server.key")
                .withExposedPorts(SMTP_PORT)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
    }

    /**
     * Writes a Postfix SMTP server {@code main.cf} config to a temp file, enabling mandatory
     * STARTTLS with required client certificate.
     */
    private static File writeSmtpMainCf() throws IOException {
        String cf =
                String.join(
                        "\n",
                        "myhostname = " + SMTP_ALIAS,
                        "mydomain = test.local",
                        "myorigin = $mydomain",
                        "mydestination = $myhostname, localhost, $mydomain",
                        "mynetworks = 0.0.0.0/0, [::]/0",
                        "inet_interfaces = all",
                        "inet_protocols = ipv4",
                        "maillog_file = /dev/stdout",
                        "",
                        "# Relay",
                        "smtpd_relay_restrictions = permit_mynetworks defer_unauth_destination",
                        "",
                        "# Require STARTTLS and a trusted client certificate",
                        "smtpd_tls_security_level = encrypt",
                        "smtpd_tls_req_ccert = yes",
                        "smtpd_tls_cert_file = /etc/postfix/tls/server.pem",
                        "smtpd_tls_key_file = /etc/postfix/tls/server.key",
                        "smtpd_tls_CAfile = /etc/postfix/tls/ca.crt",
                        "smtpd_tls_loglevel = 2",
                        "smtpd_tls_received_header = yes",
                        "",
                        "# Disable DNS and accept all local recipients without /etc/passwd lookup",
                        "disable_dns_lookups = yes",
                        "local_recipient_maps =",
                        "mailbox_size_limit = 0");
        File f = Files.createTempFile("smtp-main-", ".cf").toFile();
        f.deleteOnExit();
        Files.writeString(f.toPath(), cf);
        return f;
    }

    //
    // Keycloak container
    //

    /**
     * Starts a Keycloak container with the email-mtls provider and the test CA as the truststore.
     * The mTLS client keystore may or may not be set based on the {@code withClientKeystore}
     * parameter.
     *
     * @param withClientKeystore if {@code true}, the PKCS12 mTLS keystore is mounted and the mTLS
     *     keystore env vars are set, enabling mTLS authentication. If {@code false}, no keystore is
     *     provided and the mTLS SMTP connection is expected to fail.
     */
    private static GenericContainer<?> startKeycloak(boolean withClientKeystore)
            throws IOException {
        Path providerJar = findProviderJar();

        GenericContainer<?> kc =
                new GenericContainer<>(KEYCLOAK_DOCKER_IMAGE)
                        .withNetwork(network)
                        .withCopyFileToContainer(
                                MountableFile.forHostPath(providerJar),
                                "/opt/keycloak/providers/keycloak-email-mtls.jar")
                        .withCopyFileToContainer(
                                MountableFile.forHostPath(pki.getCaCertPem().toPath()),
                                "/opt/keycloak/tls/ca.crt")
                        .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USER)
                        .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASS)
                        .withEnv("KC_SPI_EMAIL_SENDER__PROVIDER", "email-mtls")
                        // Point Keycloak's TruststoreProvider at our test CA
                        .withEnv("KC_TRUSTSTORE_PATHS", "/opt/keycloak/tls/ca.crt")
                        .withCommand("start-dev")
                        .withExposedPorts(8080)
                        .waitingFor(
                                Wait.forHttp("/realms/master")
                                        .forStatusCode(200)
                                        .withStartupTimeout(Duration.ofMinutes(5)));

        if (withClientKeystore) {
            kc.withCopyFileToContainer(
                            MountableFile.forHostPath(pki.getClientKeystore().toPath()),
                            "/opt/keycloak/keystores/client.p12")
                    .withEnv(
                            "KC_SPI_EMAIL_SENDER_EMAIL_MTLS_KEY_STORE",
                            "/opt/keycloak/keystores/client.p12")
                    .withEnv(
                            "KC_SPI_EMAIL_SENDER_EMAIL_MTLS_KEY_STORE_PASSWORD",
                            pki.getClientKeystorePassword());
        }

        kc.start();
        return kc;
    }

    /**
     * Finds the email-mtls provider JAR in {@code target/} directory, skipping sources and javadoc
     * JARs.
     */
    private static Path findProviderJar() throws IOException {
        Path target = Paths.get("target");
        try (DirectoryStream<Path> jars =
                Files.newDirectoryStream(target, "keycloak-email-mtls-*.jar")) {
            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                if (!name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")) {
                    return jar;
                }
            }
        }
        throw new IllegalStateException(
                "Provider JAR keycloak-email-mtls-*.jar not found in target/ directory. Build it before running the tests.");
    }
}
