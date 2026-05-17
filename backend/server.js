const express = require('express');

const app = express();
app.use(express.json({ limit: '16kb' }));

const PORT = process.env.PORT || 3000;
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '';

function parseAliasMap(raw) {
  var map = {};
  if (!raw) return map;
  var pairs = raw.split(',');
  for (var i = 0; i < pairs.length; i++) {
    var p = pairs[i];
    var idx = p.indexOf(':');
    if (idx <= 0) continue;
    var alias = p.substring(0, idx).trim();
    var chatId = p.substring(idx + 1).trim();
    if (alias.length > 0 && chatId.length > 0) map[alias] = chatId;
  }
  return map;
}

// alias -> telegram chat id (configured via env: ALIAS_MAP)
const aliasMap = parseAliasMap(process.env.ALIAS_MAP || '');

// deviceId -> [{id, fromAlias, text, ts}]
const inbox = {};

function pushInbox(deviceId, msg) {
  if (!inbox[deviceId]) inbox[deviceId] = [];
  inbox[deviceId].push(msg);
  if (inbox[deviceId].length > 200) inbox[deviceId].shift();
}

app.get('/health', (req, res) => res.json({ ok: true, now: Date.now() }));

app.post('/send', async (req, res) => {
  const { deviceId, to, text } = req.body || {};
  if (!deviceId || !to || !text) {
    return res.status(400).json({ ok: false, error: 'deviceId,to,text required' });
  }
  const chatId = aliasMap[to];
  if (!chatId) {
    return res.status(404).json({ ok: false, error: 'unknown alias (check ALIAS_MAP)' });
  }

  const message = `[${deviceId}] ${text}`;

  try {
    if (!TELEGRAM_BOT_TOKEN) {
      return res.status(500).json({ ok: false, error: 'missing TELEGRAM_BOT_TOKEN' });
    }
    const tgResp = await fetch(`https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: chatId, text: message })
    });
    const data = await tgResp.json();
    if (!data.ok) {
      return res.status(502).json({ ok: false, error: 'telegram send failed', detail: data });
    }

    return res.json({ ok: true, status: 'sent', serverTime: Date.now() });
  } catch (err) {
    return res.status(500).json({ ok: false, error: 'send_exception', detail: String(err) });
  }
});

app.post('/telegram/webhook', (req, res) => {
  const update = req.body || {};
  const msg = update.message;
  if (msg && msg.text) {
    const fromAlias = (msg.from && (msg.from.username || msg.from.first_name)) || 'tg';
    const deviceId = 'KP1001'; // TODO: map by conversation/user.
    pushInbox(deviceId, {
      id: String(msg.message_id),
      fromAlias,
      text: msg.text,
      ts: Date.now()
    });
  }
  res.json({ ok: true });
});

app.get('/inbox', (req, res) => {
  const deviceId = req.query.deviceId;
  const since = Number(req.query.since || 0);
  if (!deviceId) return res.status(400).json({ ok: false, error: 'deviceId required' });

  const list = (inbox[deviceId] || []).filter(m => m.ts > since);
  res.json({ ok: true, messages: list, serverTime: Date.now() });
});

app.listen(PORT, () => {
  console.log(`Bridge server listening on :${PORT}`);
  console.log('Configured aliases:', Object.keys(aliasMap).length);
});
