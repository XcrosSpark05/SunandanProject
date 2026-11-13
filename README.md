# Live Screen Broadcaster (Phone to Web)

A zero-maintenance, zero-authentication, real-time typing broadcaster. Whatever you type on your Android phone appears live on a public web display URL in real time.

---

## 🚀 How It Works

```
[Android App on Phone]  ---(Realtime Stream)--->  [Public Web URL on Vercel]
```

- **Phone (Android App):** Open the app, type in the text box (or use keyboard voice dictation).
- **Public URL (Vercel):** Anyone viewing your link sees your words typed live character-by-character with an animated glowing cursor.
- **Zero Maintenance:** Uses high-availability cloud pub/sub transport with dual WebSockets + SSE fallback. No servers to maintain, no databases to clean, no passwords/API keys to expire.

---

## 📱 1. Android App Setup

1. Open this project folder in **Android Studio**.
2. Connect your Android phone (or launch an Emulator).
3. Press **Run** (`Shift + F10`) to install the app.
4. **Using the App:**
   - As you type in the text area, the text broadcasts instantly to the display URL.
   - Tap the **Share URL** button at the top to send the live viewing link via WhatsApp, email, or copy to clipboard.
   - Tap the **Settings** icon to customize your private room channel name or change your Vercel host URL.

---

## 🌐 2. Web Display Deployment (Vercel)

The `web/` folder is a self-contained static web application that requires **no build step** and **no npm installation**.

### Deploy to Vercel (Free & Instant):

#### Option A: Via GitHub (Recommended)
1. Push this repository to GitHub.
2. Go to [vercel.com](https://vercel.com) and click **"Add New" > "Project"**.
3. Import your GitHub repository.
4. Set the **Root Directory** to `web` (or leave default as the root `vercel.json` already points to `web`).
5. Click **Deploy**. Done! Your URL is live (e.g. `https://your-project.vercel.app`).

#### Option B: Via Vercel CLI
```bash
cd web
npx vercel --prod
```

### Testing Locally in 5 Seconds:
You can test the web display right now without deploying:
- Simply double-click `web/index.html` in your file explorer to open it in Chrome / Edge / Safari!
- Or run:
  ```bash
  cd web
  python -m http.server 3000
  ```
  Open `http://localhost:3000?room=live-demo-room`.

---

## 🎨 Web Display Features (Audience Screen)

- **Live Animated Blinking Cursor:** Gives a smooth live typing aesthetic.
- **Teleprompter / Presentation Controls:**
  - **Fullscreen toggle** (hides toolbar when presenting on big screens/projectors).
  - **Font size scaling** (from 18px to 96px).
  - **Font family cycle** (Modern Sans, Editorial Serif, Clean Monospace).
  - **Dark / Light theme** toggle.
- **Room Code & QR Code Modal:** Displays a QR code directly on screen for audience members to scan.
- **Dual WebSocket + SSE:** Connects via WebSockets with an automatic fallback to Server-Sent Events (SSE) so firewalls never block it.
- **History Retrieval:** When a viewer opens the page, it instantly loads the current text state without needing to wait for a new keystroke.

---

## 🔒 Privacy & Rooms

Every session has a **Room ID** (default: `live-demo-room`). 
- Change your room ID in the Android app settings (e.g., `sunandan-priv-982`).
- The audience URL will automatically be `https://your-project.vercel.app?room=sunandan-priv-982`.
- No one else can view or interfere with your text without knowing your room channel.

