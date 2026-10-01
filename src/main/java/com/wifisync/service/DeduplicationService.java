package com.wifisync.service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeduplicationService {
    private static final int MAX_CACHE_SIZE = 200;
    private final Set<String> seenIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>() {
        @Override
        public Boolean put(String key, Boolean value) {
            if (size() >= MAX_CACHE_SIZE) {
                String firstKey = keySet().iterator().next();
                remove(firstKey);
            }
            return super.put(key, value);
        }
    });

    public boolean isNewAndMark(String messageId) {
        if (messageId == null || messageId.isEmpty()) return false;
        return seenIds.add(messageId);
    }
}