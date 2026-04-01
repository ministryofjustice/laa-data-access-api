# Network Policies

This document describes the Kubernetes NetworkPolicy resources deployed by the Helm chart.

All Helm templates are in `.helm/data-access-api/templates/`.

## Prometheus Scraping (`networkPolicy.yaml`)

Allows ingress from the monitoring namespace so Prometheus can scrape metrics from all pods in the namespace:

```yaml
spec:
  podSelector: {}
  policyTypes:
    - Ingress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              component: monitoring
```

- `podSelector: {}` applies to all pods in the namespace (API and PostgreSQL)
- The monitoring namespace must have the label `component: monitoring`
- Without this policy, Prometheus cannot scrape `/actuator/prometheus` and all Grafana dashboard panels will be blank

## Verifying the policy

Confirm the monitoring namespace has the required label:

```bash
kubectl get ns -l component=monitoring
```

## Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| Grafana panels blank | Monitoring NetworkPolicy missing or misconfigured | Check `networkPolicy.yaml` exists and monitoring namespace has `component: monitoring` label |
