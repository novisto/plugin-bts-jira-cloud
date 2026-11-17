const regex = (regexStr) => (value) => RegExp(regexStr).test(value);
const trimValue = (value) => (typeof value === 'string' ? value.trim() : value);
const isEmpty = (value) => {
  const trimmedValue = trimValue(value);
  return trimmedValue === '' || trimmedValue === undefined || trimmedValue === null;
};
const isNotEmpty = (value) => !isEmpty(value);
const composeValidators = (validators) => (value) =>
  validators.every((validator) => validator(value));

const jiraCloudUrl = composeValidators([
  isNotEmpty,
  regex(/^https:\/\/[^?]*\.(atlassian\.(com|net)|jira\.com)\/?$/),
]);

const bindMessageToValidator = (validator, errorMessage) => (value) =>
  !validator(value) ? errorMessage : undefined;

export const btsJiraCloudUrl = bindMessageToValidator(jiraCloudUrl, 'btsUrlHint');

const cloudId = composeValidators([
  isNotEmpty,
  regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i),
]);

export const cloudIdValidator = bindMessageToValidator(cloudId, 'Cloud ID must be a UUID');
