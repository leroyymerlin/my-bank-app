package ru.yandex.practicum.config;

import brave.propagation.CurrentTraceContext;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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
public class TracingKafkaConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(TracingKafkaConsumerInterceptor.class);

    private static final String TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String SPAN_ID_HEADER = "X-B3-SpanId";
    private static final String PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId";
    private static final String SAMPLED_HEADER = "X-B3-Sampled";

    private static boolean tracingEnabled = false;
    private static CurrentTraceContext currentTraceContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        try {
            currentTraceContext = ctx.getBean(CurrentTraceContext.class);
            tracingEnabled = true;
        } catch (BeansException e) {
            tracingEnabled = false;
            if (log.isTraceEnabled()) {
                log.trace("CurrentTraceContext not available, tracing disabled for Kafka consumer interceptor: {}", e.getMessage());
            }
        }
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        if (!tracingEnabled || currentTraceContext == null) {
            return records;
        }

        for (ConsumerRecord<K, V> record : records) {
            extractAndSetTraceContext(record.headers());
        }
        return records;
    }

    private void extractAndSetTraceContext(Headers headers) {
        String traceIdStr = extractHeaderValue(headers, TRACE_ID_HEADER);
        String spanIdStr = extractHeaderValue(headers, SPAN_ID_HEADER);
        String parentSpanIdStr = extractHeaderValue(headers, PARENT_SPAN_ID_HEADER);

        if (traceIdStr != null) {
            try {
                long traceIdLong = Long.parseUnsignedLong(traceIdStr, 16);
                long spanIdLong = spanIdStr != null ? Long.parseUnsignedLong(spanIdStr, 16) : traceIdLong;
                long parentSpanIdLong = parentSpanIdStr != null ? Long.parseUnsignedLong(parentSpanIdStr, 16) : 0L;
                boolean sampled = "1".equals(extractHeaderValue(headers, SAMPLED_HEADER));

                brave.propagation.TraceContext traceContext = brave.propagation.TraceContext.newBuilder()
                        .traceId(traceIdLong)
                        .spanId(spanIdLong)
                        .parentId(parentSpanIdLong)
                        .sampled(sampled)
                        .build();

                CurrentTraceContext.Scope scope = currentTraceContext.newScope(traceContext);
                scope.close();

                if (log.isDebugEnabled()) {
                    log.debug("Extracted B3 tracing context from Kafka record: traceId={}", traceIdStr);
                }
            } catch (Exception e) {
                log.warn("Failed to extract B3 tracing context from Kafka record: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onCommit(Map<org.apache.kafka.common.TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets) {
    }

    @Override
    public void close() {
    }

    private String extractHeaderValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
