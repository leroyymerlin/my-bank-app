package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ProcessedEventsStorage {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    private static final int MAX_CACHE_SIZE = 100_000;

    public synchronized boolean tryMarkAsProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        if (processedEventIds.contains(eventId)) {
            return true;
        }

        if (processedEventIds.size() >= MAX_CACHE_SIZE) {
            log.warn("Cache reached max size ({}). Consider switching to persistent storage.", MAX_CACHE_SIZE);
        }

        processedEventIds.add(eventId);
        return false;
    }

    public synchronized void clear() {
        processedEventIds.clear();
    }

    public synchronized boolean contains(String eventId) {
        return processedEventIds.contains(eventId);
    }

    public synchronized int size() {
        return processedEventIds.size();
    }
}
