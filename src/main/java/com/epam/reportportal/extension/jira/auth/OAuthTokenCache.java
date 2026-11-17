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

  public void invalidate(OAuthCredentials creds) {
    Holder holder = holders.get(creds.cacheKey());
    if (holder != null) {
      holder.clear();
    }
  }

  private final class Holder {

    private AccessToken token;

    synchronized String get(OAuthCredentials creds) {
      if (token == null || !token.expiresAt().minus(REFRESH_MARGIN).isAfter(Instant.now(clock))) {
        token = null;
        token = client.fetch(creds.clientId(), creds.clientSecret());
      }
      return token.value();
    }

    synchronized void clear() {
      token = null;
    }
  }
}
