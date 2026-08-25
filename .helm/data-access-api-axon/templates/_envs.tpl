{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "axonDbConnectionDetails" }}
{{- if eq .Values.spring.profile "preview" }}
{{/*
Preview releases use the same ephemeral PostgreSQL instance as the main API,
with Axon isolated in its own schema.
*/}}
- name: DB_NAME
  value: "postgres"
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ required "db.postgresqlReleaseName is required for the preview profile" .Values.db.postgresqlReleaseName }}
      key: postgres-password
- name: DB_HOST
  value: {{ required "db.postgresqlReleaseName is required for the preview profile" .Values.db.postgresqlReleaseName }}
- name: DB_URL
  value: "jdbc:postgresql://$(DB_HOST):5432/$(DB_NAME)?currentSchema={{ .Values.axon.schema }}"
{{- else }}
{{/*
Persistent environments use shared RDS, isolated via a dedicated Axon schema.
*/}}
- name: DB_NAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_name
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_username
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_password
- name: DB_HOST
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: rds_instance_address
- name: SPRING_DATASOURCE_URL
  value: "jdbc:postgresql://$(DB_HOST):5432/$(DB_NAME)?currentSchema={{ .Values.axon.schema }}"
{{- end }}
- name: AXON_DB_SCHEMA
  value: {{ .Values.axon.schema | quote }}
- name: AXON_FLYWAY_TABLE
  value: {{ .Values.axon.flywayTable | quote }}
{{- end }}

{{/*
  Define OAuth2/Entra ID environment variables for authentication

  UAT/Testing: Always uses shared mock-oauth2 (NO real Entra ID)
  Production: Uses real Azure Entra ID from secrets
*/}}
{{- define "axonOauth2Config" }}
{{- if and .Values.mockOAuth2 .Values.mockOAuth2.sharedInstance .Values.mockOAuth2.sharedInstance.enabled }}
- name: ENTRA_ISSUER_URI
  value: "http://{{ .Values.mockOAuth2.sharedInstance.serviceName }}.{{ .Values.mockOAuth2.sharedInstance.namespace }}.svc.cluster.local:9999/entra"
- name: ENTRA_JWK_SET_URI
  value: "http://{{ .Values.mockOAuth2.sharedInstance.serviceName }}.{{ .Values.mockOAuth2.sharedInstance.namespace }}.svc.cluster.local:9999/entra/jwks"
- name: ENTRA_AUD
  value: "laa-data-access-api"
{{- else }}
- name: ENTRA_ISSUER_URI
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: ENTRA_ISSUER_URI
- name: ENTRA_JWK_SET_URI
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: ENTRA_JWK_SET_URI
- name: ENTRA_AUD
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: ENTRA_AUD
{{- end }}
{{- end }}

{{/*
  Define feature environment variables for flags
*/}}
{{- define "axonFeatureConfig" }}
{{- if and .Values.featureFlags (hasKey .Values.featureFlags "enable_dev_token") }}
- name: FEATURE_ENABLE_DEV_TOKEN
  value: {{ .Values.featureFlags.enable_dev_token | quote }}
{{- else }}
- name: FEATURE_ENABLE_DEV_TOKEN
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: FEATURE_ENABLE_DEV_TOKEN
{{- end }}
{{- if and .Values.featureFlags (hasKey .Values.featureFlags "disable_security") }}
- name: FEATURE_DISABLE_SECURITY
  value: {{ .Values.featureFlags.disable_security | quote }}
{{- else }}
- name: FEATURE_DISABLE_SECURITY
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: FEATURE_DISABLE_SECURITY
{{- end }}
{{- if .Values.featureFlags }}
{{- range $key, $value := .Values.featureFlags }}
{{- if and (ne $key "enable_dev_token") (ne $key "disable_security") }}
- name: FEATURE_{{ upper $key }}
  value: {{ $value | quote }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}

{{/*
  Define Sentry environment variables
*/}}
{{- define "axonSentryConfig" }}
{{- if .Values.sentry.enabled }}
- name: SENTRY_ENABLED
  value: "true"
- name: SENTRY_DSN
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: SENTRY_DSN
- name: SENTRY_ENVIRONMENT
  value: {{ .Values.sentry.environment | quote }}
- name: SENTRY_TRACES_SAMPLE_RATE
  value: {{ .Values.sentry.tracesSampleRate | quote }}
{{- else }}
- name: SENTRY_ENABLED
  value: "false"
{{- end }}
{{- end }}
