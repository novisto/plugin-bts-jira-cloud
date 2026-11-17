# Plugin to integrate JIRA Cloud with Report Portal

## Novisto fork

This fork exists to add an Atlassian service-account OAuth 2.0 (client credentials) auth mode next to the original email + API token mode.

The long-lived branch is `novisto/5.13.x`; releases are versioned `5.13.10-novisto.N`.

Auth modes, selected by the `authType` integration parameter (absent means `basic`):

- `basic`: email + API token against the Jira site URL, unchanged from upstream.
- `oauth`: `clientId`, `clientSecret` and `cloudId` of an Atlassian service account; requests go through `https://api.atlassian.com/ex/jira/<cloudId>` and the secret is stored encrypted like the API token.

To release: bump `version` in `gradle.properties`, merge to `novisto/5.13.x`, then push the tag `v<version>`. The release workflow builds the plugin and attaches `build/libs/plugin-bts-jira-cloud-<version>.jar` to a GitHub release.

Upload that jar to ReportPortal under Administrate → Plugins.

## Build the plugin

Preconditions:
- Install JDK version 21.
- Specify version number in gradle.properties file.

Build the plugin: `gradlew build`
