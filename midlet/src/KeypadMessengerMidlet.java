import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/**
 * Minimal J2ME MIDlet for text messaging through a bridge server.
 */
public class KeypadMessengerMidlet extends MIDlet implements CommandListener {
    private Display display;

    private Form configForm;
    private TextField baseUrlField;
    private TextField deviceIdField;

    private Form sendForm;
    private TextField toField;
    private TextField messageField;

    private Form inboxForm;

    private Command cmdSave = new Command("Save", Command.OK, 1);
    private Command cmdNext = new Command("Next", Command.OK, 1);
    private Command cmdBack = new Command("Back", Command.BACK, 1);
    private Command cmdSend = new Command("Send", Command.OK, 1);
    private Command cmdPoll = new Command("Poll", Command.SCREEN, 2);
    private Command cmdInbox = new Command("Inbox", Command.SCREEN, 2);

    private String baseUrl = "http://YOUR_SERVER:3000";
    private String deviceId = "KP1001";
    private long since = 0;

    public void startApp() {
        display = Display.getDisplay(this);
        showConfig();
    }

    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}

    private void showConfig() {
        configForm = new Form("Config");
        baseUrlField = new TextField("Server URL", baseUrl, 128, TextField.URL);
        deviceIdField = new TextField("Device ID", deviceId, 32, TextField.ANY);
        configForm.append(baseUrlField);
        configForm.append(deviceIdField);
        configForm.addCommand(cmdSave);
        configForm.addCommand(cmdNext);
        configForm.setCommandListener(this);
        display.setCurrent(configForm);
    }

    private void showSend() {
        sendForm = new Form("Send Message");
        toField = new TextField("To(alias)", "", 32, TextField.ANY);
        messageField = new TextField("Text", "", 300, TextField.ANY);
        sendForm.append(toField);
        sendForm.append(messageField);
        sendForm.addCommand(cmdSend);
        sendForm.addCommand(cmdPoll);
        sendForm.addCommand(cmdInbox);
        sendForm.setCommandListener(this);
        display.setCurrent(sendForm);
    }

    private void showInbox(String messages) {
        inboxForm = new Form("Inbox");
        inboxForm.append(messages.length() == 0 ? "No messages" : messages);
        inboxForm.addCommand(cmdBack);
        inboxForm.addCommand(cmdPoll);
        inboxForm.setCommandListener(this);
        display.setCurrent(inboxForm);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdSave) {
            baseUrl = baseUrlField.getString();
            deviceId = deviceIdField.getString();
            alert("Saved");
        } else if (c == cmdNext) {
            baseUrl = baseUrlField.getString();
            deviceId = deviceIdField.getString();
            showSend();
        } else if (c == cmdSend) {
            String to = toField.getString();
            String text = messageField.getString();
            String body = "{\"deviceId\":\"" + esc(deviceId) + "\",\"to\":\"" + esc(to) + "\",\"text\":\"" + esc(text) + "\"}";
            String resp = httpPost(baseUrl + "/send", body);
            alert("Send: " + shortText(resp));
            messageField.setString("");
        } else if (c == cmdPoll) {
            String resp = httpGet(baseUrl + "/inbox?deviceId=" + url(deviceId) + "&since=" + since);
            // Minimal: display raw JSON, parse "serverTime" roughly.
            int idx = resp.indexOf("\"serverTime\":");
            if (idx >= 0) {
                int start = idx + 13;
                int end = start;
                while (end < resp.length() && Character.isDigit(resp.charAt(end))) end++;
                try { since = Long.parseLong(resp.substring(start, end)); } catch (Exception ignored) {}
            }
            showInbox(shortText(resp));
        } else if (c == cmdInbox) {
            showInbox("");
        } else if (c == cmdBack) {
            showSend();
        }
    }

    private void alert(String text) {
        Alert a = new Alert("Info", text, null, AlertType.INFO);
        a.setTimeout(1800);
        display.setCurrent(a, sendForm != null ? sendForm : configForm);
    }

    private String httpPost(String endpoint, String payload) {
        HttpConnection conn = null;
        OutputStream os = null;
        InputStream is = null;
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
        InputStream is = null;
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
            else if (ch == '\n') out.append("\\n");
            else out.append(ch);
        }
        return out.toString();
    }

    private String url(String s) {
        return s == null ? "" : s.replace(' ', '+');
    }

    private String shortText(String s) {
        if (s == null) return "";
        return s.length() > 700 ? s.substring(0, 700) + "..." : s;
    }

    private void closeQuietly(Object o) {
        try {
            if (o instanceof InputStream) ((InputStream) o).close();
            else if (o instanceof OutputStream) ((OutputStream) o).close();
            else if (o instanceof HttpConnection) ((HttpConnection) o).close();
        } catch (Exception ignored) {}
    }
}
