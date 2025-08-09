package multithreaded;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

final class StaticFiles {
    private final Path baseDir;
    private final Map<String, String> mime = new HashMap<>();

    StaticFiles(Path baseDir) {
        this.baseDir = baseDir.normalize().toAbsolutePath();
        mime.put("html", "text/html; charset=utf-8");
        mime.put("htm",  "text/html; charset=utf-8");
        mime.put("css",  "text/css; charset=utf-8");
        mime.put("js",   "application/javascript; charset=utf-8");
        mime.put("json", "application/json; charset=utf-8");
        mime.put("png",  "image/png");
        mime.put("jpg",  "image/jpeg");
        mime.put("jpeg", "image/jpeg");
        mime.put("gif",  "image/gif");
        mime.put("svg",  "image/svg+xml");
        mime.put("ico",  "image/x-icon");
        mime.put("txt",  "text/plain; charset=utf-8");
        mime.put("map",  "application/json; charset=utf-8");
        mime.put("wasm", "application/wasm");
    }

    void handle(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            sendStatus(ex, 405, "Method Not Allowed");
            return;
        }

        String raw = ex.getRequestURI().getPath();
        String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        String rel = decoded.replaceFirst("^/files/?", "");

        if (rel.isEmpty()) rel = "index.html";

        Path target = baseDir.resolve(rel).normalize().toAbsolutePath();
        if (!target.startsWith(baseDir)) {
            sendStatus(ex, 403, "Forbidden");
            return;
        }

        if (Files.isDirectory(target)) {
            target = target.resolve("index.html");
        }

        if (!Files.exists(target) || !Files.isReadable(target)) {
            sendStatus(ex, 404, "Not Found");
            return;
        }

        String ext = ext(target.getFileName().toString());
        String ct = mime.getOrDefault(ext, "application/octet-stream");
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=300");
        ex.getResponseHeaders().set("Last-Modified", Instant.ofEpochMilli(Files.getLastModifiedTime(target).toMillis()).toString());

        long len = Files.size(target);
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }

        ex.sendResponseHeaders(200, len);
        try (OutputStream os = ex.getResponseBody()) {
            Files.copy(target, os);
        }
    }

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return (i == -1) ? "" : name.substring(i + 1).toLowerCase();
    }

    private static void sendStatus(HttpExchange ex, int code, String msg) throws IOException {
        byte[] body = ("<h3>" + code + " " + msg + "</h3>").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}
