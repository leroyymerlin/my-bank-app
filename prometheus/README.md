# Prometheus Monitoring

Этот подпроект содержит конфигурационные файлы для системы мониторинга Prometheus.

## Файлы

- `prometheus.yml` — основная конфигурация Prometheus с описаниями scrape jobs для всех микросервисов
- `alerts.yml` — правила алертинга (Prometheus Rule) для мониторинга метрик
- `alertmanager.yml` — конфигурация AlertManager для маршрутизации алертов
- `grafana/` — провижининг Grafana (datasources и dashboards)

## Helm-чарты

Helm-чарты для Prometheus и AlertManager находятся в `helm/prometheus/` и `helm/alertmanager/`.

Установка через Helm:

```bash
# Установка всего стека мониторинга
helm install monitoring ./helm --set prometheus.enabled=true

# Установка только Prometheus
helm install prometheus ./helm/prometheus

# Установка только AlertManager
helm install alertmanager ./helm/alertmanager
```
