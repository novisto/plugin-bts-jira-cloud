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

package com.epam.reportportal.extension.jira.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthSecretsToStringTest {

  @Test
  void basicJiraAuthToStringOmitsApiToken() {
    var text = new BasicJiraAuth("https://example.atlassian.net", "a@b.c", "api-token-value").toString();
    assertFalse(text.contains("api-token-value"));
  }

  @Test
  void accessTokenToStringOmitsValue() {
    var text = new AccessToken("access-token-value", Instant.EPOCH).toString();
    assertFalse(text.contains("access-token-value"));
  }
}
