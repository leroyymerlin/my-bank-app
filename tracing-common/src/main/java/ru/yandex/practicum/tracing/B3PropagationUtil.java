package ru.yandex.practicum.tracing;

import brave.propagation.TraceContext;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public final class B3PropagationUtil {

    private static final Logger log = LoggerFactory.getLogger(B3PropagationUtil.class);

    private static final String TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String SPAN_ID_HEADER = "X-B3-SpanId";
    private static final String PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId";
    private static final String SAMPLED_HEADER = "X-B3-Sampled";

    private static final int HEX_RADIX = 16;

    private B3PropagationUtil() {
    }

    public static TraceContext extractTraceContext(Headers headers) {
        String traceIdStr = extractHeaderValue(headers, TRACE_ID_HEADER);
        if (traceIdStr == null || traceIdStr.isEmpty()) {
            return null;
        }

        try {
            long traceIdHigh;
            long traceIdLow;

            if (traceIdStr.length() == 32) {
                traceIdHigh = Long.parseUnsignedLong(traceIdStr.substring(0, 16), HEX_RADIX);
                traceIdLow = Long.parseUnsignedLong(traceIdStr.substring(16), HEX_RADIX);
            } else if (traceIdStr.length() == 16) {
                traceIdHigh = 0;
                traceIdLow = Long.parseUnsignedLong(traceIdStr, HEX_RADIX);
            } else {
                log.warn("Некорректная длина traceId: {}, ожидаются 16 или 32 hex-символа", traceIdStr.length());
                return null;
            }

            TraceContext.Builder builder = TraceContext.newBuilder();
            if (traceIdHigh != 0) {
                builder.traceId(traceIdLow).traceIdHigh(traceIdHigh);
            } else {
                builder.traceId(traceIdLow);
            }

            String spanIdStr = extractHeaderValue(headers, SPAN_ID_HEADER);
            if (spanIdStr != null && !spanIdStr.isEmpty()) {
                builder.spanId(Long.parseUnsignedLong(spanIdStr, HEX_RADIX));
            }

            String parentSpanIdStr = extractHeaderValue(headers, PARENT_SPAN_ID_HEADER);
            if (parentSpanIdStr != null && !parentSpanIdStr.isEmpty()) {
                builder.parentId(Long.parseUnsignedLong(parentSpanIdStr, HEX_RADIX));
            }

            String sampledStr = extractHeaderValue(headers, SAMPLED_HEADER);
            if ("1".equals(sampledStr)) {
                builder.sampled(true);
            } else if ("0".equals(sampledStr)) {
                builder.sampled(false);
            }

            return builder.build();
        } catch (IllegalArgumentException e) {
            log.warn("Не удалось разобрать B3-заголовки: traceId={}, error={}", traceIdStr, e.getMessage());
            return null;
        }
    }

    private static String extractHeaderValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
