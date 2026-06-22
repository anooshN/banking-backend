{{/*
Expand the name of the chart.
*/}}
{{- define "loan-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "loan-service.fullname" -}}
{{- .Release.Name }}-{{- include "loan-service.name" . }}
{{- end }}

{{- define "loan-service.labels" -}}
helm.sh/chart: {{ include "loan-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "loan-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "loan-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "loan-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
