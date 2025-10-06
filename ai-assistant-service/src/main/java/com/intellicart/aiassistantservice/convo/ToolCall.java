package com.intellicart.aiassistantservice.convo;
import java.util.Map;
public record ToolCall(String name, Map<String,Object> args) {}
