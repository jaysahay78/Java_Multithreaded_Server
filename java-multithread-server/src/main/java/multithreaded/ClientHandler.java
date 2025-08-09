package multithreaded;

import java.io.*;
import java.net.Socket;
import java.util.Locale;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ClientRegistry registry;
    private String username = null;

    public ClientHandler(Socket socket, ClientRegistry registry) {
        this.socket = socket;
        this.registry = registry;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true)) {

            out.println("WELCOME. Commands: LOGIN <name>, MSG <to> <text>, BROADCAST <text>, USERS, QUIT, GETFILE <path>");

            String line;
            while ((line = in.readLine()) != null) {
                String resp = handle(line, out);
                if (resp != null) out.println(resp);
                if ("BYE".equals(resp)) break;
            }
        } catch (IOException ignored) {
        } finally {
            if (username != null) {
                registry.removeTcp(username);
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String handle(String input, PrintWriter out) {
        String[] parts = input.trim().split("\\s+", 3);
        if (parts.length == 0) return "ERR Empty";

        String cmd = parts[0].toUpperCase(Locale.ROOT);

        switch (cmd) {
            case "LOGIN": {
                if (parts.length < 2) return "ERR Usage: LOGIN <name>";
                String name = parts[1];
                if (registry.addTcp(name, out)) {
                    this.username = name;
                    return "OK Logged in as " + name;
                } else {
                    return "ERR Username taken";
                }
            }
            case "MSG": {
                if (username == null) return "ERR Login first";
                if (parts.length < 3) return "ERR Usage: MSG <to> <text>";
                String to = parts[1];
                String msg = parts.length == 3 ? parts[2] : "";
                boolean sent = registry.sendToUser(to, "[PM from " + username + "] " + msg);
                return sent ? "OK" : "ERR user not found";
            }
            case "BROADCAST": {
                if (username == null) return "ERR Login first";
                String msg = parts.length >= 2 ? input.substring("BROADCAST".length()).trim() : "";
                registry.broadcast("[ALL from " + username + "] " + msg);
                return "OK";
            }
            case "USERS": {
                return "USERS " + registry.usernames();
            }
            case "GETFILE": {
                if (parts.length < 2) return "ERR Usage: GETFILE <relative-path>";
                return StaticFileReader.readTextFromPublic(parts[1], out);
            }
            case "QUIT": {
                return "BYE";
            }
            default:
                return "ERR Unknown command";
        }
    }
}
