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
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum AuthType {

  BASIC("basic"),
  OAUTH("oauth");

  private final String value;

  AuthType(String value) {
    this.value = value;
  }

  public static AuthType from(Optional<String> raw) {
    return raw.map(String::trim)
        .filter(v -> !v.isEmpty())
        .map(v -> Arrays.stream(values())
            .filter(t -> t.value.equalsIgnoreCase(v))
            .findFirst()
            .orElseThrow(() -> new ReportPortalException(
                ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "Unknown auth type: " + v)))
        .orElse(BASIC);
  }
}
