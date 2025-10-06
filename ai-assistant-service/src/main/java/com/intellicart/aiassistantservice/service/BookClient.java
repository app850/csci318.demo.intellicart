package com.intellicart.aiassistantservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class BookClient {

    private static final Logger log = LoggerFactory.getLogger(BookClient.class);

    private final RestTemplate http;
    private final String baseUrl;

    public BookClient(@Value("${book.service.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;

        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(2000);
        rf.setReadTimeout(4000);
        this.http = new RestTemplate(rf);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchAll() {
        try {
            List<Map<String, Object>> out = http.getForObject(baseUrl + "/api/books", List.class);
            return (out == null) ? Collections.emptyList() : out;
        } catch (Exception e) {
            log.warn("Book service not reachable at {}: {}", baseUrl, e.getMessage());
            return Collections.emptyList();  // <-- critical: don’t throw
        }
    }
}
