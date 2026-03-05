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
  Define OAuth2 client and provider environment variables
*/}}
{{- define "oauth2Config" }}
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
