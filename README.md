# MessengerForKeypad

Minimal messaging bridge for Java keypad phones (J2ME + Telegram bridge backend).

## Where to store IDs and secrets

Use an `.env` file inside `backend/` for all run-time details.

- `TELEGRAM_BOT_TOKEN` → your bot token from BotFather.
- `ALIAS_MAP` → alias to Telegram chat-id mapping (example: `alice:123456789,bob:987654321`).
- `PORT` (optional) → backend port.

A template is provided in `backend/.env.example`.

## Do we need a virtual environment?

- For **Node.js backend**: no Python virtual environment is needed.
- You only need Node.js + npm.
- Install dependencies with `npm install` in `backend/`.

## Project layout

- `midlet/src/KeypadMessengerMidlet.java`: J2ME app for send/poll messaging.
- `backend/server.js`: minimal bridge API and Telegram forwarding.
- `backend/.env.example`: environment variable template.

## Flow

1. MIDlet sends `POST /send` with `deviceId`, `to`, `text`.
2. Backend maps `to` alias to Telegram `chat_id` from `ALIAS_MAP`.
3. Telegram replies come to `/telegram/webhook`.
4. MIDlet polls `GET /inbox?deviceId=...&since=...`.

## Backend quick start

```bash
cd backend
cp .env.example .env
# edit .env with your real token and alias map
npm install
set -a; source .env; set +a
npm start
```

## MIDlet build

Compile with your Java ME toolchain (WTK/Ant/NetBeans Mobility).
This repository includes a starting source file only.
