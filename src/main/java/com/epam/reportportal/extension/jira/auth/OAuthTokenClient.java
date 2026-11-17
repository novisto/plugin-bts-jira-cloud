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

import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

public class OAuthTokenClient {

  private static final URI DEFAULT_TOKEN_ENDPOINT =
      URI.create("https://auth.atlassian.com/oauth/token");
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CONNECTION_REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);
  private static final String FAILURE_PREFIX = "OAuth token request failed: HTTP ";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final URI tokenEndpoint;
  private final Clock clock;
  private final Duration responseTimeout;

  public OAuthTokenClient() {
    this(DEFAULT_TOKEN_ENDPOINT, Clock.systemUTC());
  }

  public OAuthTokenClient(URI tokenEndpoint, Clock clock) {
    this(tokenEndpoint, clock, RESPONSE_TIMEOUT);
  }

  OAuthTokenClient(URI tokenEndpoint, Clock clock, Duration responseTimeout) {
    this.tokenEndpoint = tokenEndpoint;
    this.clock = clock;
    this.responseTimeout = responseTimeout;
  }

  public AccessToken fetch(String clientId, String clientSecret) {
    HttpPost request = new HttpPost(tokenEndpoint);
    request.setEntity(new UrlEncodedFormEntity(List.of(
        new BasicNameValuePair("grant_type", "client_credentials"),
        new BasicNameValuePair("client_id", clientId),
        new BasicNameValuePair("client_secret", clientSecret))));

    try (CloseableHttpClient httpClient = newHttpClient()) {
      return httpClient.execute(request, response -> {
        int status = response.getCode();
        String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        JsonNode json = parse(body);
        if (status < 200 || status >= 300) {
          throw failure(status, json);
        }
        String accessToken = text(json, "access_token");
        JsonNode expiresIn = json == null ? null : json.get("expires_in");
        if (accessToken == null || expiresIn == null || !expiresIn.canConvertToLong()) {
          throw failure(status, json);
        }
        return new AccessToken(accessToken, clock.instant().plusSeconds(expiresIn.asLong()));
      });
    } catch (IOException e) {
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
          "OAuth token request failed: connection error");
    }
  }

  private CloseableHttpClient newHttpClient() {
    return HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                .build())
            .build())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(CONNECTION_REQUEST_TIMEOUT))
            .setResponseTimeout(Timeout.of(responseTimeout))
            .build())
        .build();
  }

  private JsonNode parse(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (IOException e) {
      return null;
    }
  }

  private static String text(JsonNode json, String field) {
    if (json == null) {
      return null;
    }
    JsonNode node = json.get(field);
    if (node == null || !node.isTextual() || node.asText().isBlank()) {
      return null;
    }
    return node.asText();
  }

  private static ReportPortalException failure(int status, JsonNode json) {
    String error = text(json, "error");
    return new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
        FAILURE_PREFIX + status + (error == null ? "" : ", " + error));
  }
}
