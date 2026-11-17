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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epam.reportportal.rules.exception.ReportPortalException;
import org.junit.jupiter.api.Test;

class OAuthCredentialsTest {

  private static final String UUID_STR = "2335a2f3-094f-4eb5-848d-4ac3a4ef39ce";

  @Test
  void trimsPastedValues() {
    var c = new OAuthCredentials(" " + UUID_STR + "\n", " id ", " secret ");
    assertEquals(UUID_STR, c.cloudId());
    assertEquals("id", c.clientId());
    assertEquals("secret", c.clientSecret());
  }

  @Test
  void rejectsNonUuidCloudId() {
    assertThrows(ReportPortalException.class, () -> new OAuthCredentials("novisto", "id", "s"));
  }

  @Test
  void rejectsBlankSecretWithoutLeakingIt() {
    var e = assertThrows(ReportPortalException.class,
        () -> new OAuthCredentials(UUID_STR, "id", " "));
    assertTrue(e.getMessage().contains("clientSecret"));
  }

  @Test
  void rejectsNullClientId() {
    var e = assertThrows(ReportPortalException.class,
        () -> new OAuthCredentials(UUID_STR, null, "s"));
    assertTrue(e.getMessage().contains("clientId"));
  }

  @Test
  void apiBaseUrl() {
    assertEquals("https://api.atlassian.com/ex/jira/" + UUID_STR,
        new OAuthCredentials(UUID_STR, "id", "s").apiBaseUrl());
  }

  @Test
  void cacheKeyChangesWithSecretAndHidesIt() {
    var a = new OAuthCredentials(UUID_STR, "id", "s1").cacheKey();
    var b = new OAuthCredentials(UUID_STR, "id", "s2").cacheKey();
    assertNotEquals(a, b);
    assertFalse(a.contains("s1"));
  }
}
