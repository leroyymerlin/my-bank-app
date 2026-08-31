package ru.yandex.practicum.config;

import brave.propagation.CurrentTraceContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.tracing.AbstractTracingConsumerInterceptor;

@Component
public class TracingKafkaConsumerInterceptor<K, V>
        extends AbstractTracingConsumerInterceptor<K, V> {

    @Override
    protected CurrentTraceContext createCurrentTraceContext(ApplicationContext ctx) {
        try {
            return ctx.getBean(CurrentTraceContext.class);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }
}
