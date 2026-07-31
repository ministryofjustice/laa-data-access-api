{{/*
_helpers.tpl
This file contains Helm template helpers that can be reused throughout the chart.
*/}}

{{- define "data-access-api-axon.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "data-access-api-axon.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "data-access-api-axon.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "data-access-api-axon.labels" -}}
helm.sh/chart: {{ include "data-access-api-axon.chart" . }}
{{ include "data-access-api-axon.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "data-access-api-axon.selectorLabels" -}}
app.kubernetes.io/name: {{ include "data-access-api-axon.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: {{ template "data-access-api-axon.name" . }}
release: {{ .Release.Name }}
{{- end }}

