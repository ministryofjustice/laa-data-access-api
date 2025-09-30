{{/*
_helpers.tpl
This file contains Helm template helpers that can be reused throughout the chart.
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "providers-app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name (release name + chart name unless fullnameOverride is set).
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "providers-app.fullname" -}}
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

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "providers-app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels (note that the Selector labels are included in here)
*/}}
{{- define "providers-app.labels" -}}
{{ include "providers-app.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ include "providers-app.chart" . }}
helm.sh/revision: {{ .Release.Revision | quote }}
{{- end }}

{{/*
Selector labels (identify this instance of the appliction)
*/}}
{{- define "providers-app.selectorLabels" -}}
{{ include "providers-app.appLabels" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
App selector labels (identify all instances of the application)
*/}}
{{- define "providers-app.appLabels" -}}
app.kubernetes.io/name: {{ include "providers-app.name" . }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "providers-app.serviceAccountName" -}}
{{- if (.Values.serviceAccount).create }}
{{- default (include "providers-app.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" (.Values.serviceAccount).name }}
{{- end }}
{{- end }}
