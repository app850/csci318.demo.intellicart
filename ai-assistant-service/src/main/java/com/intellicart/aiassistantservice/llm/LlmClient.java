package com.intellicart.aiassistantservice.llm;
import com.intellicart.aiassistantservice.convo.ToolCall;
import com.intellicart.aiassistantservice.convo.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface LlmClient {
    record LlmResponse(String assistantText, List<Map<String,Object>> toolEvents) {}
    LlmResponse chat(String system, String style, List<Map<String,String>> history,
                     List<String> toolSchemas, Function<ToolCall, ToolResult> toolExecutor);
}
