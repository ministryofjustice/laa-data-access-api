{{- define "k6-perf.fullname" -}}
{{ .Values.testRun.name }}
{{- end }}

{{- define "k6-perf.labels" -}}
app.kubernetes.io/name: {{ .Values.testRun.name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
