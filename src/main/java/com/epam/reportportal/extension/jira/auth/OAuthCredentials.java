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

import com.epam.reportportal.rules.exception.ErrorType;
import com.epam.reportportal.rules.exception.ReportPortalException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public record OAuthCredentials(String cloudId, String clientId, String clientSecret) {

  private static final String API_BASE = "https://api.atlassian.com/ex/jira/";

  public OAuthCredentials {
    cloudId = requireNonBlank("cloudId", cloudId);
    clientId = requireNonBlank("clientId", clientId);
    clientSecret = requireNonBlank("clientSecret", clientSecret);
    try {
      UUID.fromString(cloudId);
    } catch (IllegalArgumentException e) {
      throw invalid("cloudId must be a UUID");
    }
  }

  public String cacheKey() {
    return cloudId + "|" + clientId + "|" + sha256Hex(clientSecret);
  }

  public String apiBaseUrl() {
    return API_BASE + cloudId;
  }

  @Override
  public String toString() {
    return "OAuthCredentials[cloudId=" + cloudId + ", clientId=" + clientId + "]";
  }

  private static String requireNonBlank(String field, String value) {
    if (value == null || value.isBlank()) {
      throw invalid(field + " must not be blank");
    }
    return value.trim();
  }

  private static ReportPortalException invalid(String message) {
    return new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, message);
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
