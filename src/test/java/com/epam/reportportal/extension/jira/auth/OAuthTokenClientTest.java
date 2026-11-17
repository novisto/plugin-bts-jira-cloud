/*
 * Copyright 2021 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.extension.jira.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epam.reportportal.rules.exception.ReportPortalException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuthTokenClientTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final String SECRET = "s3cr&t=value";

  private HttpServer server;
  private final AtomicReference<String> method = new AtomicReference<>();
  private final AtomicReference<String> contentType = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String responseBody = "";
  private OAuthTokenClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/oauth/token", exchange -> {
      method.set(exchange.getRequestMethod());
      contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    URI endpoint = URI.create(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/token");
    client = new OAuthTokenClient(endpoint, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void postsFormEncodedClientCredentials() {
    responseBody = "{\"access_token\":\"t\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

    client.fetch("my-id", SECRET);

    assertEquals("POST", method.get());
    assertTrue(contentType.get().startsWith("application/x-www-form-urlencoded"));
    Map<String, String> form = new HashMap<>();
    for (String pair : body.get().split("&")) {
      String[] kv = pair.split("=", 2);
      form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
          URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    assertEquals("client_credentials", form.get("grant_type"));
    assertEquals("my-id", form.get("client_id"));
    assertEquals(SECRET, form.get("client_secret"));
  }

  @Test
  void parsesTokenAndExpiry() {
    responseBody = "{\"access_token\":\"t\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

    AccessToken token = client.fetch("my-id", SECRET);

    assertEquals("t", token.value());
    assertEquals(NOW.plusSeconds(3600), token.expiresAt());
  }

  @Test
  void non2xxGivesStatusAndErrorCodeOnly() {
    status = 401;
    responseBody = "{\"error\":\"access_denied\",\"error_description\":\"secret-ish\"}";

    ReportPortalException e = assertThrows(ReportPortalException.class,
        () -> client.fetch("my-id", SECRET));

    assertTrue(e.getMessage().contains("OAuth token request failed: HTTP 401, access_denied"));
    assertFalse(e.getMessage().contains("secret-ish"));
  }

  @Test
  void okWithoutAccessTokenFails() {
    responseBody = "{}";

    ReportPortalException e = assertThrows(ReportPortalException.class,
        () -> client.fetch("my-id", SECRET));

    assertTrue(e.getMessage().contains("OAuth token request failed: HTTP 200"));
  }

  @Test
  void okWithHtmlBodyFails() {
    responseBody = "<html>proxy</html>";

    ReportPortalException e = assertThrows(ReportPortalException.class,
        () -> client.fetch("my-id", SECRET));

    assertTrue(e.getMessage().contains("OAuth token request failed: HTTP 200"));
    assertFalse(e.getMessage().contains("proxy"));
  }

  @Test
  void messageNeverContainsSecret() {
    status = 400;
    responseBody = "{\"error\":\"invalid_client\",\"error_description\":\"" + SECRET + "\"}";

    ReportPortalException e = assertThrows(ReportPortalException.class,
        () -> client.fetch("my-id", SECRET));

    assertFalse(e.getMessage().contains(SECRET));
  }
}
