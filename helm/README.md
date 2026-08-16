# My Bank App - Helm Charts

Зонтичный Helm-чарт для деплоя приложения My Bank в Kubernetes.

## Структура

```
helm/
├── Chart.yaml              # Зонтичный чарт (родительский)
├── values.yaml             # Глобальные значения
├── accounts/               # Accounts Service + PostgreSQL
├── cash/                   # Cash Service + PostgreSQL
├── transfer/               # Transfer Service + PostgreSQL
├── notifications/          # Notifications Service + PostgreSQL
├── gateway/                # API Gateway + PostgreSQL
└── front/                  # Frontend (без БД)
```

## Требования

- Kubernetes >= 1.28
- Helm >= 3.12
- Доступ к кластеру (kubectl конфиг по умолчанию)
- PV-провайдер (для StatefulSet PostgreSQL)

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

## Конфигурация

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `namespace` | namespace для деплоя | `my-bank-app` |
| `imagePullPolicy` | policy для pull образов | `IfNotPresent` |

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

## Ресурсы

### Service

Каждый сервис (accounts, cash, transfer, notifications, gateway, front) создаёт Service типа ClusterIP для внутреннего взаимодействия.

### Deployment

Каждый микросервис создаётся как Deployment с настраиваемым количеством реплик.

### StatefulSet (PostgreSQL)

Каждый микросервис имеет свою PostgreSQL базу данных в StatefulSet с PVC для хранения данных.

## DNS в кластере

Сервисы доступны друг другу по DNS-именам:

```
{{ .Release.Name }}-accounts:8082
{{ .Release.Name }}-cash:8083
{{ .Release.Name }}-transfer:8084
{{ .Release.Name }}-notifications:8085
{{ .Release.Name }}-gateway:8081
{{ .Release.Name }}-postgresql:5432
```

## Troubleshooting

### Проблема с доступом к PostgreSQL

```bash
kubectl get pods -n my-bank-app
kubectl get pvc -n my-bank-app
```

### Логи контейнера

```bash
kubectl logs -n my-bank-app -l app=accounts
```

### Проверка сервисов

```bash
kubectl get svc -n my-bank-app
```
