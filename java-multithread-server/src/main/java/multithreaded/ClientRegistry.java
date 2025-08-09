package multithreaded;

import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {
    private static final ClientRegistry INSTANCE = new ClientRegistry();
    public static ClientRegistry getInstance() { return INSTANCE; }

    private final ConcurrentHashMap<String, PrintWriter> tcpClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WsSessionBridge> wsClients = new ConcurrentHashMap<>();

    public boolean addTcp(String username, PrintWriter out) {
        return tcpClients.putIfAbsent(username, out) == null;
    }
    public void removeTcp(String username) {
        tcpClients.remove(username);
    }

    public boolean addWs(String username, WsSessionBridge bridge) {
        return wsClients.putIfAbsent(username, bridge) == null;
    }
    public void removeWs(String username) {
        wsClients.remove(username);
    }

    public boolean sendToUser(String username, String message) {
        boolean ok = false;
        PrintWriter out = tcpClients.get(username);
        if (out != null) {
            out.println(message);
            ok = true;
        }
        WsSessionBridge ws = wsClients.get(username);
        if (ws != null) {
            ws.send(message);
            ok = true;
        }
        return ok;
    }

    public void broadcast(String message) {
        tcpClients.values().forEach(w -> w.println(message));
        wsClients.values().forEach(ws -> ws.send(message));
    }

    public int activeConnections() {
        return tcpClients.size() + wsClients.size();
    }

    public Set<String> usernames() {
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.addAll(tcpClients.keySet());
        set.addAll(wsClients.keySet());
        return set;
    }

    public void closeAll() {
        tcpClients.values().forEach(w -> { try { w.println("SERVER SHUTDOWN"); } catch (Exception ignored) {} });
        wsClients.values().forEach(ws -> { try { ws.close(); } catch (Exception ignored) {} });
        tcpClients.clear();
        wsClients.clear();
    }
}
