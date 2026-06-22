{{/*
Expand the name of the chart.
*/}}
{{- define "card-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "card-service.fullname" -}}
{{- .Release.Name }}-{{- include "card-service.name" . }}
{{- end }}

{{- define "card-service.labels" -}}
helm.sh/chart: {{ include "card-service.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "card-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "card-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "card-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
