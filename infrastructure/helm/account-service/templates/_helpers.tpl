{{/*
Expand the name of the chart.
*/}}
{{- define "account-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "account-service.fullname" -}}
{{- .Release.Name }}-{{- include "account-service.name" . }}
{{- end }}

{{- define "account-service.labels" -}}
helm.sh/chart: {{ include "account-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "account-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "account-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "account-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
