# Java Multithreaded Server

A project that demonstrates:
* A **thread‑pooled TCP server** (custom protocol)
* A **WebSocket server** that also serves a **single‑page UI**
* A **dashboard/metrics HTTP server**
* A simple **static file system** served two ways (HTTP & TCP)

---

## Quick start

```bash
mvn -U clean package
java -jar target/java-chat-server-jetty-1.0.0-jar-with-dependencies.jar
```

Open:

* **UI + WebSocket**: [http://localhost:8080/](http://localhost:8080/)
* **Dashboard**: [http://localhost:9000/](http://localhost:9000/)
* **Static files (dashboard)**: [http://localhost:9000/files/](http://localhost:9000/files/)
* **WebSocket endpoint**: `ws://localhost:8080/chat/{username}`
* **TCP server (custom protocol)**: `localhost:8081`

---
```
## Architecture overview

+---------------------------+             +-----------------------------+
|        TCP Server         |             |            Jetty            |
|           :8081           |             |             :8080           |
|---------------------------|             |-----------------------------|
| - ThreadPoolExecutor      |             | - Serves ./public at "/"    |
| - Line protocol:          |             | - WebSocket /chat/{user}    |
|   LOGIN, MSG, BROADCAST,  |             | - SPA (index.html + JS)     |
|   GETFILE, USERS, QUIT    |             +-----------------------------+
+-------------+-------------+                           |
              |                                         |
              | (register users, PM/broadcast)          | (register users, PM/broadcast)
              v                                         v
+---------------------------------------------------------------------+
|                          ClientRegistry                             |
|---------------------------------------------------------------------|
| - Tracks TCP clients (username → PrintWriter)                       |
| - Tracks WS clients  (username → WsSessionBridge)                   |
| - usernames(), broadcast(msg), sendToUser(user,msg), activeCount()  |
+----------------------+-------------------------------+--------------+
                       |                               |
                       | reads from ./public           | reads from ./public
                       v                               v
      +-----------------------------+       +-----------------------------+
      |     Dashboard HTTP Server   |       |          Jetty (8080)       |
      |            :9000            |       |             "/"             |
      |-----------------------------|       |  Static file server         |
      | - "/"  HTML overview        |       |  (DefaultServlet)           |
      | - "/metrics" JSON (+CORS)   |       +-----------------------------+
      | - "/files" static from      |
      |    ./public (mime, cache)   |
      +-----------------------------+

Flows:
- UI (http://localhost:8080/) ⇄ WS (ws://localhost:8080/chat/{username})
- UI fetches metrics from http://localhost:9000/metrics (CORS enabled)
- TCP clients connect to localhost:8081 and use the text commands
```
---

## Modules & key classes

### 1) TCP server (port 8081)

* **`multithreaded.ChatServer`**
  Creates a `ServerSocket`, accepts connections, and uses a **bounded** `ThreadPoolExecutor` (fixed size + queue) to handle clients. Starts Jetty (UI+WS) and the dashboard.

* **`multithreaded.ClientHandler`**
  Parses line‑based commands from each TCP client:

  * `LOGIN <name>` — register username
  * `MSG <to> <text>` — private message
  * `BROADCAST <text>` — broadcast to all users
  * `USERS` — list connected users (TCP + WS)
  * `GETFILE <relative-path>` — stream a file from `./public` (line by line)
  * `QUIT` — close the connection

* **`multithreaded.StaticFileReader`**
  Used by `GETFILE`. Validates the path inside `./public`, reads UTF‑8 lines, prints them to the socket, and ends with `OK`. Prevents traversal outside `public`.

### 2) WebSocket + static UI (port: 8080)

* **`multithreaded.WebSocketBootstrap`**
  A single Jetty instance serving:

  * Static files from `./public` at `/` (via `DefaultServlet`)
  * A WS endpoint `@ServerEndpoint("/chat/{username}")`
    On connect, the `{username}` path segment is the login.

* **Inner `ChatEndpoint` (in `WebSocketBootstrap`)**

  * `@OnOpen`: stores the session, registers user in **`ClientRegistry`** so metrics and Private Message dropdown work
  * `@OnMessage`:

    * Messages starting with `@user ` are **private**
    * Anything else is **broadcast**
  * `@OnClose`: unregisters the user

* **`public/` (UI)**
  * **`chat.js`**: connects to `ws://localhost:8080/chat/<username>`, renders messages, sends private messages using `@user message`, fetches metrics to populate the users list

### 3) Dashboard & files (port 9000)

* **`multithreaded.DashboardServer`**
  A small HTTP server (`com.sun.net.httpserver.HttpServer`) with:

  * `/` — HTML dashboard (active connection count + links)
  * `/metrics` — JSON: `{ activeConnections, users: [...] }`
    Adds `Access-Control-Allow-Origin: *` so the UI (8080) can fetch it.
  * `/files` — static file handler for `./public` (see below)

* **`multithreaded.StaticFiles`**
  Serves files from `./public` with:

  * Content types (html, css, js, png, jpg, svg, json, etc.)
  * Cache headers (`Cache-Control`, `Last-Modified`)
  * `HEAD` support
  * Safe path resolution (no `../` traversal)
  * Directory -> `index.html`

### 4) Shared state & utilities

* **`multithreaded.ClientRegistry`**
  Thread-safe maps of:

  * TCP clients: `username → PrintWriter`
  * WS clients:  `username → WsSessionBridge`
    Provides `sendToUser`, `broadcast`, `usernames`, `activeConnections`, `closeAll`.

* **`multithreaded.WsSessionBridge`**
  Thin wrapper around a WebSocket `Session` with `send()` and `close()` helpers.

---

## Ports & endpoints

| Component        | Port | Protocol            | Endpoints / Commands                                           |
| ---------------- | ---: | ------------------- | -------------------------------------------------------------- |
| Web Socket       | 8080 | HTTP / WS           | `/` (static UI), `ws://localhost:8080/chat/{username}`         |
| Dashboard server | 9000 | HTTP                | `/` (HTML), `/metrics` (JSON, CORS `*`), `/files/...` (static) |
| TCP server       | 8081 | TCP (line protocol) | `LOGIN`, `MSG`, `BROADCAST`, `USERS`, `GETFILE`, `QUIT`        |

---

## Using the system

### A) Use the browser UI

1. Start the app (see **Quick start**).
2. Open **[http://localhost:8080/](http://localhost:8080/)**.
3. Enter a username.
   The UI connects to `ws://localhost:8080/chat/<username>`.
4. Open a second tab with a different username.
5. **Broadcast**: type a message; it’s sent as plain text.
6. **Private message**: select a user and send — the UI sends `@target your message`.

The users list refreshes from `/metrics` (port 9000).

### B) Manual WebSocket test (without the UI)

In the browser console:

```js
const a = new WebSocket("ws://localhost:8080/chat/alice");
a.onmessage = e => console.log("alice:", e.data);

const b = new WebSocket("ws://localhost:8080/chat/bob");
b.onmessage = e => console.log("bob:", e.data);

a.onopen = () => { a.send("hello all"); a.send("@bob hi"); };
```

### C) Test the TCP server

**PowerShell**:

```powershell
$client = New-Object System.Net.Sockets.TcpClient("localhost",8081)
$stream = $client.GetStream()
$w = New-Object IO.StreamWriter($stream); $w.AutoFlush = $true
$r = New-Object IO.StreamReader($stream)

$r.ReadLine()                 # welcome
$w.WriteLine("LOGIN alice")   # login
$r.ReadLine()

$w.WriteLine("USERS")         # list users
$r.ReadLine()

$w.WriteLine("GETFILE test.html")  # stream file from ./public/test.html
while (($line = $r.ReadLine()) -ne $null) {
  $line; if ($line -eq "OK") { break }
}

$w.WriteLine("QUIT")          # exit
$r.ReadLine()
$client.Close()
```

---

## File system functionality

Your project returns static files **three ways**:

1. **WS server (8080)**

   * Static root is `./public`
   * `http://localhost:8080/` → `public/index.html`
   * `http://localhost:8080/test.html` → `public/test.html`

2. **Dashboard `/files` (9000)**

   * `http://localhost:9000/files/` → directory root (`public/`)
   * `http://localhost:9000/files/test.html` → `public/test.html`

3. **TCP `GETFILE` (8081)**

   * `GETFILE test.html`
   * Streams the file **line by line** to the TCP client, ends with `OK`.

**Safety**: both HTTP and TCP readers prevent path traversal outside `./public`. Unknown files → 404 / `ERR Not found`.

---

## Implementation details

**Thread pool**

* The server has a fixed number of worker threads to handle TCP clients.
* This means it can serve multiple people at the same time without getting overloaded.
* If too many people connect at once, extra work waits in a queue instead of crashing the server.

**Shared client list**

* Whether you connect via TCP or WebSocket, your username gets stored in the same list.
* This way, the `USERS` command and the `/metrics` dashboard show *everyone* connected, no matter how they joined.
* Private messages and broadcasts work between TCP and WebSocket clients seamlessly.

**WebSocket chat rules**

* You join by visiting: `ws://localhost:8080/chat/yourName`.
* If your message starts with `@username` it’s sent only to that person.
* If it doesn’t start with `@`, it’s sent to *everyone*.

**Live dashboard**

* Runs on port `9000` and shows a live list of connected users plus stats.
* It also has a `/metrics` link that gives the same info in a JSON format.
* The dashboard is set up so the browser UI on port 8080 can pull this data without being blocked (CORS enabled).
