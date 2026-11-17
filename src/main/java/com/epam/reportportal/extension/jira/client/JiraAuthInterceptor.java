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

import com.epam.reportportal.extension.jira.auth.JiraAuth;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class JiraAuthInterceptor implements ClientHttpRequestInterceptor {

  private final JiraAuth auth;

  public JiraAuthInterceptor(JiraAuth auth) {
    this.auth = auth;
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    String sentHeader = auth.authorizationHeader();
    request.getHeaders().set(HttpHeaders.AUTHORIZATION, sentHeader);
    ClientHttpResponse response = execution.execute(request, body);
    if (response.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
      return response;
    }
    response.close();
    auth.invalidate(sentHeader);
    request.getHeaders().set(HttpHeaders.AUTHORIZATION, auth.authorizationHeader());
    return execution.execute(request, body);
  }
}
