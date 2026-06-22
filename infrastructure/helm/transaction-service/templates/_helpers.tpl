{{/*
Expand the name of the chart.
*/}}
{{- define "transaction-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "transaction-service.fullname" -}}
{{- .Release.Name }}-{{- include "transaction-service.name" . }}
{{- end }}

{{- define "transaction-service.labels" -}}
helm.sh/chart: {{ include "transaction-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "transaction-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "transaction-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "transaction-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
