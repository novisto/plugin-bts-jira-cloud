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

import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.jira.auth.AuthType;
import com.epam.reportportal.extension.jira.auth.OAuthCredentials;
import com.epam.reportportal.extension.jira.command.utils.CloudJiraProperties;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import org.jasypt.util.text.BasicTextEncryptor;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class RetrieveUpdateParamsCommand implements CommonPluginCommand<Map<String, Object>> {

	private final BasicTextEncryptor textEncryptor;

	public RetrieveUpdateParamsCommand(BasicTextEncryptor textEncryptor) {
		this.textEncryptor = textEncryptor;
	}

	@Override
	public String getName() {
		return "retrieveUpdated";
	}

	@Override
	//@param integration is always null because it can be not saved yet
	public Map<String, Object> executeCommand(Map<String, Object> integrationParams) {
		Map<String, Object> resultParams = Maps.newHashMapWithExpectedSize(integrationParams.size());
		CloudJiraProperties.URL.getParam(integrationParams).ifPresent(url -> resultParams.put(CloudJiraProperties.URL.getName(), url));
		CloudJiraProperties.PROJECT.getParam(integrationParams)
				.ifPresent(url -> resultParams.put(CloudJiraProperties.PROJECT.getName(), url));
		CloudJiraProperties.EMAIL.getParam(integrationParams).ifPresent(url -> resultParams.put(CloudJiraProperties.EMAIL.getName(), url));
		CloudJiraProperties.API_TOKEN.getParam(integrationParams)
				.ifPresent(token -> resultParams.put(CloudJiraProperties.API_TOKEN.getName(), textEncryptor.encrypt(token)));
		CloudJiraProperties.AUTH_TYPE.getParam(integrationParams)
				.ifPresent(authType -> resultParams.put(CloudJiraProperties.AUTH_TYPE.getName(), authType));
		CloudJiraProperties.CLIENT_ID.getParam(integrationParams)
				.ifPresent(clientId -> resultParams.put(CloudJiraProperties.CLIENT_ID.getName(), clientId.isBlank() ? clientId : OAuthCredentials.normalizeClientId(clientId)));
		CloudJiraProperties.CLOUD_ID.getParam(integrationParams)
				.ifPresent(cloudId -> resultParams.put(CloudJiraProperties.CLOUD_ID.getName(), cloudId.isBlank() ? cloudId : OAuthCredentials.normalizeCloudId(cloudId)));
		CloudJiraProperties.CLIENT_SECRET.getParam(integrationParams)
				.ifPresent(secret -> resultParams.put(CloudJiraProperties.CLIENT_SECRET.getName(), textEncryptor.encrypt(secret)));
		boolean oauth = CloudJiraProperties.AUTH_TYPE.getParam(integrationParams)
				.map(authType -> AuthType.from(Optional.of(authType)) == AuthType.OAUTH)
				.orElse(false);
		if (oauth
				&& CloudJiraProperties.CLOUD_ID.getParam(integrationParams).isPresent()
				&& CloudJiraProperties.CLIENT_ID.getParam(integrationParams).isPresent()
				&& CloudJiraProperties.CLIENT_SECRET.getParam(integrationParams).isPresent()) {
			OAuthCredentials credentials = new OAuthCredentials(
					CloudJiraProperties.CLOUD_ID.getParam(integrationParams).get(),
					CloudJiraProperties.CLIENT_ID.getParam(integrationParams).get(),
					CloudJiraProperties.CLIENT_SECRET.getParam(integrationParams).get()
			);
			resultParams.put(CloudJiraProperties.CLOUD_ID.getName(), credentials.cloudId());
			resultParams.put(CloudJiraProperties.CLIENT_ID.getName(), credentials.clientId());
			resultParams.put(CloudJiraProperties.CLIENT_SECRET.getName(), textEncryptor.encrypt(credentials.clientSecret()));
		}
		Optional.ofNullable(integrationParams.get("defectFormFields"))
				.ifPresent(defectFormFields -> resultParams.put("defectFormFields", defectFormFields));
		return resultParams;
	}
}
