package ru.yandex.practicum.config;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class TracingKafkaConsumerInterceptor<K, V>
        implements ConsumerInterceptor<K, V>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(TracingKafkaConsumerInterceptor.class);

    private static final String TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String SPAN_ID_HEADER = "X-B3-SpanId";
    private static final String PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId";
    private static final String SAMPLED_HEADER = "X-B3-Sampled";

    private static volatile CurrentTraceContext currentTraceContext;
    private final ThreadLocal<CurrentTraceContext.Scope> scopeHolder = new ThreadLocal<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        try {
            currentTraceContext = ctx.getBean(CurrentTraceContext.class);
        } catch (BeansException e) {
            if (log.isTraceEnabled()) {
                log.trace("CurrentTraceContext not available: {}", e.getMessage());
            }
        }
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        if (records.isEmpty() || currentTraceContext == null) {
            return records;
        }

        for (ConsumerRecord<K, V> record : records) {
            TraceContext traceContext = extractTraceContext(record.headers());
            if (traceContext == null) {
                continue;
            }

            try {
                CurrentTraceContext.Scope scope = currentTraceContext.newScope(traceContext);
                scopeHolder.set(scope);

                if (log.isDebugEnabled()) {
                    log.debug("Restored B3 tracing context for Kafka batch: topic={}, partition={}, " +
                                    "offset={}, traceId={}, spanId={}",
                            record.topic(), record.partition(), record.offset(),
                            traceContext.traceIdString(), traceContext.spanIdString());
                }
                break;
            } catch (Exception e) {
                log.warn("Failed to create tracing scope for Kafka batch: topic={}, error={}",
                        record.topic(), e.getMessage());
            }
        }

        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        CurrentTraceContext.Scope scope = scopeHolder.get();
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception e) {
                log.warn("Failed to close tracing scope: {}", e.getMessage());
            } finally {
                scopeHolder.remove();
            }
        }
    }

    @Override
    public void close() {
        CurrentTraceContext.Scope scope = scopeHolder.get();
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception e) {
                log.warn("Failed to close tracing scope on interceptor close: {}", e.getMessage());
            }
            scopeHolder.remove();
        }
    }

    private TraceContext extractTraceContext(Headers headers) {
        String traceIdStr = extractHeaderValue(headers, TRACE_ID_HEADER);
        if (traceIdStr == null) {
            return null;
        }

        try {
            long traceId = Long.parseUnsignedLong(traceIdStr);
            TraceContext.Builder builder = TraceContext.newBuilder().traceId(traceId);

            String spanIdStr = extractHeaderValue(headers, SPAN_ID_HEADER);
            if (spanIdStr != null) {
                builder.spanId(Long.parseUnsignedLong(spanIdStr));
            }

            String parentSpanIdStr = extractHeaderValue(headers, PARENT_SPAN_ID_HEADER);
            if (parentSpanIdStr != null) {
                builder.parentId(Long.parseUnsignedLong(parentSpanIdStr));
            }

            String sampledStr = extractHeaderValue(headers, SAMPLED_HEADER);
            if ("1".equals(sampledStr)) {
                builder.sampled(true);
            } else if ("0".equals(sampledStr)) {
                builder.sampled(false);
            }

            return builder.build();
        } catch (IllegalArgumentException e) {
            log.warn("128-bit trace ID not supported by current Brave version: traceId={}", traceIdStr);
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract B3 tracing context from Kafka record: traceId={}, error={}",
                    traceIdStr, e.getMessage());
            return null;
        }
    }

    private String extractHeaderValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
