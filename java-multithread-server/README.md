# Java Chat (Plain TCP) + WebSocket + Dashboard + Static Files

Features (no SSL/TLS):
- Plain TCP server (ServerSocket)
- ThreadPoolExecutor (bounded) for client handling
- Private messaging + broadcast across TCP & WebSocket
- WebSocket endpoint at `/ws/chat` (Tyrus)
- Dashboard (HTTP) + `/metrics`
- Static file server `/files` from `./public`

## Run
```bash
mvn -q -e -DskipTests package
java -jar target/java-chat-server-plain-1.0.0-jar-with-dependencies.jar
```

Ports:
- TCP: `8081`
- WebSocket: `ws://localhost:8080/ws/chat`
- Dashboard: `http://localhost:9000/`
- Static files: `http://localhost:9000/files`

### Commands (TCP & WS)
- `LOGIN <name>`
- `MSG <to> <text>`
- `BROADCAST <text>`
- `USERS`
- `GETFILE <relative-path>` (from `./public`)
- `QUIT`
