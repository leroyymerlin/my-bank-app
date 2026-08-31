# Grafana Monitoring

Этот подпроект содержит конфигурационные файлы для системы визуализации и мониторинга Grafana.

## Настройка Datasources

Grafana настроена на сбор данных из двух источников:

1. **Prometheus** - для метрик микросервисов (HTTP, JVM, кастомные)
2. **Loki** - для логов микросервисов

## Дашборды

### Banking Microservices
Общий дашборд с ключевыми метриками

### HTTP Metrics
Детальный дашборд для HTTP метрик

### JVM Metrics
Дашборд для JVM метрик

### Business Metrics
Дашборд для бизнес-метрик

## Alering

Grafana настроена на получение алертов из Prometheus Alertmanager.

## Использование

### Запуск через Docker Compose

```bash
# Запуск всех сервисов
docker-compose up -d
```