package ru.yandex.practicum.tracing;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

public abstract class AbstractTracingConsumerInterceptor<K, V>
        implements ConsumerInterceptor<K, V>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(AbstractTracingConsumerInterceptor.class);

    private CurrentTraceContext currentTraceContext;
    private final ThreadLocal<CurrentTraceContext.Scope> currentScope = new ThreadLocal<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.currentTraceContext = createCurrentTraceContext(ctx);
    }

    protected abstract CurrentTraceContext createCurrentTraceContext(ApplicationContext ctx);

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        if (records.isEmpty() || currentTraceContext == null) {
            return records;
        }

        for (ConsumerRecord<K, V> record : records) {
            TraceContext traceContext = B3PropagationUtil.extractTraceContext(record.headers());
            if (traceContext == null) {
                continue;
            }

            CurrentTraceContext.Scope scope = null;
            try {
                scope = currentTraceContext.newScope(traceContext);
                currentScope.set(scope);

                if (log.isDebugEnabled()) {
                    log.debug("Restored B3 tracing context for Kafka record: topic={}, partition={}, "
                                    + "offset={}, traceId={}, spanId={}",
                            record.topic(), record.partition(), record.offset(),
                            traceContext.traceIdString(), traceContext.spanIdString());
                }
            } catch (Exception e) {
                log.warn("Failed to create tracing scope for Kafka record: topic={}, partition={}, offset={}, error={}",
                        record.topic(), record.partition(), record.offset(), e.getMessage());
                closeScopeSafely(scope);
            }
        }

        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
    }

    @Override
    public void close() {
        CurrentTraceContext.Scope scope = currentScope.get();
        closeScopeSafely(scope);
        currentScope.remove();
    }

    private void closeScopeSafely(CurrentTraceContext.Scope scope) {
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception e) {
                log.warn("Failed to close tracing scope: {}", e.getMessage());
            }
        }
        currentScope.remove();
    }
}
