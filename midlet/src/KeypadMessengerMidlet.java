import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.*;

/**
 * KeypadMessenger - Modern styled J2ME MIDlet with persistent storage & auto-poll.
 */
public class KeypadMessengerMidlet extends MIDlet implements CommandListener, Runnable {

    private Display display;

    // ── Forms ──────────────────────────────────────────────────────────────────
    private Form    configForm;
    private TextField baseUrlField;
    private TextField deviceIdField;

    private Form    sendForm;
    private TextField toField;
    private TextField messageField;
    private StringItem statusBar;

    private Form    inboxForm;

    // ── Commands ───────────────────────────────────────────────────────────────
    private Command cmdSave   = new Command("Save",    Command.OK,     1);
    private Command cmdNext   = new Command("Next",    Command.OK,     1);
    private Command cmdBack   = new Command("Back",    Command.BACK,   1);
    private Command cmdSend   = new Command("Send",    Command.OK,     1);
    private Command cmdInbox  = new Command("Inbox",   Command.SCREEN, 2);
    private Command cmdClear  = new Command("Clear",   Command.SCREEN, 3);

    // ── State ──────────────────────────────────────────────────────────────────
    private String  baseUrl   = "http://messengerforkeypad.onrender.com";
    private String  deviceId  = "DevilxG";
    private long    since     = 0;

    // ── Persistent storage record IDs ──────────────────────────────────────────
    private static final String SETTINGS_STORE = "KMSettings";
    private static final String INBOX_STORE    = "KMInbox";
    private static final int    MAX_INBOX_MSGS = 30;   // keep last 30 messages

    // ── Background polling ─────────────────────────────────────────────────────
    private Thread  pollThread;
    private boolean polling = false;
    private static final int POLL_INTERVAL_MS = 8000;  // 8 seconds

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    public void startApp() {
        display = Display.getDisplay(this);
        loadSettings();
        showConfig();
        startPolling();
    }

    public void pauseApp() { stopPolling(); }

    public void destroyApp(boolean unconditional) {
        stopPolling();
        notifyDestroyed();
    }

    // ── Polling thread ─────────────────────────────────────────────────────────
    private void startPolling() {
        polling    = true;
        pollThread = new Thread(this);
        pollThread.start();
    }

    private void stopPolling() {
        polling = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    /** Background thread: polls /inbox every 8 seconds and saves new messages. */
    public void run() {
        while (polling) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
            if (!polling) break;

            // Only poll if we have a configured server
            if (baseUrl.indexOf("YOUR_SERVER") >= 0) continue;

            try {
                String resp = httpGet(baseUrl + "/inbox?deviceId=" + url(deviceId) + "&since=" + since);
                if (resp != null && resp.indexOf("ERR:") != 0) {
                    // Parse new messages and store them
                    processInboxResponse(resp);
                    // Update status bar on send screen
                    if (statusBar != null && sendForm != null) {
                        statusBar.setText("Last synced: " + currentTime());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Parses inbox JSON response, extracts messages, and appends new ones
     * to local RecordStore. Also updates the 'since' timestamp.
     */
    private void processInboxResponse(String resp) {
        // Update since timestamp
        int idx = resp.indexOf("\"serverTime\":");
        if (idx >= 0) {
            int start = idx + 13, end = start;
            while (end < resp.length() && Character.isDigit(resp.charAt(end))) end++;
            try { since = Long.parseLong(resp.substring(start, end)); } catch (Exception ignored) {}
        }

        // Extract messages array content between [ and ]
        int arrStart = resp.indexOf("\"messages\":[");
        if (arrStart < 0) return;
        arrStart += 12;
        int arrEnd = resp.indexOf("]", arrStart);
        if (arrEnd < 0) return;
        String arrContent = resp.substring(arrStart, arrEnd).trim();
        if (arrContent.length() == 0) return;

        // Split messages by "},{" pattern
        // Each message object: {"from":"name","text":"hello","ts":12345}
        String[] parts = splitJson(arrContent);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            String from = extractField(part, "from");
            String text = extractField(part, "text");
            String ts   = extractField(part, "ts");
            if (from != null && text != null) {
                String formatted = "[" + ts + "] " + from + ": " + text;
                appendToInbox(formatted);
            }
        }
    }

    /** Very simple JSON string field extractor */
    private String extractField(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        if (idx >= json.length()) return null;
        char first = json.charAt(idx);
        if (first == '"') {
            // String value
            int end = json.indexOf("\"", idx + 1);
            if (end < 0) return null;
            return json.substring(idx + 1, end);
        } else {
            // Numeric value
            int end = idx;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            return json.substring(idx, end);
        }
    }

    /** Splits a JSON array content by top-level objects */
    private String[] splitJson(String s) {
        // Simple split by },{
        java.util.Vector parts = new java.util.Vector();
        // Remove leading/trailing { }
        int start = 0;
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    parts.addElement(s.substring(objStart + 1, i));
                    objStart = -1;
                }
            }
        }
        String[] result = new String[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = (String) parts.elementAt(i);
        }
        return result;
    }

    // ── Screens ────────────────────────────────────────────────────────────────
    private void showConfig() {
        configForm = new Form("\u2699 Setup");

        StringItem header = new StringItem(null, "Server Setup");
        header.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        configForm.append(header);

        StringItem sub = new StringItem(null, "Saved settings are loaded automatically");
        sub.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        configForm.append(sub);
        configForm.append(new Spacer(0, 6));

        baseUrlField  = new TextField("Server URL", baseUrl,  128, TextField.URL);
        deviceIdField = new TextField("Device ID",  deviceId, 32,  TextField.ANY);

        configForm.append(baseUrlField);
        configForm.append(deviceIdField);
        configForm.addCommand(cmdSave);
        configForm.addCommand(cmdNext);
        configForm.setCommandListener(this);
        display.setCurrent(configForm);
    }

    private void showSend() {
        sendForm = new Form("Send");

        StringItem banner = new StringItem(null, "KeypadMessenger");
        banner.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        sendForm.append(banner);

        statusBar = new StringItem(null, "Auto-sync every 8s");
        statusBar.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        sendForm.append(statusBar);
        sendForm.append(new Spacer(0, 4));

        toField      = new TextField("To (alias)", "", 32,  TextField.ANY);
        messageField = new TextField("Message",    "", 300, TextField.ANY);

        sendForm.append(toField);
        sendForm.append(messageField);
        sendForm.addCommand(cmdSend);
        sendForm.addCommand(cmdInbox);
        sendForm.addCommand(cmdBack);
        sendForm.setCommandListener(this);
        display.setCurrent(sendForm);
    }

    private void showInbox() {
        inboxForm = new Form("Inbox");

        StringItem inboxHeader = new StringItem(null, "Received Messages");
        inboxHeader.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        inboxForm.append(inboxHeader);

        StringItem syncing = new StringItem(null, "Auto-syncing every 8s");
        syncing.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        inboxForm.append(syncing);
        inboxForm.append(new Spacer(0, 4));

        // Load messages from local RecordStore
        String[] msgs = loadInbox();
        if (msgs.length == 0) {
            StringItem empty = new StringItem(null, "No messages yet.\nMessages arrive automatically.");
            empty.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            inboxForm.append(empty);
        } else {
            // Show newest first
            for (int i = msgs.length - 1; i >= 0; i--) {
                StringItem item = new StringItem(null, msgs[i] + "\n");
                item.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
                inboxForm.append(item);
            }
        }

        inboxForm.addCommand(cmdBack);
        inboxForm.addCommand(cmdClear);
        inboxForm.setCommandListener(this);
        display.setCurrent(inboxForm);
    }

    // ── Commands ───────────────────────────────────────────────────────────────
    public void commandAction(Command c, Displayable d) {
        if (c == cmdSave) {
            baseUrl  = baseUrlField.getString();
            deviceId = deviceIdField.getString();
            saveSettings();
            alert("Settings saved!");
        } else if (c == cmdNext) {
            baseUrl  = baseUrlField.getString();
            deviceId = deviceIdField.getString();
            saveSettings();
            showSend();
        } else if (c == cmdSend) {
            String to   = toField.getString();
            String text = messageField.getString();
            if (to.length() == 0 || text.length() == 0) {
                alert("Fill in both fields.");
                return;
            }
            String body = "{\"deviceId\":\"" + esc(deviceId) + "\",\"to\":\"" + esc(to) + "\",\"text\":\"" + esc(text) + "\"}";
            String resp = httpPost(baseUrl + "/send", body);
            if (resp != null && resp.indexOf("\"ok\":true") >= 0) {
                alert("Message sent!");
            } else {
                alert("Failed: " + shortText(resp));
            }
            messageField.setString("");
        } else if (c == cmdInbox) {
            showInbox();
        } else if (c == cmdClear) {
            clearInbox();
            showInbox();
        } else if (c == cmdBack) {
            if (d == inboxForm) {
                showSend();
            } else {
                showConfig();
            }
        }
    }

    // ── Persistent Settings (RecordStore) ──────────────────────────────────────
    private void saveSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SETTINGS_STORE, true);
            String data = baseUrl + "\n" + deviceId + "\n" + since;
            byte[] bytes = data.getBytes("UTF-8");
            if (rs.getNumRecords() == 0) {
                rs.addRecord(bytes, 0, bytes.length);
            } else {
                rs.setRecord(1, bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            // Silently fail - not critical
        } finally {
            closeStore(rs);
        }
    }

    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SETTINGS_STORE, false);
            if (rs.getNumRecords() > 0) {
                byte[] bytes = rs.getRecord(1);
                String data  = new String(bytes, "UTF-8");
                int    nl1   = data.indexOf('\n');
                int    nl2   = data.indexOf('\n', nl1 + 1);
                if (nl1 > 0) {
                    baseUrl  = data.substring(0, nl1);
                }
                if (nl2 > nl1) {
                    deviceId = data.substring(nl1 + 1, nl2);
                    try {
                        since = Long.parseLong(data.substring(nl2 + 1).trim());
                    } catch (Exception ignored) {}
                }
            }
        } catch (RecordStoreNotFoundException e) {
            // First run - use defaults
        } catch (Exception e) {
            // Use defaults
        } finally {
            closeStore(rs);
        }
    }

    // ── Persistent Inbox (RecordStore) ─────────────────────────────────────────
    private void appendToInbox(String message) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(INBOX_STORE, true);
            // Trim to MAX_INBOX_MSGS - delete oldest if needed
            while (rs.getNumRecords() >= MAX_INBOX_MSGS) {
                rs.deleteRecord(rs.getNextRecordID() - rs.getNumRecords());
            }
            byte[] bytes = message.getBytes("UTF-8");
            rs.addRecord(bytes, 0, bytes.length);
        } catch (Exception ignored) {
        } finally {
            closeStore(rs);
        }
    }

    private String[] loadInbox() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(INBOX_STORE, false);
            int count = rs.getNumRecords();
            String[] msgs = new String[count];
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            int i = 0;
            while (re.hasNextElement()) {
                try {
                    msgs[i++] = new String(re.nextRecord(), "UTF-8");
                } catch (Exception ignored) {}
            }
            return msgs;
        } catch (RecordStoreNotFoundException e) {
            return new String[0];
        } catch (Exception e) {
            return new String[0];
        } finally {
            closeStore(rs);
        }
    }

    private void clearInbox() {
        try {
            RecordStore.deleteRecordStore(INBOX_STORE);
        } catch (Exception ignored) {}
    }

    private void closeStore(RecordStore rs) {
        if (rs != null) {
            try { rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private String currentTime() {
        long ms = System.currentTimeMillis() % 86400000L;
        int h = (int)(ms / 3600000);
        int m = (int)((ms % 3600000) / 60000);
        int s = (int)((ms % 60000)   / 1000);
        return pad(h) + ":" + pad(m) + ":" + pad(s);
    }

    private String pad(int n) { return n < 10 ? "0" + n : "" + n; }

    private void alert(String text) {
        Alert a = new Alert("Info", text, null, AlertType.INFO);
        a.setTimeout(2000);
        display.setCurrent(a, sendForm != null ? sendForm : configForm);
    }

    private String httpPost(String endpoint, String payload) {
        HttpConnection conn = null;
        OutputStream os     = null;
        InputStream  is     = null;
        try {
            conn = (HttpConnection) Connector.open(endpoint, Connector.READ_WRITE);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] data = payload.getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            os = conn.openOutputStream();
            os.write(data);
            os.flush();
            is = conn.openInputStream();
            return readAll(is);
        } catch (Exception e) {
            return "ERR:" + e.toString();
        } finally {
            closeQuietly(is); closeQuietly(os); closeQuietly(conn);
        }
    }

    private String httpGet(String endpoint) {
        HttpConnection conn = null;
        InputStream    is   = null;
        try {
            conn = (HttpConnection) Connector.open(endpoint);
            conn.setRequestMethod(HttpConnection.GET);
            is = conn.openInputStream();
            return readAll(is);
        } catch (Exception e) {
            return "ERR:" + e.toString();
        } finally {
            closeQuietly(is); closeQuietly(conn);
        }
    }

    private String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int r;
        while ((r = is.read(buf)) != -1) bos.write(buf, 0, r);
        return new String(bos.toByteArray(), "UTF-8");
    }

    private String esc(String s) {
        if (s == null) return "";
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '"') out.append('\\').append(ch);
            else if (ch == '\n')         out.append("\\n");
            else                         out.append(ch);
        }
        return out.toString();
    }

    private String url(String s) { return s == null ? "" : s.replace(' ', '+'); }

    private String shortText(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    private void closeQuietly(Object o) {
        try {
            if      (o instanceof InputStream)    ((InputStream)    o).close();
            else if (o instanceof OutputStream)   ((OutputStream)   o).close();
            else if (o instanceof HttpConnection) ((HttpConnection) o).close();
        } catch (Exception ignored) {}
    }
}
