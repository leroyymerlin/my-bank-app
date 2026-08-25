# My Bank App - Helm Charts

Зонтичный Helm-чарт для деплоя приложения My Bank в Kubernetes.

## Быстрый старт

### 1. Установить все сервисы

```bash
helm install my-bank ./helm
```

### 2. Установить отдельные сервисы

```bash
# Только gateway и accounts
helm install my-bank ./helm --set accounts.enabled=true --set gateway.enabled=true

# Отключить notifications
helm install my-bank ./helm --set notifications.enabled=false
```

### 3. Обновить

```bash
helm upgrade my-bank ./helm
```

### 4. Удалить

```bash
helm uninstall my-bank
```
### Accounts Service

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `accounts.enabled` | Включить accounts | `true` |
| `accounts.replicaCount` | Количество реплик | `1` |
| `accounts.service.type` | Тип сервиса | `ClusterIP` |

### Cash Service

Аналогично accounts.

### Transfer Service

Аналогично accounts.

### Notifications Service

Аналогично accounts.

### Gateway

Аналогично accounts.

### Front

Аналогично accounts.

### PostgreSQL (для всех сервисов)

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `postgresql.service.type` | Тип сервиса | `ClusterIP` |
| `postgresql.resources.requests.memory` | Request памяти | `256Mi` |
| `postgresql.resources.limits.memory` | Limit памяти | `512Mi` |
| `postgresql.volumeClaim.storage` | Размер PVC | `1Gi` |

### Prometheus

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `prometheus.enabled` | Включить Prometheus | `true` |
| `prometheus.replicaCount` | Количество реплик | `1` |
| `prometheus.service.type` | Тип сервиса | `ClusterIP` |
| `prometheus.retention` | Хранение данных | `15d` |
| `prometheus.persistence.size` | Размер PVC | `5Gi` |
| `prometheus.scrapeInterval` | Интервал сбора метрик | `15s` |

### AlertManager

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `alertmanager.enabled` | Включить AlertManager | `true` |
| `alertmanager.replicaCount` | Количество реплик | `1` |
| `alertmanager.service.type` | Тип сервиса | `ClusterIP` |

### ELK Stack

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `elk.enabled` | Включить ELK stack | `true` |
| `elk.elasticsearch.replicaCount` | Количество реплик Elasticsearch | `1` |
| `elk.elasticsearch.service.type` | Тип сервиса Elasticsearch | `ClusterIP` |
| `elk.elasticsearch.persistence.size` | Размер PVC для Elasticsearch | `5Gi` |
| `elk.logstash.replicaCount` | Количество реплик Logstash | `1` |
| `elk.logstash.service.type` | Тип сервиса Logstash | `ClusterIP` |
| `elk.kibana.replicaCount` | Количество реплик Kibana | `1` |
| `elk.kibana.service.type` | Тип сервиса Kibana | `ClusterIP` |
| `elk.loki.replicaCount` | Количество реплик Loki | `1` |
| `elk.loki.service.type` | Тип сервиса Loki | `ClusterIP` |