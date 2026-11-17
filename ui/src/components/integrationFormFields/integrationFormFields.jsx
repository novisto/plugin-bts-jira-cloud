import React, { useEffect, useState } from 'react';
import { AUTH_TYPE_OPTIONS, AUTH_TYPES, BASIC_FIELDS, LABELS, OAUTH_FIELDS } from '../constants';
import { btsJiraCloudUrl, cloudIdValidator } from '../utils';

export const IntegrationFormFields = (props) => {
  const { initialize, change, disabled, initialData, updateMetaData, ...extensionProps } = props;
  const {
    components: { Dropdown, FieldErrorHint, FieldElement, FieldText, FieldTextFlex },
    validators: { requiredField, btsProjectKey, btsIntegrationName, email },
    constants: { SECRET_FIELDS_KEY },
  } = extensionProps;

  const [authType, setAuthType] = useState(initialData?.authType || AUTH_TYPES.BASIC);

  useEffect(() => {
    initialize({ authType: AUTH_TYPES.BASIC, ...initialData });
    updateMetaData({
      [SECRET_FIELDS_KEY]: ['apiToken', 'clientSecret'],
    });
  }, []);

  const onAuthTypeChange = (event, newAuthType) => {
    setAuthType(newAuthType);
    const staleFields = newAuthType === AUTH_TYPES.OAUTH ? BASIC_FIELDS : OAUTH_FIELDS;
    staleFields.forEach((field) => change(field, ''));
  };

  return (
    <>
      <FieldElement
        name="integrationName"
        label={LABELS.INTEGRATION_NAME}
        validate={btsIntegrationName}
        disabled={disabled}
        isRequired
        dataAutomationId="integrationNameField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldText defaultWidth={false} />
        </FieldErrorHint>
      </FieldElement>
      <FieldElement
        name="url"
        label={LABELS.URL}
        validate={btsJiraCloudUrl}
        disabled={disabled}
        isRequired
        dataAutomationId="linkToBTSField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldText defaultWidth={false} />
        </FieldErrorHint>
      </FieldElement>
      <FieldElement
        name="project"
        label={LABELS.PROJECT}
        validate={btsProjectKey}
        disabled={disabled}
        isRequired
        dataAutomationId="projectKeyInBTSField"
      >
        <FieldErrorHint provideHint={false}>
          <FieldText defaultWidth={false} />
        </FieldErrorHint>
      </FieldElement>
      <FieldElement
        name="authType"
        label={LABELS.AUTH_TYPE}
        disabled={disabled}
        onChange={onAuthTypeChange}
        isRequired
        dataAutomationId="authTypeBTSField"
      >
        <Dropdown options={AUTH_TYPE_OPTIONS} defaultWidth={false} />
      </FieldElement>
      {authType === AUTH_TYPES.BASIC && (
        <>
          <FieldElement
            name="email"
            label={LABELS.EMAIl}
            validate={email}
            disabled={disabled}
            isRequired
            dataAutomationId="emailBTSField"
          >
            <FieldErrorHint provideHint={false}>
              <FieldText defaultWidth={false} />
            </FieldErrorHint>
          </FieldElement>
          <FieldElement
            name="apiToken"
            label={LABELS.TOKEN}
            disabled={disabled}
            validate={requiredField}
            isRequired
            dataAutomationId="apiTokenBTSField"
          >
            <FieldErrorHint provideHint={false}>
              <FieldTextFlex />
            </FieldErrorHint>
          </FieldElement>
        </>
      )}
      {authType === AUTH_TYPES.OAUTH && (
        <>
          <FieldElement
            name="cloudId"
            label={LABELS.CLOUD_ID}
            description={LABELS.CLOUD_ID_HINT}
            validate={cloudIdValidator}
            disabled={disabled}
            isRequired
            dataAutomationId="cloudIdBTSField"
          >
            <FieldErrorHint provideHint={false}>
              <FieldText defaultWidth={false} />
            </FieldErrorHint>
          </FieldElement>
          <FieldElement
            name="clientId"
            label={LABELS.CLIENT_ID}
            validate={requiredField}
            disabled={disabled}
            isRequired
            dataAutomationId="clientIdBTSField"
          >
            <FieldErrorHint provideHint={false}>
              <FieldText defaultWidth={false} />
            </FieldErrorHint>
          </FieldElement>
          <FieldElement
            name="clientSecret"
            label={LABELS.CLIENT_SECRET}
            validate={requiredField}
            disabled={disabled}
            isRequired
            dataAutomationId="clientSecretBTSField"
          >
            <FieldErrorHint provideHint={false}>
              <FieldTextFlex />
            </FieldErrorHint>
          </FieldElement>
        </>
      )}
    </>
  );
};
