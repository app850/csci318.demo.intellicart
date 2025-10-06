package com.intellicart.aiassistantservice.config;

import com.intellicart.aiassistantservice.service.BookIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupIndexer {
    private static final Logger log = LoggerFactory.getLogger(StartupIndexer.class);
    private final BookIndexService index;

    public StartupIndexer(BookIndexService index) {
        this.index = index;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            String result = index.reindex();
            log.info("[StartupIndexer] {}", result);
        } catch (Exception e) {
            log.warn("[StartupIndexer] Reindex failed on startup: {}", e.getMessage());
        }
    }
}
