package com.intellicart.aiassistantservice.presentation;

import com.intellicart.aiassistantservice.dto.ChatRequest;
import com.intellicart.aiassistantservice.dto.ChatResponse;
import com.intellicart.aiassistantservice.llm.LlmAssistant;
import com.intellicart.aiassistantservice.llm.LlmTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/chat")
public class AssistantController {

    private final LlmAssistant agent;

    public AssistantController(@Qualifier("llmChatModel") ChatLanguageModel model, LlmTools tools) {
        ChatMemoryProvider memory = id -> MessageWindowChatMemory.withMaxMessages(30);
        this.agent = AiServices.builder(LlmAssistant.class)
                .chatLanguageModel(model)
                .tools(tools)
                .chatMemoryProvider(memory)
                .build();
    }

    @PostMapping(
            value = "/human",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ChatResponse humanChat(@RequestBody ChatRequest request,
                                  @RequestHeader("X-Session-Id") String sessionId) {
        String userMsg = request != null ? request.message() : null;
        if (!StringUtils.hasText(userMsg)) {
            return new ChatResponse("Please type a message.", sessionId);
        }
        String answer = agent.chat(sessionId, userMsg);
        answer = normalizeNewlines(answer);
        return new ChatResponse(answer, sessionId);
    }


    private static String normalizeNewlines(String s) {
        if (s == null) return "";
        // 1) normalize platform endings to \n
        String t = s.replace("\r\n", "\n").replace("\r", "\n");
        // 2) if upstream sent literal backslash-n, turn it into a real newline
        t = t.replace("\\n", "\n");
        // 3) trim only; DO NOT do any regex magic on plain 'n'
        return t.trim();
    }

}
