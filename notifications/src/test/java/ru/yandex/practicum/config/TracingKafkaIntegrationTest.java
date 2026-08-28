package ru.yandex.practicum.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TracingKafkaIntegrationTest {

    @Autowired
    private brave.Tracer braveTracer;

    @Autowired
    private CurrentTraceContext currentTraceContext;

    @Autowired
    private TracingKafkaProducerInterceptor<String, String> producerInterceptor;

    @Autowired
    private TracingKafkaConsumerInterceptor<String, String> consumerInterceptor;

    @Test
    void shouldAddB3HeadersToProducerRecord() {
        brave.Span span = braveTracer.nextSpan().start();
        try {
            TraceContext traceContext = span.context();
            try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(traceContext)) {
                ProducerRecord<String, String> originalRecord = new ProducerRecord<>("test-topic", "key", "value");
                ProducerRecord<String, String> interceptedRecord = producerInterceptor.onSend(originalRecord);

                byte[] traceIdBytes = interceptedRecord.headers().lastHeader("X-B3-TraceId").value();
                byte[] spanIdBytes = interceptedRecord.headers().lastHeader("X-B3-SpanId").value();

                assertThat(traceIdBytes).isNotNull();
                assertThat(spanIdBytes).isNotNull();
                assertThat(new String(traceIdBytes, StandardCharsets.UTF_8))
                        .isEqualTo(traceContext.traceIdString());
                assertThat(new String(spanIdBytes, StandardCharsets.UTF_8))
                        .isEqualTo(traceContext.spanIdString());
            }
        } finally {
            span.finish();
        }
    }

    @Test
    void shouldRestoreTraceContextFromHeaders() {
        long traceId = 0x1234567890abcdefL;
        long spanId = 0x0987654321fedcbaL;

        TraceContext originalContext = TraceContext.newBuilder()
                .traceId(traceId)
                .spanId(spanId)
                .sampled(true)
                .build();

        CurrentTraceContext.Scope originalScope = currentTraceContext.newScope(originalContext);
        try {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "test-topic", 0, 0L, "key", "value");
            record.headers().add("X-B3-TraceId", String.format("%016x", traceId).getBytes(StandardCharsets.UTF_8));
            record.headers().add("X-B3-SpanId", String.format("%016x", spanId).getBytes(StandardCharsets.UTF_8));
            record.headers().add("X-B3-Sampled", "1".getBytes(StandardCharsets.UTF_8));

            Map<TopicPartition, List<ConsumerRecord<String, String>>> partitionMap = new HashMap<>();
            partitionMap.put(new TopicPartition("test-topic", 0), Collections.singletonList(record));

            ConsumerRecords<String, String> records = new ConsumerRecords<>(partitionMap);

            consumerInterceptor.onConsume(records);

            TraceContext restoredContext = currentTraceContext.get();
            assertThat(restoredContext).isNotNull();
            assertThat(restoredContext.spanIdString()).isEqualTo(String.format("%016x", spanId));

            consumerInterceptor.onCommit(Map.of(
                    new TopicPartition("test-topic", 0),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(1L)));
        } finally {
            originalScope.close();
        }
    }

    @Test
    void shouldHandleMessagesWithoutTraceHeaders() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "test-topic", 0, 0L, "key", "value");

        Map<TopicPartition, List<ConsumerRecord<String, String>>> partitionMap = new HashMap<>();
        partitionMap.put(new TopicPartition("test-topic", 0), Collections.singletonList(record));

        ConsumerRecords<String, String> records = new ConsumerRecords<>(partitionMap);

        ConsumerRecords<String, String> result = consumerInterceptor.onConsume(records);

        assertThat(result).isNotEmpty();
    }
}
