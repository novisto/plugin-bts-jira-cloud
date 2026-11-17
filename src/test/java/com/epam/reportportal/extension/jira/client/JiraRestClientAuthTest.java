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

package com.epam.reportportal.extension.jira.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.web.client.HttpClientErrorException;

class JiraRestClientAuthTest {

  private static final String CLOUD_ID = "11111111-2222-3333-4444-555555555555";
  private static final String GATEWAY_PATH = "/ex/jira/" + CLOUD_ID;
  private static final String PROJECT_JSON = "{\"key\":\"DO\"}";

  private HttpServer server;
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> authHeaders = new CopyOnWriteArrayList<>();
  private final List<Integer> statuses = new CopyOnWriteArrayList<>();
  private final AtomicInteger served = new AtomicInteger();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      paths.add(exchange.getRequestURI().getPath());
      authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
      int status = statuses.get(Math.min(served.getAndIncrement(), statuses.size() - 1));
      byte[] body = (status == 200 ? PROJECT_JSON : "{}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, body.length);
      exchange.getResponseBody().write(body);
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

  private OAuthCredentials creds() {
    return new OAuthCredentials(CLOUD_ID, "client", "secret");
  }

  @Test
  void oauthSendsBearerToGatewayPath() {
    statuses.add(200);
    OAuthCredentials creds = creds();
    JiraRestClient client = new JiraRestClient(
        oauthAgainstStub(creds, cacheReturning(creds, "t1", "t2")));

    assertNotNull(client.projectsApi().getProject("DO", null, null));

    assertEquals(List.of(GATEWAY_PATH + "/rest/api/latest/project/DO"), paths);
    assertEquals(List.of("Bearer t1"), authHeaders);
  }

  @Test
  void oauth401RefreshesAndRetriesOnce() {
    statuses.add(401);
    statuses.add(200);
    OAuthCredentials creds = creds();
    OAuthTokenCache cache = cacheReturning(creds, "t1", "t2");
    JiraRestClient client = new JiraRestClient(oauthAgainstStub(creds, cache));

    assertNotNull(client.projectsApi().getProject("DO", null, null));

    assertEquals(List.of("Bearer t1", "Bearer t2"), authHeaders);
    verify(cache, times(1)).invalidate(creds);
  }

  @Test
  void oauthSecond401Surfaces() {
    statuses.add(401);
    OAuthCredentials creds = creds();
    OAuthTokenCache cache = cacheReturning(creds, "t1", "t2");
    JiraRestClient client = new JiraRestClient(oauthAgainstStub(creds, cache));

    assertThrows(HttpClientErrorException.Unauthorized.class,
        () -> client.projectsApi().getProject("DO", null, null));

    assertEquals(2, paths.size());
    verify(cache, times(1)).invalidate(creds);
  }

  @Test
  void oauth403DoesNotRetryOrInvalidate() {
    statuses.add(403);
    OAuthCredentials creds = creds();
    OAuthTokenCache cache = cacheReturning(creds, "t1", "t2");
    JiraRestClient client = new JiraRestClient(oauthAgainstStub(creds, cache));

    assertThrows(HttpClientErrorException.Forbidden.class,
        () -> client.projectsApi().getProject("DO", null, null));

    assertEquals(1, paths.size());
    verify(cache, never()).invalidate(creds);
  }

  @Test
  void basicModeSendsBasicHeaderUnchanged() {
    statuses.add(200);
    JiraRestClient client = new JiraRestClient(new BasicJiraAuth(serverUrl(), "e", "t"));

    assertNotNull(client.projectsApi().getProject("DO", null, null));

    assertEquals(List.of("/rest/api/latest/project/DO"), paths);
    String expected = "Basic " + Base64.getEncoder()
        .encodeToString("e:t".getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of(expected), authHeaders);
  }
}
