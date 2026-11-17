export const LABELS = {
  INTEGRATION_NAME: 'Integration Name',
  URL: 'Link to BTS',
  PROJECT: 'Project key in BTS',
  EMAIl: 'Email',
  TOKEN: 'API Token',
  AUTH_BY: 'Authorized by email',
  AUTH_BY_ACCOUNT: 'Authorized by',
  AUTH_TYPE: 'Authorization type',
  CLOUD_ID: 'Cloud ID',
  CLOUD_ID_HINT: 'From https://<site>.atlassian.net/_edge/tenant_info',
  CLIENT_ID: 'Client ID',
  CLIENT_SECRET: 'Client secret',
};

export const AUTH_TYPES = {
  BASIC: 'basic',
  OAUTH: 'oauth',
};

export const AUTH_TYPE_OPTIONS = [
  { value: AUTH_TYPES.BASIC, label: 'Email + API token' },
  { value: AUTH_TYPES.OAUTH, label: 'Service account (OAuth 2.0)' },
];

export const BASIC_FIELDS = ['email', 'apiToken'];
export const OAUTH_FIELDS = ['cloudId', 'clientId', 'clientSecret'];
