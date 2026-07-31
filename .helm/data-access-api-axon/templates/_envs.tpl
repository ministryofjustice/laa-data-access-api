{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "axonDbConnectionDetails" }}
{{/*
Shared RDS PostgreSQL instance, isolated via a dedicated Axon schema.
Reuses the same secret as the API (temporary risk - see docs/axon-option-a-ai-execution-task-plan.md Task 5).
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
- name: AXON_DB_SCHEMA
  value: {{ .Values.axon.schema | quote }}
- name: AXON_FLYWAY_TABLE
  value: {{ .Values.axon.flywayTable | quote }}
- name: SPRING_DATASOURCE_URL
  value: "jdbc:postgresql://$(DB_HOST):5432/$(DB_NAME)?currentSchema={{ .Values.axon.schema }}"
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

