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


import com.epam.reportportal.extension.jira.api.IssueAttachmentsApi;
import com.epam.reportportal.extension.jira.api.IssueLinksApi;
import com.epam.reportportal.extension.jira.api.IssueSearchApi;
import com.epam.reportportal.extension.jira.api.IssuesApi;
import com.epam.reportportal.extension.jira.api.ProjectsApi;
import com.epam.reportportal.extension.jira.api.UserSearchApi;
import com.epam.reportportal.extension.jira.api.UsersApi;
import com.epam.reportportal.extension.jira.api.client.ApiClient;
import com.epam.reportportal.extension.jira.auth.AuthType;
import com.epam.reportportal.extension.jira.auth.BasicJiraAuth;
import com.epam.reportportal.extension.jira.auth.JiraAuth;
import lombok.Getter;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Getter
public class JiraRestClient {

  private final ApiClient apiClient;

  public JiraRestClient(JiraAuth auth) {
    if (auth.type() == AuthType.OAUTH) {
      this.apiClient = new ApiClient(oauthRestTemplate(auth));
      this.apiClient.setBasePath(auth.apiBaseUrl());
    } else {
      BasicJiraAuth basic = (BasicJiraAuth) auth;
      this.apiClient = new ApiClient();
      this.apiClient.setBasePath(basic.apiBaseUrl());
      this.apiClient.setUsername(basic.email());
      this.apiClient.setPassword(basic.apiToken());
    }
    this.apiClient.setDebugging(Boolean.parseBoolean(System.getenv().getOrDefault("DEBUGGER_ENABLED", "false")));
  }

  private static RestTemplate oauthRestTemplate(JiraAuth auth) {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));
    DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
    restTemplate.setUriTemplateHandler(uriBuilderFactory);
    restTemplate.getInterceptors().add(new JiraAuthInterceptor(auth));
    return restTemplate;
  }

  public ProjectsApi projectsApi() {
    return new ProjectsApi(apiClient);
  }

  public UsersApi usersApi() {
    return new UsersApi(apiClient);
  }

  public UserSearchApi userSearchApi() {
    return new UserSearchApi(apiClient);
  }

  public IssuesApi issuesApi() {
    return new IssuesApi(apiClient);
  }

  public IssueSearchApi issueSearchApi() {
    return new IssueSearchApi(apiClient);
  }

  public IssueLinksApi issueLinksApi() {
    return new IssueLinksApi(apiClient);
  }

  public IssueAttachmentsApi issueAttachmentsApi() {
    return new IssueAttachmentsApi(apiClient);
  }

}
