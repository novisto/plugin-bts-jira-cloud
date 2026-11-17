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

public record OAuthJiraAuth(OAuthCredentials creds, OAuthTokenCache cache) implements JiraAuth {

  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public AuthType type() {
    return AuthType.OAUTH;
  }

  @Override
  public String apiBaseUrl() {
    return creds.apiBaseUrl();
  }

  @Override
  public String authorizationHeader() {
    return BEARER_PREFIX + cache.token(creds);
  }

  @Override
  public void invalidate(String rejectedAuthorizationHeader) {
    if (rejectedAuthorizationHeader != null && rejectedAuthorizationHeader.startsWith(BEARER_PREFIX)) {
      cache.invalidate(creds, rejectedAuthorizationHeader.substring(BEARER_PREFIX.length()));
    }
  }
}
