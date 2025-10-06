package com.intellicart.aiassistantservice.convo;
import java.util.Map;
public record ToolResult(boolean ok, Map<String,Object> data, String error) {
    public static ToolResult ok(Map<String,Object> data){ return new ToolResult(true, data, null); }
    public static ToolResult err(String msg){ return new ToolResult(false, null, msg); }
}
