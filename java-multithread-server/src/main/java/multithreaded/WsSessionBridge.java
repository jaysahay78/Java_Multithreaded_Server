// multithreaded/WsSessionBridge.java
package multithreaded;

import jakarta.websocket.Session;

public class WsSessionBridge {
    private final Session session;
    public WsSessionBridge(Session session) { this.session = session; }
    public void send(String msg) {
        try { session.getBasicRemote().sendText(msg); } catch (Exception ignored) {}
    }
    public void close() {
        try { session.close(); } catch (Exception ignored) {}
    }
}
