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
package com.epam.reportportal.extension.jira.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TestPropertiesTest {

  @Test
  void environmentOverridesFileValues() {
    Properties file = new Properties();
    file.setProperty("clientSecret", "");
    file.setProperty("project", "FILE");

    Properties result = TestProperties.applyEnvOverrides(file,
        Map.of("RP_JIRA_CLIENT_SECRET", "from-env", "RP_JIRA_PROJECT", "ENV")::get);

    assertEquals("from-env", result.getProperty("clientSecret"));
    assertEquals("ENV", result.getProperty("project"));
  }

  @Test
  void missingOrBlankEnvironmentKeepsFileValues() {
    Properties file = new Properties();
    file.setProperty("project", "FILE");
    file.setProperty("url", "");

    Properties result = TestProperties.applyEnvOverrides(file, Map.of("RP_JIRA_URL", " ")::get);

    assertEquals("FILE", result.getProperty("project"));
    assertEquals("", result.getProperty("url"));
  }
}
