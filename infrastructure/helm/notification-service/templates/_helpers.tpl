{{/*
Expand the name of the chart.
*/}}
{{- define "notification-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "notification-service.fullname" -}}
{{- .Release.Name }}-{{- include "notification-service.name" . }}
{{- end }}

{{- define "notification-service.labels" -}}
helm.sh/chart: {{ include "notification-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "notification-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "notification-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "notification-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
