package com.intellicart.aiassistantservice.llm;

import com.intellicart.aiassistantservice.convo.ToolCall;
import com.intellicart.aiassistantservice.convo.ToolResult;
import com.intellicart.aiassistantservice.service.GeminiRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class TextToolCallingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(TextToolCallingLlmClient.class);

    private final GeminiRagService gemini;

    public TextToolCallingLlmClient(GeminiRagService gemini) {
        this.gemini = gemini;
    }

    @Override
    public LlmResponse chat(
            String system,
            String style,
            List<Map<String, String>> history,
            List<String> toolSchemas,
            Function<ToolCall, ToolResult> toolExecutor
    ) {
        String transcript = renderHistory(history);
        String toolCatalog = renderToolSchemas(toolSchemas);

        String pass1Prompt = """
                %s

                %s

                You can optionally call a tool to fetch data or act.
                Available tools (JSON signatures):
                %s

                Conversation so far:
                %s

                Decide ONE of the following:
                1) Answer directly in natural language if you have enough info.
                2) Or, if you need data/action, output EXACTLY one line:
                   TOOL_CALL: {"name":"<toolName>","args":{"action":"...","...":...}}

                Rules:
                - If you output TOOL_CALL, do NOT add any extra text before/after it.
                - Keep args minimal and valid per the tool signature.
                - Tool names must match one of the available tools.
                - Prefer asking a short clarifying question only if required.
                """.formatted(system, style, toolCatalog, transcript);

        String pass1 = safe(gemini.answer(pass1Prompt)).trim();

        // Don't normalize before TOOL_CALL detection
        if (pass1.startsWith("TOOL_CALL:")) {
            String json = pass1.substring("TOOL_CALL:".length()).trim();
            ToolCall call = parseToolCall(json);
            if (call == null) {
                log.warn("Model returned malformed TOOL_CALL: {}", pass1);
                return new LlmResponse("Sorry, I couldn’t interpret the tool request. Could you rephrase?", List.of());
            }

            ToolResult toolResult = toolExecutor.apply(call);

            // ---- SHORT-CIRCUIT: if the tool already returns user-facing text, skip LLM pass 2
            if ("recommend_books".equalsIgnoreCase(call.name())
                    || "search_catalogue".equalsIgnoreCase(call.name())) {

                String ready;
                Object data = toolResult.data();
                if (data instanceof List<?> l) {
                    List<String> parts = new ArrayList<>();
                    for (Object o : l) if (o != null) parts.add(String.valueOf(o));
                    ready = String.join("\n", parts); // real newlines
                } else {
                    ready = String.valueOf(data);
                }

                String finalOut = normalizeNewlines(ready);
                return new LlmResponse(finalOut, List.of(Map.of(
                        "tool", call.name(),
                        "args", call.args(),
                        "ok", toolResult.ok(),
                        "data", toolResult.data(),
                        "error", toolResult.error()
                )));
            }
            // ---- END SHORT-CIRCUIT ----

            // Default behavior: ask the LLM to compose a reply from the tool JSON
            String pass2Prompt = """
                    %s

                    %s

                    You requested this tool call earlier:
                    %s

                    The tool responded with this JSON (do not fabricate other fields):
                    %s

                    Compose a concise, friendly reply for the user in plain language, using ONLY the provided tool result.
                    If the result is an error, explain briefly and suggest one next step.
                    """.formatted(system, style, json, toPretty(toolResult));

            String pass2 = safe(gemini.answer(pass2Prompt)).trim();
            String finalOut = normalizeNewlines(pass2);
            return new LlmResponse(finalOut, List.of(Map.of(
                    "tool", call.name(),
                    "args", call.args(),
                    "ok", toolResult.ok(),
                    "data", toolResult.data(),
                    "error", toolResult.error()
            )));
        }

        String finalOut = normalizeNewlines(pass1);
        return new LlmResponse(finalOut, List.of());
    }

    private String renderHistory(List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> turn : history) {
            String role = String.valueOf(turn.getOrDefault("role", "user"));
            String content = String.valueOf(turn.getOrDefault("content", ""));
            sb.append(role).append(": ").append(content).append("\n");
        }
        return sb.toString().trim();
    }

    private String renderToolSchemas(List<String> toolSchemas) {
        if (toolSchemas == null || toolSchemas.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (String s : toolSchemas) {
            sb.append("- ").append(s).append("\n");
        }
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String toPretty(ToolResult r) {
        return "{\n  \"ok\": " + r.ok()
                + ",\n  \"data\": " + String.valueOf(r.data())
                + ",\n  \"error\": " + String.valueOf(r.error())
                + "\n}";
    }

    @SuppressWarnings("unchecked")
    private ToolCall parseToolCall(String json) {
        try {
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            String name = String.valueOf(map.get("name"));
            Object args = map.get("args");
            Map<String, Object> argsMap = (args instanceof Map<?, ?> m)
                    ? (Map<String, Object>) m
                    : new HashMap<>();
            if (name == null || name.isBlank()) return null;
            return new ToolCall(name, argsMap);
        } catch (Exception e) {
            return null;
        }
    }

    /** Minimal, safe newline normalization (no “smart” regex). */
    private static String normalizeNewlines(String s) {
        if (s == null) return "";
        String t = s.replace("\r\n", "\n").replace("\r", "\n"); // normalize platform endings
        t = t.replace("\\n", "\n");                             // unescape literal \n
        return t.trim();
    }
}
