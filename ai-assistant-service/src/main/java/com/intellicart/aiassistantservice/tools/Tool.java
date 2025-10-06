// tools/Tool.java
package com.intellicart.aiassistantservice.tools;
import com.intellicart.aiassistantservice.convo.TurnContext;
import com.intellicart.aiassistantservice.convo.ToolResult;
import java.util.Map;
public interface Tool {
    ToolResult handle(String action, Map<String,Object> args, TurnContext ctx);
}
