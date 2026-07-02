{{/*
Expand the name of the chart.
*/}}
{{- define "shared-mock-oauth2.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "shared-mock-oauth2.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "shared-mock-oauth2.labels" -}}
helm.sh/chart: {{ include "shared-mock-oauth2.chart" . }}
{{ include "shared-mock-oauth2.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "shared-mock-oauth2.selectorLabels" -}}
app: mock-oauth2-shared
app.kubernetes.io/name: mock-oauth2-shared
app.kubernetes.io/instance: shared
{{- end }}
