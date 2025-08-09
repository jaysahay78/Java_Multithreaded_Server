package multithreaded;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/chat")
public class WsChatEndpoint {

    private static final ClientRegistry registry = ClientRegistry.getInstance();
    private static final Map<Session, String> sessionToUser = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        try { session.getBasicRemote().sendText("WELCOME (WS). Send: LOGIN <name>"); }
        catch (Exception ignored) {}
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        String[] parts = message.trim().split("\\s+", 3);
        if (parts.length == 0) return;
        String cmd = parts[0].toUpperCase();

        try {
            switch (cmd) {
                case "LOGIN" -> {
                    if (parts.length < 2) { session.getBasicRemote().sendText("ERR Usage: LOGIN <name>"); return; }
                    String name = parts[1];
                    WsSessionBridge bridge = new WsSessionBridge(session);
                    if (registry.addWs(name, bridge)) {
                        sessionToUser.put(session, name);
                        session.getBasicRemote().sendText("OK Logged in as " + name);
                    } else session.getBasicRemote().sendText("ERR Username taken");
                }
                case "MSG" -> {
                    String user = sessionToUser.get(session);
                    if (user == null) { session.getBasicRemote().sendText("ERR Login first"); return; }
                    if (parts.length < 3) { session.getBasicRemote().sendText("ERR Usage: MSG <to> <text>"); return; }
                    String to = parts[1];
                    String text = parts[2];
                    boolean sent = registry.sendToUser(to, "[PM from " + user + "] " + text);
                    session.getBasicRemote().sendText(sent ? "OK" : "ERR user not found");
                }
                case "BROADCAST" -> {
                    String user = sessionToUser.get(session);
                    if (user == null) { session.getBasicRemote().sendText("ERR Login first"); return; }
                    String text = parts.length >= 2 ? message.substring("BROADCAST".length()).trim() : "";
                    registry.broadcast("[ALL from " + user + "] " + text);
                    session.getBasicRemote().sendText("OK");
                }
                case "USERS" -> session.getBasicRemote().sendText("USERS " + registry.usernames());
                case "QUIT" -> { session.getBasicRemote().sendText("BYE"); session.close(); }
                default -> session.getBasicRemote().sendText("ERR Unknown command");
            }
        } catch (Exception ignored) {}
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String user = sessionToUser.remove(session);
        if (user != null) registry.removeWs(user);
    }

    @OnError
    public void onError(Session session, Throwable thr) {
        // log if needed
    }
}
