{{/*
Expand the name of the chart.
*/}}
{{- define "fraud-detection-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "fraud-detection-service.fullname" -}}
{{- .Release.Name }}-{{- include "fraud-detection-service.name" . }}
{{- end }}

{{- define "fraud-detection-service.labels" -}}
helm.sh/chart: {{ include "fraud-detection-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "fraud-detection-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "fraud-detection-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "fraud-detection-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
