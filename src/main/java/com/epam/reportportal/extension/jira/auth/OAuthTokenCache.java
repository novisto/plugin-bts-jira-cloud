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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OAuthTokenCache {

  private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
  private static final Duration REFRESH_RETRY_DELAY = Duration.ofSeconds(30);

  private final OAuthTokenClient client;
  private final Clock clock;
  private final ConcurrentMap<String, Holder> holders = new ConcurrentHashMap<>();

  public OAuthTokenCache(OAuthTokenClient client, Clock clock) {
    this.client = client;
    this.clock = clock;
  }

  public String token(OAuthCredentials creds) {
    return holders.computeIfAbsent(creds.cacheKey(), key -> new Holder()).get(creds);
  }

  public void invalidate(OAuthCredentials creds, String rejectedToken) {
    Holder holder = holders.get(creds.cacheKey());
    if (holder != null) {
      holder.clearIfCurrent(rejectedToken);
    }
  }

  private final class Holder {

    private AccessToken token;
    private Instant retryNotBefore;

    synchronized String get(OAuthCredentials creds) {
      Instant now = Instant.now(clock);
      if (token != null && token.expiresAt().minus(REFRESH_MARGIN).isAfter(now)) {
        return token.value();
      }
      if (token != null && token.expiresAt().isAfter(now) && retryNotBefore != null
          && now.isBefore(retryNotBefore)) {
        return token.value();
      }
      try {
        token = client.fetch(creds.clientId(), creds.clientSecret());
        retryNotBefore = null;
      } catch (RuntimeException e) {
        Instant failedAt = Instant.now(clock);
        if (token == null || !token.expiresAt().isAfter(failedAt)) {
          clear();
          throw e;
        }
        retryNotBefore = failedAt.plus(REFRESH_RETRY_DELAY);
      }
      return token.value();
    }

    synchronized void clearIfCurrent(String rejectedToken) {
      if (token != null && token.value().equals(rejectedToken)) {
        clear();
      }
    }

    synchronized void clear() {
      token = null;
      retryNotBefore = null;
    }
  }
}
