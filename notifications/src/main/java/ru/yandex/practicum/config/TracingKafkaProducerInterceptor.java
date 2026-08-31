package ru.yandex.practicum.config;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
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
public class TracingKafkaProducerInterceptor<K, V>
        implements ProducerInterceptor<K, V>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(TracingKafkaProducerInterceptor.class);

    private static final String TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String SPAN_ID_HEADER = "X-B3-SpanId";
    private static final String PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId";

    private static volatile CurrentTraceContext currentTraceContext;

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
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (currentTraceContext == null) {
            return record;
        }

        TraceContext traceContext = currentTraceContext.get();
        if (traceContext == null) {
            return record;
        }

        Headers headers = record.headers();
        headers.remove(TRACE_ID_HEADER);
        headers.remove(SPAN_ID_HEADER);
        headers.remove(PARENT_SPAN_ID_HEADER);

        headers.add(TRACE_ID_HEADER, traceContext.traceIdString().getBytes(StandardCharsets.UTF_8));
        headers.add(SPAN_ID_HEADER, traceContext.spanIdString().getBytes(StandardCharsets.UTF_8));

        if (traceContext.parentId() != null) {
            headers.add(PARENT_SPAN_ID_HEADER, traceContext.parentIdString().getBytes(StandardCharsets.UTF_8));
        }

        if (log.isDebugEnabled()) {
            log.debug("Added B3 tracing headers to Kafka record: topic={}, partition={}, traceId={}, spanId={}",
                    record.topic(), record.partition(),
                    traceContext.traceIdString(), traceContext.spanIdString());
        }

        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
    }
}
