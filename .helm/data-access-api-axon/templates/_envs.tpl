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
