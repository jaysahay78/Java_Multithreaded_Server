package multithreaded;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WebSocketBootstrap {

    private static final ClientRegistry registry = ClientRegistry.getInstance();

    private static Server jetty; // one Jetty instance for both static files + WS
    // Keep WS sessions here only for the WS path on Jetty (your TCP server stays separate)
    private static final Map<String, Session> clients = new ConcurrentHashMap<>();

    @ServerEndpoint("/chat/{username}")
    public static class ChatEndpoint {
        private static final Map<String, Session> clients = new ConcurrentHashMap<>();
        private static final ClientRegistry registry = ClientRegistry.getInstance();

        @OnOpen
        public void onOpen(Session session, @PathParam("username") String username) {
            clients.put(username, session);
            registry.addWs(username, new WsSessionBridge(session)); // make /metrics see WS users
            broadcast("SERVER", username + " joined the chat");
        }

        @OnMessage
        public void onMessage(String message, @PathParam("username") String username) {
            if (message.startsWith("@")) {
                String[] parts = message.split(" ", 2);
                String target = parts[0].substring(1);
                sendPrivate(username, target, parts.length > 1 ? parts[1] : "");
            } else {
                broadcast(username, message);
            }
        }

        @OnClose
        public void onClose(Session session, @PathParam("username") String username) {
            clients.remove(username);
            registry.removeWs(username); // keep registry in sync
            broadcast("SERVER", username + " left the chat");
        }

        private static void sendPrivate(String from, String to, String message) {
            Session target = clients.get(to);
            if (target != null) {
                try { target.getBasicRemote().sendText("[PM from " + from + "]: " + message); } catch (Exception ignored) {}
            }
        }

        private static void broadcast(String from, String message) {
            for (Session s : List.copyOf(clients.values())) {
                try { s.getBasicRemote().sendText(from + ": " + message); } catch (Exception ignored) {}
            }
        }
    }


    /** Start Jetty serving static files from publicDir and WS at /chat/{username}. */
    public static void start(int port, Path publicDir) throws Exception {
        if (jetty != null && jetty.isStarted()) return;

        jetty = new Server(port);

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        // Static files: map "/" to your public directory
        ctx.setBaseResource(Resource.newResource(publicDir.toUri()));
        ctx.addServlet(DefaultServlet.class, "/");

        // WebSocket: register annotated endpoint
        JakartaWebSocketServletContainerInitializer.configure(ctx, (servletContext, wsContainer) -> {
            wsContainer.addEndpoint(ChatEndpoint.class);
        });

        jetty.setHandler(ctx);
        jetty.start();

        System.out.println("[Jetty] Static files  → http://localhost:" + port + "/  (serving " + publicDir.toAbsolutePath() + ")");
        System.out.println("[Jetty] WebSocket     → ws://localhost:" + port + "/chat/{username}");
    }

    public static void stop() {
        if (jetty != null) {
            try { jetty.stop(); } catch (Exception ignored) {}
        }
    }
}
