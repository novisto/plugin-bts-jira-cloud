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

import com.epam.reportportal.extension.jira.command.utils.JIRATicketUtils;

public record BasicJiraAuth(String apiBaseUrl, String email, String apiToken) implements JiraAuth {

  @Override
  public AuthType type() {
    return AuthType.BASIC;
  }

  @Override
  public String authorizationHeader() {
    return "Basic " + JIRATicketUtils.getAuthorizationHeader(email, apiToken);
  }

  @Override
  public void invalidate(String rejectedAuthorizationHeader) {
  }

  @Override
  public String toString() {
    return "BasicJiraAuth[apiBaseUrl=" + apiBaseUrl + ", email=" + email + "]";
  }
}
