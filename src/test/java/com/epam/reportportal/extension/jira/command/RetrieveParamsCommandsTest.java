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
package com.epam.reportportal.extension.jira.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.epam.reportportal.rules.exception.ReportPortalException;
import java.util.HashMap;
import java.util.Map;
import org.jasypt.util.text.BasicTextEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrieveParamsCommandsTest {

	private static final String CLOUD_ID = "0b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d";

	private BasicTextEncryptor encryptor;
	private RetrieveCreationParamsCommand create;
	private RetrieveUpdateParamsCommand update;

	@BeforeEach
	void setUp() {
		encryptor = new BasicTextEncryptor();
		encryptor.setPassword("123");
		create = new RetrieveCreationParamsCommand(encryptor);
		update = new RetrieveUpdateParamsCommand(encryptor);
	}

	private static Map<String, Object> oauthParams() {
		Map<String, Object> params = new HashMap<>();
		params.put("authType", "oauth");
		params.put("project", "PRJ");
		params.put("url", "https://example.atlassian.net");
		params.put("clientId", " client-1 ");
		params.put("clientSecret", "s3cret");
		params.put("cloudId", " " + CLOUD_ID + " ");
		return params;
	}

	private static Map<String, Object> oauthParamsWithout(String key) {
		Map<String, Object> params = oauthParams();
		params.remove(key);
		return params;
	}

	@Test
	void createBasicUnchangedPlusAuthType() {
		Map<String, Object> params = new HashMap<>();
		params.put("project", "PRJ");
		params.put("url", "https://example.atlassian.net");
		params.put("email", "a@b.c");
		params.put("apiToken", "tok");

		Map<String, Object> result = create.executeCommand(params);

		assertEquals(5, result.size());
		assertEquals("PRJ", result.get("project"));
		assertEquals("https://example.atlassian.net", result.get("url"));
		assertEquals("a@b.c", result.get("email"));
		assertEquals("tok", encryptor.decrypt((String) result.get("apiToken")));
		assertEquals("basic", result.get("authType"));
		assertFalse(result.containsKey("clientId"));
		assertFalse(result.containsKey("clientSecret"));
		assertFalse(result.containsKey("cloudId"));
	}

	@Test
	void createOauth() {
		Map<String, Object> result = create.executeCommand(oauthParams());

		assertEquals(6, result.size());
		assertEquals("oauth", result.get("authType"));
		assertEquals("PRJ", result.get("project"));
		assertEquals("https://example.atlassian.net", result.get("url"));
		assertEquals("client-1", result.get("clientId"));
		assertEquals(CLOUD_ID, result.get("cloudId"));
		assertEquals("s3cret", encryptor.decrypt((String) result.get("clientSecret")));
		assertFalse(result.containsKey("email"));
		assertFalse(result.containsKey("apiToken"));
	}

	@Test
	void createOauthMissingCloudIdThrows() {
		assertThrows(ReportPortalException.class, () -> create.executeCommand(oauthParamsWithout("cloudId")));
	}

	@Test
	void updateEncryptsClientSecretWhenPresent() {
		Map<String, Object> result = update.executeCommand(Map.of("clientSecret", "x"));

		assertEquals("x", encryptor.decrypt((String) result.get("clientSecret")));
	}

	@Test
	void updatePassesAuthTypeClientIdCloudId() {
		Map<String, Object> result = update.executeCommand(Map.of("authType", "oauth", "clientId", "c", "cloudId", CLOUD_ID));

		assertEquals(Map.of("authType", "oauth", "clientId", "c", "cloudId", CLOUD_ID), result);
	}

	@Test
	void updateKeepsBasicBehaviour() {
		Map<String, Object> fields = Map.of("k", "v");
		Map<String, Object> result = update.executeCommand(
				Map.of("url", "u", "project", "p", "email", "e", "apiToken", "t", "defectFormFields", fields));

		assertEquals(5, result.size());
		assertEquals("u", result.get("url"));
		assertEquals("p", result.get("project"));
		assertEquals("e", result.get("email"));
		assertEquals("t", encryptor.decrypt((String) result.get("apiToken")));
		assertEquals(fields, result.get("defectFormFields"));
	}

	@Test
	void updateRejectsUnknownAuthType() {
		assertThrows(ReportPortalException.class, () -> update.executeCommand(Map.of("authType", "kerberos")));
	}

	@Test
	void updateOauthStoresTrimmedValues() {
		Map<String, Object> result = update.executeCommand(
				Map.of("authType", "oauth", "clientId", " client-1 ", "clientSecret", " s3cret ", "cloudId", " " + CLOUD_ID + " "));

		assertEquals("oauth", result.get("authType"));
		assertEquals("client-1", result.get("clientId"));
		assertEquals(CLOUD_ID, result.get("cloudId"));
		assertEquals("s3cret", encryptor.decrypt((String) result.get("clientSecret")));
	}

	@Test
	void updateOauthRejectsNonUuidCloudId() {
		assertThrows(ReportPortalException.class,
				() -> update.executeCommand(Map.of("authType", "oauth", "clientId", "c", "clientSecret", "s", "cloudId", "novisto")));
	}

	@Test
	void updateRejectsNonUuidCloudIdWithoutSecret() {
		assertThrows(ReportPortalException.class, () -> update.executeCommand(Map.of("cloudId", "novisto")));
	}

	@Test
	void updateStoresBlankCloudIdAndClientIdWhenSwitchingToBasic() {
		Map<String, Object> result = update.executeCommand(Map.of("authType", "basic", "clientId", "", "cloudId", ""));

		assertEquals(Map.of("authType", "basic", "clientId", "", "cloudId", ""), result);
	}

	@Test
	void updateStoresTrimmedCloudIdAndClientIdWithoutSecret() {
		Map<String, Object> result = update.executeCommand(Map.of("clientId", " client-1 ", "cloudId", " " + CLOUD_ID + " "));

		assertEquals(Map.of("clientId", "client-1", "cloudId", CLOUD_ID), result);
	}

	@Test
	void updateWithOnlyDefectFormFieldsIsUnchanged() {
		Map<String, Object> fields = Map.of("k", "v");

		assertEquals(Map.of("defectFormFields", fields), update.executeCommand(Map.of("defectFormFields", fields)));
	}

	@Test
	void updateStoresNormalizedAuthType() {
		Map<String, Object> result = update.executeCommand(Map.of("authType", " OAUTH "));

		assertEquals("oauth", result.get("authType"));
	}
}
