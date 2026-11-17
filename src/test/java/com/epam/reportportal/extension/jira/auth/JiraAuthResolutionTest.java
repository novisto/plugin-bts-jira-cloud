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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.epam.reportportal.extension.jira.command.utils.CloudJiraClientProvider;
import com.epam.reportportal.rules.exception.ReportPortalException;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.jasypt.util.text.BasicTextEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraAuthResolutionTest {

  private static final String CLOUD_ID = "11111111-2222-3333-4444-555555555555";
  private static final String URL = "https://acme.atlassian.net";

  private final BasicTextEncryptor encryptor = new BasicTextEncryptor();
  private final OAuthTokenCache cache = mock(OAuthTokenCache.class);
  private CloudJiraClientProvider provider;

  @BeforeEach
  void setUp() {
    encryptor.setPassword("pw");
    provider = new CloudJiraClientProvider(encryptor, cache);
  }

  private Map<String, Object> basicParams() {
    Map<String, Object> params = new HashMap<>();
    params.put("url", URL);
    params.put("email", "e");
    params.put("apiToken", encryptor.encrypt("t"));
    return params;
  }

  private Map<String, Object> oauthParams() {
    Map<String, Object> params = new HashMap<>();
    params.put("authType", "oauth");
    params.put("cloudId", CLOUD_ID);
    params.put("clientId", "cid");
    params.put("clientSecret", encryptor.encrypt("secret"));
    return params;
  }

  @Test
  void paramsWithoutAuthTypeResolveToBasic() {
    JiraAuth auth = provider.resolveAuth(new IntegrationParams(basicParams()));

    assertInstanceOf(BasicJiraAuth.class, auth);
    assertEquals(AuthType.BASIC, auth.type());
    assertEquals(URL, auth.apiBaseUrl());
    assertEquals("Basic " + Base64.getEncoder().encodeToString("e:t".getBytes(StandardCharsets.UTF_8)),
        auth.authorizationHeader());
  }

  @Test
  void oauthResolvesToGateway() {
    when(cache.token(new OAuthCredentials(CLOUD_ID, "cid", "secret"))).thenReturn("t");

    JiraAuth auth = provider.resolveAuth(new IntegrationParams(oauthParams()));

    assertInstanceOf(OAuthJiraAuth.class, auth);
    assertEquals(AuthType.OAUTH, auth.type());
    assertEquals("https://api.atlassian.com/ex/jira/" + CLOUD_ID, auth.apiBaseUrl());
    assertEquals("Bearer t", auth.authorizationHeader());
  }

  @Test
  void oauthTrimsStoredValues() {
    Map<String, Object> params = oauthParams();
    params.put("cloudId", " " + CLOUD_ID + " ");
    params.put("clientId", " cid ");
    when(cache.token(new OAuthCredentials(CLOUD_ID, "cid", "secret"))).thenReturn("t");

    JiraAuth auth = provider.resolveAuth(new IntegrationParams(params));

    assertEquals("https://api.atlassian.com/ex/jira/" + CLOUD_ID, auth.apiBaseUrl());
    assertEquals("Bearer t", auth.authorizationHeader());
  }

  @Test
  void staleBasicParamsIgnoredInOauthMode() {
    Map<String, Object> params = oauthParams();
    params.putAll(basicParams());

    JiraAuth auth = provider.resolveAuth(new IntegrationParams(params));

    assertInstanceOf(OAuthJiraAuth.class, auth);
  }

  @Test
  void staleOauthParamsIgnoredInBasicMode() {
    Map<String, Object> params = basicParams();
    params.put("authType", "basic");
    params.put("clientId", "cid");
    params.put("cloudId", CLOUD_ID);

    JiraAuth auth = provider.resolveAuth(new IntegrationParams(params));

    assertInstanceOf(BasicJiraAuth.class, auth);
    assertEquals(URL, auth.apiBaseUrl());
  }

  @Test
  void unknownAuthTypeThrows() {
    Map<String, Object> params = basicParams();
    params.put("authType", "kerberos");

    assertThrows(ReportPortalException.class, () -> provider.resolveAuth(new IntegrationParams(params)));
  }

  @Test
  void oauthMissingSecretThrows() {
    Map<String, Object> params = oauthParams();
    params.remove("clientSecret");

    assertThrows(ReportPortalException.class, () -> provider.resolveAuth(new IntegrationParams(params)));
  }

  @Test
  void oauthInvalidCloudIdThrows() {
    Map<String, Object> params = oauthParams();
    params.put("cloudId", "not-a-uuid");

    assertThrows(ReportPortalException.class, () -> provider.resolveAuth(new IntegrationParams(params)));
  }

  @Test
  void oauthHeaderIsLazy() {
    when(cache.token(new OAuthCredentials(CLOUD_ID, "cid", "secret"))).thenReturn("t");

    JiraAuth auth = provider.resolveAuth(new IntegrationParams(oauthParams()));

    verifyNoInteractions(cache);
    auth.authorizationHeader();
    verify(cache).token(new OAuthCredentials(CLOUD_ID, "cid", "secret"));
  }

  @Test
  void oauthInvalidateDelegatesToCache() {
    JiraAuth auth = provider.resolveAuth(new IntegrationParams(oauthParams()));

    auth.invalidate();

    verify(cache).invalidate(new OAuthCredentials(CLOUD_ID, "cid", "secret"));
  }
}
