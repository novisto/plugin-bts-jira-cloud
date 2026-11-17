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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuthTokenCacheTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final String CLOUD_ID = "11111111-2222-3333-4444-555555555555";
  private static final long WAIT_SECONDS = 10;

  private final MutableClock clock = new MutableClock(NOW);
  private OAuthTokenClient client;
  private OAuthTokenCache cache;
  private OAuthCredentials creds;

  @BeforeEach
  void setUp() {
    client = mock(OAuthTokenClient.class);
    cache = new OAuthTokenCache(client, clock);
    creds = new OAuthCredentials(CLOUD_ID, "client", "s1");
  }

  @Test
  void cachesWhileMoreThanFiveMinutesLeft() {
    when(client.fetch("client", "s1")).thenReturn(token("t1", 3600));

    clock.advance(Duration.ofMinutes(54));

    assertEquals("t1", cache.token(creds));
    assertEquals("t1", cache.token(creds));
    verify(client, times(1)).fetch("client", "s1");
  }

  @Test
  void refetchesInsideFiveMinuteWindow() {
    when(client.fetch("client", "s1")).thenReturn(token("t1", 3600)).thenReturn(token("t2", 3600));

    assertEquals("t1", cache.token(creds));
    clock.advance(Duration.ofMinutes(56));

    assertEquals("t2", cache.token(creds));
    verify(client, times(2)).fetch("client", "s1");
  }

  @Test
  void shortLivedTokenIsStillReturned() {
    when(client.fetch("client", "s1")).thenReturn(token("short", 60)).thenReturn(token("short2", 60));

    assertEquals("short", cache.token(creds));
    assertEquals("short2", cache.token(creds));
    verify(client, times(2)).fetch("client", "s1");
  }

  @Test
  void differentSecretDifferentEntry() {
    OAuthCredentials other = new OAuthCredentials(CLOUD_ID, "client", "s2");
    when(client.fetch("client", "s1")).thenReturn(token("t1", 3600));
    when(client.fetch("client", "s2")).thenReturn(token("t2", 3600));

    assertEquals("t1", cache.token(creds));
    assertEquals("t2", cache.token(other));
    verify(client).fetch("client", "s1");
    verify(client).fetch("client", "s2");
  }

  @Test
  void invalidateForcesRefetch() {
    when(client.fetch("client", "s1")).thenReturn(token("t1", 3600)).thenReturn(token("t2", 3600));

    assertEquals("t1", cache.token(creds));
    cache.invalidate(creds);

    assertEquals("t2", cache.token(creds));
    verify(client, times(2)).fetch("client", "s1");
  }

  @Test
  void invalidateOfUnknownCredentialsIsHarmless() {
    cache.invalidate(creds);

    when(client.fetch("client", "s1")).thenReturn(token("t1", 3600));
    assertEquals("t1", cache.token(creds));
  }

  @Test
  void failedFetchIsNotCachedAndPropagates() {
    ReportPortalException failure =
        new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "boom");
    when(client.fetch("client", "s1")).thenThrow(failure).thenReturn(token("t1", 3600));

    ReportPortalException thrown = assertThrows(ReportPortalException.class, () -> cache.token(creds));
    assertSame(failure, thrown);

    assertEquals("t1", cache.token(creds));
    verify(client, times(2)).fetch("client", "s1");
  }

  @Test
  void refreshFailureInsideWindowReturnsOldToken() {
    when(client.fetch("client", "s1")).thenReturn(token("old", 3600)).thenThrow(failure());

    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofMinutes(56));

    assertEquals("old", cache.token(creds));
    verify(client, times(2)).fetch("client", "s1");
  }

  @Test
  void refreshFailureWithExpiredTokenRethrowsOriginal() {
    ReportPortalException failure = failure();
    when(client.fetch("client", "s1")).thenReturn(token("old", 3600)).thenThrow(failure);

    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofMinutes(61));

    assertSame(failure, assertThrows(ReportPortalException.class, () -> cache.token(creds)));
  }

  @Test
  void noRetryWithinThirtySecondsAfterFailedRefresh() {
    when(client.fetch("client", "s1")).thenReturn(token("old", 3600)).thenThrow(failure());

    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofMinutes(56));
    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofSeconds(29));

    assertEquals("old", cache.token(creds));
    verify(client, times(2)).fetch("client", "s1");
  }

  @Test
  void retriesAfterThirtySecondsFollowingFailedRefresh() {
    when(client.fetch("client", "s1"))
        .thenReturn(token("old", 3600))
        .thenThrow(failure())
        .thenReturn(token("new", 3600));

    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofMinutes(56));
    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofSeconds(31));

    assertEquals("new", cache.token(creds));
    verify(client, times(3)).fetch("client", "s1");
  }

  @Test
  void invalidatedTokenIsNeverUsedAsFallback() {
    ReportPortalException failure = failure();
    when(client.fetch("client", "s1")).thenReturn(token("old", 3600)).thenThrow(failure);

    assertEquals("old", cache.token(creds));
    cache.invalidate(creds);

    assertSame(failure, assertThrows(ReportPortalException.class, () -> cache.token(creds)));
  }

  @Test
  void invalidateClearsFailedRefreshBackoff() {
    when(client.fetch("client", "s1"))
        .thenReturn(token("old", 3600))
        .thenThrow(failure())
        .thenReturn(token("new", 3600));

    assertEquals("old", cache.token(creds));
    clock.advance(Duration.ofMinutes(56));
    assertEquals("old", cache.token(creds));
    cache.invalidate(creds);

    assertEquals("new", cache.token(creds));
  }

  private static ReportPortalException failure() {
    return new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "boom");
  }

  @Test
  void concurrentCallersShareOneFetch() throws Exception {
    int callers = 8;
    CountDownLatch fetchStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.fetch("client", "s1")).thenAnswer(invocation -> {
      fetchStarted.countDown();
      assertTrue(release.await(WAIT_SECONDS, TimeUnit.SECONDS));
      return token("shared", 3600);
    });

    ExecutorService executor = Executors.newFixedThreadPool(callers);
    try {
      CountDownLatch ready = new CountDownLatch(callers);
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        results.add(executor.submit(() -> {
          ready.countDown();
          return cache.token(creds);
        }));
      }
      assertTrue(ready.await(WAIT_SECONDS, TimeUnit.SECONDS));
      assertTrue(fetchStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
      release.countDown();

      for (Future<String> result : results) {
        assertEquals("shared", result.get(WAIT_SECONDS, TimeUnit.SECONDS));
      }
      verify(client, times(1)).fetch("client", "s1");
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
    }
  }

  @Test
  void slowFetchForOneKeyDoesNotBlockAnotherKey() throws Exception {
    OAuthCredentials other = new OAuthCredentials(CLOUD_ID, "client", "s2");
    CountDownLatch slowStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.fetch("client", "s1")).thenAnswer(invocation -> {
      slowStarted.countDown();
      assertTrue(release.await(WAIT_SECONDS, TimeUnit.SECONDS));
      return token("slow", 3600);
    });
    when(client.fetch("client", "s2")).thenReturn(token("fast", 3600));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> slow = executor.submit(() -> cache.token(creds));
      assertTrue(slowStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));

      Future<String> fast = executor.submit(() -> cache.token(other));
      try {
        assertEquals("fast", fast.get(WAIT_SECONDS, TimeUnit.SECONDS));
      } finally {
        release.countDown();
      }
      assertEquals("slow", slow.get(WAIT_SECONDS, TimeUnit.SECONDS));
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
    }
  }

  private AccessToken token(String value, long expiresInSeconds) {
    return new AccessToken(value, clock.instant().plusSeconds(expiresInSeconds));
  }

  private static final class MutableClock extends Clock {

    private volatile Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
