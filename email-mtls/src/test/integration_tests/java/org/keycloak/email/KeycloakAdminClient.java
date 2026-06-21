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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keycloak Admin REST client to support integration tests. It connects to Keycloak at {@code
 * http://localhost:<port>} and operates against the {@code master} realm.
 */
class KeycloakAdminClient {

    // Regex patterns to extract access token and user id from REST API JSON responses.
    private static final Pattern ACCESS_TOKEN_PATTERN =
            Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private static final String ADMIN_REALM_PATH = "/admin/realms/master/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String baseUrl;

    KeycloakAdminClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    /**
     * Obtains an admin access token from the master realm for specified admin user and password.
     */
    String getAdminToken(String adminUser, String adminPassword)
            throws IOException, InterruptedException {
        String url = baseUrl + "/realms/master/protocol/openid-connect/token";
        String body =
                "grant_type=password"
                        + "&client_id=admin-cli"
                        + "&username="
                        + URLEncoder.encode(adminUser, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);

        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(REQUEST_TIMEOUT)
                        .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Matcher m = ACCESS_TOKEN_PATTERN.matcher(response.body());
        if (!m.find()) {
            throw new IllegalStateException(
                    "Failed to obtain access token; status: "
                            + response.statusCode()
                            + ", response: "
                            + response.body());
        }
        return m.group(1);
    }

    /**
     * Sets email property for Keycloak user {@code userName} (required for testSMTPConnection to
     * have a recipient).
     */
    void setUserEmail(String token, String userName, String email)
            throws IOException, InterruptedException {
        String userId = findUserId(token, userName);

        String updateUrl = baseUrl + ADMIN_REALM_PATH + "users/" + userId;
        String updateBody = "{\"email\":\"" + email + "\",\"emailVerified\":true}";
        HttpRequest updateReq =
                HttpRequest.newBuilder()
                        .uri(URI.create(updateUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(updateBody))
                        .timeout(REQUEST_TIMEOUT)
                        .build();
        HttpResponse<String> updateResponse =
                httpClient.send(updateReq, HttpResponse.BodyHandlers.ofString());
        if (updateResponse.statusCode() != 204) {
            throw new IllegalStateException(
                    "Failed to set email for user '"
                            + userName
                            + "'; status: "
                            + updateResponse.statusCode()
                            + ", response: "
                            + updateResponse.body());
        }
    }

    /** Returns the Keycloak user ID for the given {@code userName}. */
    private String findUserId(String token, String userName)
            throws IOException, InterruptedException {
        String searchUrl =
                baseUrl
                        + ADMIN_REALM_PATH
                        + "users?username="
                        + URLEncoder.encode(userName, StandardCharsets.UTF_8);
        HttpRequest searchReq =
                HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .timeout(REQUEST_TIMEOUT)
                        .build();
        HttpResponse<String> response =
                httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());
        Matcher idMatcher = USER_ID_PATTERN.matcher(response.body());
        if (!idMatcher.find()) {
            throw new IllegalStateException(
                    "User '"
                            + userName
                            + "' not found; status: "
                            + response.statusCode()
                            + ", response: "
                            + response.body());
        }
        return idMatcher.group(1);
    }

    /**
     * Calls testSMTPConnection API with the given SMTP host/port and returns the HTTP status code.
     * This is the same API the Admin Console "Test connection" button on the Email settings page
     * invokes.
     */
    int triggerTestSmtpConnection(String token, String smtpHost, int smtpPort)
            throws IOException, InterruptedException {
        String url = baseUrl + ADMIN_REALM_PATH + "testSMTPConnection";
        String smtpJson = buildSmtpTestJson(smtpHost, smtpPort);

        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(smtpJson))
                        .timeout(Duration.ofSeconds(60))
                        .build();
        // Return the raw status code without checking: both 204 (success) and non-204 (failure)
        // are valid expected outcomes depending on the test case.
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static String buildSmtpTestJson(String host, int port) {
        return "{"
                + String.join(
                        ",",
                        kv("host", host),
                        kv("port", String.valueOf(port)),
                        kv("from", "keycloak@test.local"),
                        kv("fromDisplayName", "Keycloak IT"),
                        kv("starttls", "true"),
                        kv("ssl", "false"),
                        kv("auth", "false"),
                        kv("replyTo", ""),
                        kv("envelopeFrom", ""))
                + "}";
    }

    private static String kv(String key, String value) {
        return "\"" + key + "\":\"" + value + "\"";
    }
}
