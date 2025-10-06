package com.intellicart.aiassistantservice.convo;
import java.util.*;
public class TurnContext {
    private final String sessionId;
    private final List<Map<String,String>> history = new ArrayList<>();
    private Long userId;
    private final List<Map<String,Object>> cart = new ArrayList<>();
    public TurnContext(String sessionId){ this.sessionId = sessionId; }
    public String sessionId(){ return sessionId; }
    public List<Map<String,String>> history(){ return history; }
    public void addUser(String m){ history.add(Map.of("role","user","content",m)); }
    public void addAssistant(String m){ history.add(Map.of("role","assistant","content",m)); }
    public Long userId(){ return userId; }
    public void setUserId(Long id){ userId = id; }
    public List<Map<String,Object>> cart(){ return cart; }
    public void clearCart(){ cart.clear(); }
}
