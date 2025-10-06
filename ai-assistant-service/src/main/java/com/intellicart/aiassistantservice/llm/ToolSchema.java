package com.intellicart.aiassistantservice.llm;
public final class ToolSchema {
    public static final String USER = """
  {"name":"userTool","description":"User directory",
   "parameters":{"type":"object","properties":{
     "action":{"type":"string","enum":["listUsers","getUser","resolveUserByName"]},
     "userId":{"type":"integer"},"username":{"type":"string"}}, "required":["action"]}}""";

    public static final String ORDER = """
  {"name":"orderTool","description":"Orders and checkout",
   "parameters":{"type":"object","properties":{
     "action":{"type":"string","enum":["listOrdersForUser","checkout"]},
     "userId":{"type":"integer"}}, "required":["action"]}}""";

    public static final String BOOK = """
  {"name":"bookTool","description":"Catalogue & recommendations",
   "parameters":{"type":"object","properties":{
     "action":{"type":"string","enum":["recommend","search"]},
     "preference":{"type":"string"},"query":{"type":"string"}}, "required":["action"]}}""";
}
