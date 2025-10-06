package com.intellicart.aiassistantservice.convo;
import com.intellicart.aiassistantservice.tools.Tool;
import java.util.Map;
public class ToolRouter {
    private final Map<String, Tool> tools;
    public ToolRouter(Map<String, Tool> tools){ this.tools = tools; }
    public ToolResult dispatch(ToolCall call, TurnContext ctx){
        var t = tools.get(call.name());
        if (t == null) return ToolResult.err("unknown tool: " + call.name());
        var action = (String) call.args().getOrDefault("action", "");
        return t.handle(action, call.args(), ctx);
    }
}
