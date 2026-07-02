{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "dbConnectionDetails" }}
{{- if eq .Values.spring.profile "preview" }}
{{/*
For the preview branches, set DB connection details to Bitnami Postgres specific values
*/}}
- name: DB_NAME
  value: "postgres"
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Release.Name }}-postgresql
      key: postgres-password
- name: DB_HOST
  value: {{ .Release.Name }}-postgresql
{{- else if or (eq .Values.spring.profile "main") (eq .Values.spring.profile "unsecured") }}
{{/*
For the main branch, extract DB environment variables from rds-postgresql-instance-output secret
*/}}
- name: DB_NAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_name
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_username
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_password
- name: DB_HOST
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: rds_instance_address
{{- end }}
{{- end }}

{{/*
  Define Sentry environment variables
*/}}
{{- define "sentryConfig" }}
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

{{/*
  Define OAuth2/Entra ID environment variables for authentication
*/}}
{{- define "oauth2Config" }}
{{- if and .Values.mockOAuth2 .Values.mockOAuth2.sharedInstance .Values.mockOAuth2.sharedInstance.enabled }}
{{/* Use shared mock-oauth2 instance across all deployments */}}
- name: ENTRA_ISSUER_URI
  value: "http://{{ .Values.mockOAuth2.sharedInstance.serviceName }}.{{ .Values.mockOAuth2.sharedInstance.namespace }}.svc.cluster.local:9999/entra"
- name: ENTRA_JWK_SET_URI
  value: "http://{{ .Values.mockOAuth2.sharedInstance.serviceName }}.{{ .Values.mockOAuth2.sharedInstance.namespace }}.svc.cluster.local:9999/entra/jwks"
- name: ENTRA_AUD
  value: "laa-data-access-api"
{{- else if and .Values.mockOAuth2 .Values.mockOAuth2.enabled }}
{{/* Use per-deployment mock-oauth2 instance (legacy/preview mode) */}}
- name: ENTRA_ISSUER_URI
  value: "http://{{ include "data-access-api.fullname" . }}-mock-oauth2:9999/entra"
- name: ENTRA_JWK_SET_URI
  value: "http://{{ include "data-access-api.fullname" . }}-mock-oauth2:9999/entra/jwks"
- name: ENTRA_AUD
  value: "laa-data-access-api"
{{- else }}
{{/* Use real Azure Entra ID */}}
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
- name: AUTH_CLIENT_ID
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: AUTH_CLIENT_ID
- name: AUTH_CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: AUTH_CLIENT_SECRET
- name: AUTH_SCOPE
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: AUTH_SCOPE
- name: AUTH_TENANT_ID
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: AUTH_TENANT_ID
{{- end }}

{{/*
  Define feature environment variables for flags
*/}}
{{- define "featureConfig" }}
{{- if and .Values.featureFlags (hasKey .Values.featureFlags "enable_dev_token") }}
{{/* Override from values.yaml if explicitly set */}}
- name: FEATURE_ENABLE_DEV_TOKEN
  value: {{ .Values.featureFlags.enable_dev_token | quote }}
{{- else }}
{{/* Default to secret value */}}
- name: FEATURE_ENABLE_DEV_TOKEN
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: FEATURE_ENABLE_DEV_TOKEN
{{- end }}
- name: FEATURE_DISABLE_SECURITY
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: FEATURE_DISABLE_SECURITY
{{- if .Values.featureFlags }}
{{- range $key, $value := .Values.featureFlags }}
{{- if ne $key "enable_dev_token" }}
- name: FEATURE_{{ upper $key }}
  value: {{ $value | quote }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}

{{/*
  Define additional environment variables for short-lived environments
*/}}
{{- define "extraEnvConfig" }}
{{- if .Values.extraEnv }}
{{- range $key, $value := .Values.extraEnv }}
- name: {{ $key }}
  value: {{ $value | quote }}
{{- end }}
{{- end }}
{{- end }}

{{/*
  Define SDS API environment variables
*/}}
{{- define "sdsApiConfig" }}
- name: SDS_API_URL
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: SDS_API_URL
- name: SDS_API_BUCKET
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: SDS_API_BUCKET
- name: SDS_API_CLIENT_REGISTRATION_ID
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: SDS_API_CLIENT_REGISTRATION_ID
- name: SDS_API_PRINCIPAL_NAME
  valueFrom:
    secretKeyRef:
      name: laa-data-access-api-secrets
      key: SDS_API_PRINCIPAL_NAME
{{- end }}