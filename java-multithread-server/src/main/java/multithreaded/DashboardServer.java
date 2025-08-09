package multithreaded;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class DashboardServer {

    public static void startHttp(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        var registry = ClientRegistry.getInstance();

        server.createContext("/metrics", exchange -> {
            // Allow cross-origin requests (so your 8080 page can call 9000)
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            String body = """
        {
          "activeConnections": %d,
          "users": %s
        }
        """.formatted(
                    registry.activeConnections(),
                    registry.usernames().stream()
                            .map(u -> "\"" + u + "\"")
                            .collect(Collectors.joining(",", "[", "]"))
            );
            writeJson(exchange, body);
        });


        StaticFiles staticFiles = new StaticFiles(Path.of("public"));
        server.createContext("/files", exchange -> {
            try { staticFiles.handle(exchange); }
            catch (Exception e) { e.printStackTrace(); writeHtml(exchange, 500, "<h3>500 Internal Server Error</h3>"); }
        });

        server.createContext("/", exchange -> {
            String html = """
                <html><head><title>Server Dashboard</title></head>
                <body style="font-family: system-ui; max-width: 720px; margin: 2rem auto;">
                  <h2>Server Dashboard</h2>
                  <p><b>Active connections:</b> %d</p>
                  <p><b>Users:</b> %s</p>
                  <ul>
                    <li>Metrics JSON: <a href="/metrics">/metrics</a></li>
                    <li>Static site root: <a href="/files">/files</a></li>
                    <li>Example file: <a href="/files/index.html">/files/index.html</a></li>
                  </ul>
                </body></html>
                """.formatted(registry.activeConnections(), registry.usernames());
            writeHtml(exchange, 200, html);
        });

        server.start();
        System.out.println("[Dashboard+Static] http://localhost:" + port + "/  |  Static files at /files → ./public");
    }

    private static void writeJson(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void writeHtml(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
