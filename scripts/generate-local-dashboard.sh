#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SOURCE="$REPO_ROOT/.helm/data-access-api/templates/grafana-dashboard-template.json"
DEST="$REPO_ROOT/infra/grafana/provisioning/dashboards/laa-data-access-api.json"

python3 - "$SOURCE" "$DEST" <<'PYTHON'
import sys

with open(sys.argv[1]) as f:
    content = f.read()

# Strip the Helm define/end wrapper (first and last non-empty lines)
lines = content.split('\n')
# Remove the opening {{- define ... -}} line
lines = lines[1:]
# Strip trailing empty lines and the closing {{- end -}} line
while lines and lines[-1].strip() in ('', '{{- end -}}'):
    lines.pop()
content = '\n'.join(lines) + '\n'

replacements = [
    # UID field (uses Helm pipe functions — handle before the simpler patterns)
    (
        '{{ .Release.Name | trunc 12 }}-{{ .Release.Namespace | trunc -10 }}-{{ .Chart.Name | trunc -12 }}',
        'local'
    ),
    # $container template variable: replace kube-only query with a locally available metric
    (
        'label_values(kube_pod_container_info{namespace=\\"{{ .Release.Namespace }}\\", pod=~\\"{{ .Release.Name }}.*\\"},container)',
        'label_values(http_server_requests_seconds_count{release=\\"local\\"}, container)'
    ),
    # Standard Helm release/chart variables
    ('{{ .Release.Name }}', 'local'),
    ('{{ .Release.Namespace }}', 'local'),
    ('{{ .Chart.Name }}', 'data-access-api'),
    # Unescape Grafana template variables that Helm wraps in backtick literals
    ('{{`{{app}}`}}', '{{app}}'),
    ('{{`{{instance}}`}}', '{{instance}}'),
    ('{{`{{operation_type}}`}}', '{{operation_type}}'),
    ('{{`{{entity}}`}}', '{{entity}}'),
    ('{{`{{operation}}`}}', '{{operation}}'),
]

for old, new in replacements:
    content = content.replace(old, new)

with open(sys.argv[2], 'w') as f:
    f.write(content)

print(f"Generated: {sys.argv[2]}")
PYTHON
