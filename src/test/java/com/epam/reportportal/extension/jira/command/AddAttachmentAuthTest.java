/*
 * Copyright 2025 EPAM Systems
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

package com.epam.reportportal.extension.jira.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.extension.jira.auth.AuthType;
import com.epam.reportportal.extension.jira.auth.BasicJiraAuth;
import com.epam.reportportal.extension.jira.auth.JiraAuth;
import com.epam.reportportal.extension.jira.auth.OAuthCredentials;
import com.epam.reportportal.extension.jira.auth.OAuthJiraAuth;
import com.epam.reportportal.extension.jira.auth.OAuthTokenCache;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddAttachmentAuthTest {

  private static final String CLOUD_ID = "11111111-2222-3333-4444-555555555555";
  private static final String GATEWAY_PATH = "/ex/jira/" + CLOUD_ID;
  private static final String ATTACHMENTS = "/rest/api/latest/issue/DO-1/attachments";
  private static final String PAYLOAD = "payload-bytes";

  private HttpServer server;
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> authHeaders = new CopyOnWriteArrayList<>();
  private final List<String> noCheckHeaders = new CopyOnWriteArrayList<>();
  private final List<String> bodies = new CopyOnWriteArrayList<>();
  private final List<Integer> statuses = new CopyOnWriteArrayList<>();
  private final AtomicInteger served = new AtomicInteger();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      paths.add(exchange.getRequestURI().getPath());
      authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
      noCheckHeaders.add(exchange.getRequestHeaders().getFirst("X-Atlassian-Token"));
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      int status = statuses.get(Math.min(served.getAndIncrement(), statuses.size() - 1));
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private String serverUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private OAuthCredentials creds() {
    return new OAuthCredentials(CLOUD_ID, "client", "secret");
  }

  private OAuthTokenCache cacheReturning(OAuthCredentials creds, String first, String second) {
    OAuthTokenCache cache = mock(OAuthTokenCache.class);
    when(cache.token(creds)).thenReturn(first, second);
    return cache;
  }

  private JiraAuth oauthAgainstStub(OAuthCredentials creds, OAuthTokenCache cache) {
    OAuthJiraAuth real = new OAuthJiraAuth(creds, cache);
    return new JiraAuth() {
      @Override
      public AuthType type() {
        return real.type();
      }

      @Override
      public String apiBaseUrl() {
        return serverUrl() + GATEWAY_PATH;
      }

      @Override
      public String authorizationHeader() {
        return real.authorizationHeader();
      }

      @Override
      public void invalidate() {
        real.invalidate();
      }
    };
  }

  private void upload(JiraAuth auth) {
    PostTicketCommand.uploadAttachment(auth, "DO-1",
        PAYLOAD.getBytes(StandardCharsets.UTF_8), "log.txt");
  }

  @Test
  void oauthPostsToGatewayWithBearerAndNoCheck() {
    statuses.add(200);
    OAuthCredentials creds = creds();

    upload(oauthAgainstStub(creds, cacheReturning(creds, "t1", "t2")));

    assertEquals(List.of(GATEWAY_PATH + ATTACHMENTS), paths);
    assertEquals(List.of("Bearer t1"), authHeaders);
    assertEquals(List.of("no-check"), noCheckHeaders);
    assertTrue(bodies.get(0).contains("name=\"file\"; filename=\"log.txt\""));
    assertTrue(bodies.get(0).contains(PAYLOAD));
  }

  @Test
  void oauth401RetriesOnceWithFreshToken() {
    statuses.add(401);
    statuses.add(200);
    OAuthCredentials creds = creds();
    OAuthTokenCache cache = cacheReturning(creds, "t1", "t2");

    upload(oauthAgainstStub(creds, cache));

    assertEquals(List.of("Bearer t1", "Bearer t2"), authHeaders);
    verify(cache, times(1)).invalidate(creds);
    assertEquals(2, bodies.size());
    assertTrue(bodies.get(1).contains(PAYLOAD));
  }

  @Test
  void oauthSecond401Fails() {
    statuses.add(401);
    OAuthCredentials creds = creds();
    OAuthTokenCache cache = cacheReturning(creds, "t1", "t2");

    var ex = assertThrows(IllegalArgumentException.class,
        () -> upload(oauthAgainstStub(creds, cache)));

    assertEquals("Failed to upload attachment", ex.getMessage());
    assertEquals(2, paths.size());
    verify(cache, times(1)).invalidate(creds);
  }

  @Test
  void basicHeaderUnchangedAndNoRetryOn401() {
    statuses.add(401);

    assertThrows(IllegalArgumentException.class,
        () -> upload(new BasicJiraAuth(serverUrl(), "e", "t")));

    assertEquals(List.of(ATTACHMENTS), paths);
    String expected = "Basic " + Base64.getEncoder()
        .encodeToString("e:t".getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of(expected), authHeaders);
  }

  @Test
  void basicSucceeds() {
    statuses.add(200);

    upload(new BasicJiraAuth(serverUrl(), "e", "t"));

    assertEquals(List.of(ATTACHMENTS), paths);
  }

  @Test
  void non200StillFails() {
    statuses.add(500);
    OAuthCredentials creds = creds();
    OAuthTokenCache cache = cacheReturning(creds, "t1", "t2");

    var ex = assertThrows(IllegalArgumentException.class,
        () -> upload(oauthAgainstStub(creds, cache)));

    assertEquals("Failed to upload attachment", ex.getMessage());
    assertEquals(1, paths.size());
    verify(cache, never()).invalidate(creds);
  }
}
