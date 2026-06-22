{{/*
Expand the name of the chart.
*/}}
{{- define "audit-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "audit-service.fullname" -}}
{{- .Release.Name }}-{{- include "audit-service.name" . }}
{{- end }}

{{- define "audit-service.labels" -}}
helm.sh/chart: {{ include "audit-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "audit-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "audit-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "audit-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
