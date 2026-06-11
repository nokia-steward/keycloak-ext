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
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Best-effort integration smoke test (requires Docker; run with {@code ./mvnw verify -Pit}).
 *
 * <p>Starts the official Keycloak 26.6.3 image with this extension's JAR dropped into
 * {@code providers/} and the {@code email-mtls} provider selected. A successful startup
 * proves that the JAR is loaded, the {@code META-INF/services} registration is picked
 * up, and the {@code email-mtls} provider id is selectable (acceptance criterion 8).
 * The split-package override itself is validated deterministically by the unit and
 * handshake tests in the default build.</p>
 */
public class KeycloakEmailMtlsIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.6.3";

    @Test
    public void startsKeycloakWithEmailMtlsProviderSelected() throws Exception {
        Path providerJar = findProviderJar();

        try (GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
                .withCopyFileToContainer(MountableFile.forHostPath(providerJar),
                        "/opt/keycloak/providers/keycloak-email-mtls.jar")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withEnv("KC_SPI_EMAIL_SENDER__PROVIDER", "email-mtls")
                .withCommand("start-dev")
                .withExposedPorts(8080)
                .waitingFor(Wait.forLogMessage(".*(Running the server|started in)\\b.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(4)))) {

            keycloak.start();

            assertThat(keycloak.isRunning(), is(true));
            String logs = keycloak.getLogs();
            assertThat(logs, not(containsString("Failed to load provider")));
            assertThat(logs, not(containsString("NoSuchMethodError")));
            assertThat(logs, not(containsString("IllegalAccessError")));
        }
    }

    private static Path findProviderJar() throws IOException {
        Path target = Paths.get("target");
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(target, "keycloak-email-mtls-*.jar")) {
            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                if (!name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")) {
                    return jar;
                }
            }
        }
        throw new IllegalStateException(
                "Provider JAR not found in target/. Build the module first (the 'it' profile runs after 'package').");
    }
}
