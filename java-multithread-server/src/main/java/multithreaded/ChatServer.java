package multithreaded;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.*;
import java.nio.file.Paths;

public class ChatServer {

    private final int port;
    private final ServerSocket serverSocket;
    private final ThreadPoolExecutor executor;
    private final ClientRegistry registry;

    public ChatServer(int port, int poolSize, int queueSize) throws Exception {
        this.port = port;
        this.serverSocket = new ServerSocket(port);
        this.serverSocket.setSoTimeout((int) Duration.ofSeconds(10000).toMillis());

        this.executor = new ThreadPoolExecutor(
                poolSize, poolSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new ThreadFactory() {
                    private final ThreadFactory d = Executors.defaultThreadFactory();
                    @Override public Thread newThread(Runnable r) {
                        Thread t = d.newThread(r);
                        t.setName("client-worker-" + t.getId());
                        t.setDaemon(false);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.registry = ClientRegistry.getInstance();
    }

    public void start() {
        System.out.println("[TCP] Listening on port " + port);
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                executor.execute(new ClientHandler(socket, registry));
            } catch (Exception e) {
                // accept timeout or other I/O errors ignored for loop continuity
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        registry.closeAll();
        try { serverSocket.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) throws Exception {
        int tcpPort = 8081;

        ChatServer server = new ChatServer(tcpPort, 32, 512);

        DashboardServer.startHttp(9000);
        WebSocketBootstrap.start(8080, Paths.get("public"));
        server.start();

    }
}
