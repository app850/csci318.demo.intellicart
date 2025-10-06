// src/main/java/com/intellicart/aiassistantservice/presentation/RequestContext.java
package com.intellicart.aiassistantservice.presentation;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestContext {
    private Long userId;
    private String sessionId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
