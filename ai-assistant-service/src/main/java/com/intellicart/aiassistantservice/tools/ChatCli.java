package com.intellicart.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatCli {

    // Defaults (can still be overridden by args: [human|chat|legacy] [host] [port] [session])
    static String HOST = "http://localhost";
    static int PORT = 8083;
    static Mode MODE = Mode.HUMAN;   // HUMAN -> POST /api/agent/chat/human (recommended)
    // CHAT  -> POST /api/agent/chat?forceRag=...
    // LEGACY-> GET  /customerSupportAgent
    static boolean FORCE_RAG = false;

    enum Mode { HUMAN, CHAT, LEGACY }

    // Natural-language patterns
    private static final Pattern P_ID     = Pattern.compile("\\b(my\\s+(user\\s+)?id\\s+is|use\\s+(user\\s+)?id)\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NEW    = Pattern.compile("\\b(new\\s+(session|chat)|start\\s+(a\\s+)?new\\s+(session|chat)|reset\\s+session)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_MODE   = Pattern.compile("\\b(switch\\s+to\\s+|use\\s+)?(human|chat|legacy)\\s+mode\\b|\\b(use\\s+(human|chat|legacy))\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_RAG    = Pattern.compile("\\b(rag|retrieval)\\s+(on|off)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SESSION_Q = Pattern.compile("\\b(what('?|\\s*is)\\s*(my|the)\\s*session(\\s*id)?|show\\s+session)\\b", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) {
            String m = args[0].toLowerCase();
            MODE = switch (m) {
                case "legacy" -> Mode.LEGACY;
                case "chat"   -> Mode.CHAT;
                default       -> Mode.HUMAN;
            };
        }
        if (args.length >= 2) HOST = args[1];
        if (args.length >= 3) PORT = Integer.parseInt(args[2]);

        String sessionId = (args.length >= 4) ? args[3] : UUID.randomUUID().toString();
        Long userId = null;

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        // Minimal startup info (no prompts/examples)
        System.out.println("Connected to " + HOST + ":" + PORT + "  (session=" + sessionId + ", mode=" + MODE + ", forceRag=" + FORCE_RAG + ")");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\nYou> ");
            String line = br.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            // Handle “my id is 123” / “use user id 5”
            Matcher mid = P_ID.matcher(line);
            if (mid.find()) {
                String num = mid.group(4); // the digits
                try {
                    userId = Long.parseLong(num);
                    System.out.println("(ok, user id set to " + userId + ")");
                } catch (NumberFormatException e) {
                    System.out.println("(could not read a valid id)");
                }
                // Strip the id phrase and continue if anything remains
                line = (mid.replaceFirst("")).trim();
                if (line.isEmpty()) continue;
            }

            // Handle “start a new session / new chat / reset session”
            if (P_NEW.matcher(line).find()) {
                sessionId = UUID.randomUUID().toString();
                System.out.println("(new session: " + sessionId + ")");
                line = P_NEW.matcher(line).replaceAll("").trim();
                if (line.isEmpty()) continue;
            }

            // Handle “switch to human/chat/legacy mode” or “use human”
            Matcher mmode = P_MODE.matcher(line);
            if (mmode.find()) {
                String m = mmode.group(2); // may be null if matched the "use <mode>"
                if (m == null && mmode.groupCount() >= 4) m = mmode.group(4);
                if (m != null) {
                    String mv = m.toLowerCase();
                    MODE = switch (mv) {
                        case "chat"   -> Mode.CHAT;
                        case "legacy" -> Mode.LEGACY;
                        default       -> Mode.HUMAN;
                    };
                    System.out.println("(mode switched to " + MODE + ")");
                }
                line = mmode.replaceAll("").trim();
                if (line.isEmpty()) continue;
            }

            // Handle “rag on/off”
            Matcher mrag = P_RAG.matcher(line);
            if (mrag.find()) {
                boolean on = mrag.group(2).equalsIgnoreCase("on");
                FORCE_RAG = on;
                System.out.println("(forceRag=" + FORCE_RAG + ")");
                line = mrag.replaceAll("").trim();
                if (line.isEmpty()) continue;
            }

            // Handle “what’s my session”
            if (P_SESSION_Q.matcher(line).find()) {
                System.out.println("(current session: " + sessionId + ")");
                line = P_SESSION_Q.matcher(line).replaceAll("").trim();
                if (line.isEmpty()) continue;
            }

            // Send remaining natural-language message
            try {
                HttpRequest req;
                if (MODE == Mode.LEGACY) {
                    String url = String.format("%s:%d/customerSupportAgent?sessionId=%s&userMessage=%s",
                            HOST, PORT, urlEncode(sessionId), urlEncode(line));
                    req = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();
                } else if (MODE == Mode.CHAT) {
                    String url = String.format("%s:%d/api/agent/chat?forceRag=%s", HOST, PORT, FORCE_RAG);
                    String body = "{\"message\":\"" + escapeJson(line) + "\"}";
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .header("X-Session-Id", sessionId)
                            .POST(HttpRequest.BodyPublishers.ofString(body));
                    if (userId != null) b.header("X-User-Id", String.valueOf(userId));
                    req = b.build();
                } else {
                    String url = String.format("%s:%d/api/agent/chat/human", HOST, PORT);
                    String body = "{\"message\":\"" + escapeJson(line) + "\"}";
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .header("X-Session-Id", sessionId)
                            .POST(HttpRequest.BodyPublishers.ofString(body));
                    if (userId != null) b.header("X-User-Id", String.valueOf(userId));
                    req = b.build();
                }

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                String ct = resp.headers().firstValue("content-type").orElse("");
                String body = resp.body();
                int status = resp.statusCode();

                if (ct.contains("application/json")) {
                    String answer = extractJsonField(body, "answer");
                    String sidOut = extractJsonField(body, "sessionId");
                    if (sidOut != null && !sidOut.isBlank() && !sidOut.equals(sessionId)) {
                        sessionId = sidOut; // follow server-provided session if it changes
                    }
                    if (answer == null) {
                        System.out.println("Assistant> [" + status + "] " + body);
                    } else {
                        System.out.println("Assistant> " + unescape(answer));
                    }
                } else {
                    System.out.println("Assistant> [" + status + "] " + body);
                }
            } catch (Exception e) {
                System.out.println("Assistant> [error] " + e.getMessage());
            }
        }
    }

    // --- helpers ---

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String urlEncode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    /** tiny JSON string extractor: finds "field":"value" (first occurrence) */
    static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + key.length());
        if (colon < 0) return null;

        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;

        if (j < json.length() && json.charAt(j) == '"') {
            StringBuilder sb = new StringBuilder();
            boolean escaping = false;
            for (int k = j + 1; k < json.length(); k++) {
                char c = json.charAt(k);
                if (escaping) { sb.append(c); escaping = false; continue; }
                if (c == '\\') { escaping = true; continue; }
                if (c == '"') break;
                sb.append(c);
            }
            return sb.toString();
        }
        return null;
    }

    /** minimal JSON-style unescape for readability */
    static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!esc) {
                if (c == '\\') { esc = true; continue; }
                out.append(c);
            } else {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('\"');
                    case '\\'-> out.append('\\');
                    default   -> out.append(c);
                }
                esc = false;
            }
        }
        return out.toString();
    }
}
