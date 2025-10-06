package com.intellicart.aiassistantservice.convo;

import com.intellicart.aiassistantservice.llm.LlmClient;
import com.intellicart.aiassistantservice.llm.ToolSchema;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ConversationalAgent {
    private final LlmClient llm;
    private final ToolRouter router;
    private final String system;
    private final String style;

    public ConversationalAgent(LlmClient llm, ToolRouter router) throws Exception {
        this.llm = llm; this.router = router;
        this.system = readResource("prompts/system.md");
        this.style  = readResource("prompts/style.md");
    }

    private String readResource(String path) throws Exception {
        var res = new ClassPathResource(path);
        try (var in = res.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    public Map<String,Object> chat(TurnContext ctx, String userMessage){
        ctx.addUser(userMessage);
        var resp = llm.chat(system, style, ctx.history(),
                List.of(ToolSchema.USER, ToolSchema.ORDER, ToolSchema.BOOK),
                call -> router.dispatch(call, ctx));
        if (resp.assistantText() != null && !resp.assistantText().isBlank()) {
            ctx.addAssistant(resp.assistantText());
        }
        return Map.of("answer", resp.assistantText(), "sessionId", ctx.sessionId());
    }
}
