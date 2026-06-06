{{- define "rag-demo.name" -}}rag-demo{{- end -}}

{{- define "rag-demo.fullname" -}}{{ .Release.Name }}-rag-demo{{- end -}}

{{- define "rag-demo.labels" -}}
app.kubernetes.io/name: {{ include "rag-demo.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "rag-demo.selectorLabels" -}}
app.kubernetes.io/name: {{ include "rag-demo.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
