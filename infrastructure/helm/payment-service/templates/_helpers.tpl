{{/*
Expand the name of the chart.
*/}}
{{- define "payment-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "payment-service.fullname" -}}
{{- .Release.Name }}-{{- include "payment-service.name" . }}
{{- end }}

{{- define "payment-service.labels" -}}
helm.sh/chart: {{ include "payment-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "payment-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "payment-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "payment-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
