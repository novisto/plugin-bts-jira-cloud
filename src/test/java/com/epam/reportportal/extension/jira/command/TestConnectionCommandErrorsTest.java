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

package com.epam.reportportal.extension.jira.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.epam.reportportal.extension.jira.command.utils.CloudJiraClientProvider;
import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class TestConnectionCommandErrorsTest {

  private CloudJiraClientProvider provider;
  private TestConnectionCommand command;
  private Integration integration;

  @BeforeEach
  void setUp() {
    provider = mock(CloudJiraClientProvider.class);
    command = new TestConnectionCommand(provider);
    Map<String, Object> params = new HashMap<>();
    params.put("url", "https://example.atlassian.net");
    params.put("project", "PRJ");
    integration = new Integration();
    integration.setParams(new IntegrationParams(params));
  }

  @Test
  void surfacesReportPortalExceptionUnchanged() {
    var oauthFailure = new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
        "OAuth token request failed: HTTP 401, access_denied");
    when(provider.getApiClient(any())).thenThrow(oauthFailure);

    var thrown = assertThrows(ReportPortalException.class,
        () -> command.executeCommand(integration, new HashMap<>()));

    assertEquals(oauthFailure.getMessage(), thrown.getMessage());
  }

  @Test
  void otherFailuresBecomeGenericMessage() {
    when(provider.getApiClient(any())).thenThrow(new IllegalStateException("boom"));

    var thrown = assertThrows(ReportPortalException.class,
        () -> command.executeCommand(integration, new HashMap<>()));

    assertEquals(
        new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Unable to connect to Cloud Jira").getMessage(),
        thrown.getMessage());
  }

  @Test
  void jiraHttpErrorReportsStatusAndProject() {
    when(provider.getApiClient(any())).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND,
        "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

    var thrown = assertThrows(ReportPortalException.class,
        () -> command.executeCommand(integration, new HashMap<>()));

    assertEquals(
        new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Cloud Jira returned HTTP 404 for project PRJ").getMessage(),
        thrown.getMessage());
  }
}
