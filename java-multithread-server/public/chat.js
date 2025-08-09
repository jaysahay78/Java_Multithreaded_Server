// ===== WebSocket Chat UI (Jetty endpoint) =====

// Endpoints
// Endpoints
const METRICS_URL = 'http://localhost:9000/metrics';
// And keep:
const makeWsUrl = (name) => `ws://localhost:8080/chat/${encodeURIComponent(name)}`;


// State
let ws = null;
let myName = null;
let users = [];
let reconnectTimer = null;

// Elements
const el = (id) => document.getElementById(id);
const logEl = el('log');
const meEl = el('me');
const wsStatusEl = el('wsStatus');
const userListEl = el('userList');
const pmToEl = el('pmTo');
const pmTextEl = el('pmText');
const pmSendBtn = el('pmSend');
const bcTextEl = el('bcText');
const bcSendBtn = el('bcSend');
const loginPanel = el('loginPanel');
const usernameEl = el('username');
const loginBtn = el('loginBtn');
const refreshUsersBtn = el('refreshUsers');
const userCountEl = el('userCount');

function log(msg){
  const ts = new Date().toLocaleTimeString();
  logEl.textContent += `[${ts}] ${msg}\n`;
  logEl.scrollTop = logEl.scrollHeight;
}

function setWsStatus(connected){
  if(connected){
    wsStatusEl.textContent = 'WS: connected';
    wsStatusEl.classList.remove('warn');
    wsStatusEl.classList.add('ok');
  } else {
    wsStatusEl.textContent = 'WS: disconnected';
    wsStatusEl.classList.add('warn');
    wsStatusEl.classList.remove('ok');
  }
}

function connectWS(name){
  if(!name) return;
  if(ws && ws.readyState === 1) return;

  const url = makeWsUrl(name);
  ws = new WebSocket(url);

  ws.onopen = () => {
    setWsStatus(true);
    log(`WebSocket connected as ${name}`);
  };

  ws.onmessage = (e) => log(e.data);

  ws.onclose = () => {
    setWsStatus(false);
    log('WebSocket closed – retrying in 1s…');
    clearTimeout(reconnectTimer);
    reconnectTimer = setTimeout(() => connectWS(myName), 1000);
  };

  ws.onerror = () => setWsStatus(false);
}

function doLogin(){
  const name = usernameEl.value.trim();
  if(!name) return;
  myName = name;
  meEl.textContent = `Me: ${name}`;
  loginPanel.style.display = 'none';
  connectWS(myName);
  refreshUsers();
}

function refreshUsers(){
  fetch(METRICS_URL).then(r => r.json()).then(j => {
    users = Array.isArray(j.users) ? j.users : [];
    const pmUsers = users.filter(u => u !== myName);

    userListEl.innerHTML = users.map(u => `<option>${u}</option>`).join('');
    pmToEl.innerHTML = pmUsers.length
        ? pmUsers.map(u => `<option value="${u}">${u}</option>`).join('')
        : '<option value="" disabled>(no users)</option>';

    userCountEl.textContent = `${users.length} active`;
  }).catch(() => {});
}

function wsReady(){
  return ws && ws.readyState === WebSocket.OPEN;
}

// PM: prefix with @<user> + space, then message
function sendPM(){
  const to = pmToEl.value;
  const text = pmTextEl.value.trim();
  if(!to || !text) return;
  if(!wsReady()){ log('WS not open'); return; }
  ws.send(`@${to} ${text}`);
  log(`(you → ${to}) ${text}`);
  pmTextEl.value = '';
}

// Broadcast: send plain text
function sendBC(){
  const text = bcTextEl.value.trim();
  if(!text) return;
  if(!wsReady()){ log('WS not open'); return; }
  ws.send(text);
  log(`(you → ALL) ${text}`);
  bcTextEl.value = '';
}

// Wire up UI
loginBtn.onclick = doLogin;
usernameEl.addEventListener('keydown', e => { if(e.key === 'Enter') doLogin(); });

pmSendBtn.onclick = sendPM;
pmTextEl.addEventListener('keydown', e => { if(e.key === 'Enter') sendPM(); });

bcSendBtn.onclick = sendBC;
bcTextEl.addEventListener('keydown', e => { if(e.key === 'Enter') sendBC(); });

refreshUsersBtn.onclick = refreshUsers;

// Start: don't connect until user logs in (we need the name in the URL)
refreshUsers();
setInterval(refreshUsers, 3000);
