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
