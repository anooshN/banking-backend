{{/*
Expand the name of the chart.
*/}}
{{- define "report-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "report-service.fullname" -}}
{{- .Release.Name }}-{{- include "report-service.name" . }}
{{- end }}

{{- define "report-service.labels" -}}
helm.sh/chart: {{ include "report-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "report-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "report-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "report-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
