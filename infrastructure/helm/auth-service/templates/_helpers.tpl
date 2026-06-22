{{/*
Expand the name of the chart.
*/}}
{{- define "auth-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "auth-service.fullname" -}}
{{- .Release.Name }}-{{- include "auth-service.name" . }}
{{- end }}

{{- define "auth-service.labels" -}}
helm.sh/chart: {{ include "auth-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "auth-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "auth-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "auth-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
