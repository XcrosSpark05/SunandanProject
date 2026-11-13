# 🏏 Cricket Live Scoring & OBS Overlay System

A professional, zero-maintenance, real-time cricket scoring app with a **broadcast-quality transparent OBS lower-third overlay ticker** (styled in the TennisCricket.in broadcast standard).

---

## 📺 How It Works

```
[Android Phone Scorer App]  ───(Live Keystrokes/Balls <30ms)───>  [OBS Studio Browser Source]
```

1. **Scorer on Phone:** Taps runs, boundaries, wickets, and extras.
2. **OBS Overlay:** Instantly updates the broadcast score strip live on YouTube / Facebook / Twitch video streams with zero latency.
3. **Zero Maintenance:** No servers to host, no database accounts, no billing, no passwords to expire.

---

## 🎥 Adding to OBS Studio (1920x1080)

1. In OBS Studio, go to the **Sources** dock and click **`+`** (Add Source).
2. Choose **Browser** and name it `Cricket Overlay`.
3. In the settings:
   - **URL:** `https://your-project.vercel.app/?room=cricket-live-1` (or your chosen room ID)
   - **Width:** `1920`
   - **Height:** `1080`
   - **Custom CSS:** (Leave empty, the overlay already has a transparent background)
   - Check **"Shutdown source when not visible"**
   - Check **"Refresh browser when scene becomes active"**
4. Click **OK**. The lower-third banner will now sit perfectly at the bottom of your live video stream!

> 💡 **Previewing before stream:** You can open `web/preview.html` in your browser to simulate the overlay over a sample cricket video canvas.

---

## 📱 Android Scorer Features

- **Match & Team Setup (👥 Icon):**
  - Register Batting Team & Bowling Team names.
  - Set total overs (e.g., 6, 8, 10, 20 overs).
  - Set toss decision (e.g., *"Shrutesh 11 decided to bowl"*).
- **Live Striker & Non-Striker Management:**
  - One-tap **"Swap Strike"** button.
  - Auto-swaps striker on 1s, 3s, and at the end of each over.
  - Tap any batsman or bowler to substitute or add a new player from the squad.
- **Big Touch Scoring Keypad:**
  - Dots (`0`), Singles (`1`), Twos (`2`), Threes (`3`).
  - **FOUR (4)** and **SIX (6)** buttons (triggers floating animated broadcast popup on stream!).
  - **WIDE** and **NO BALL** buttons.
  - **OUT / WICKET** button (triggers wicket banner).
- **Full UNDO Support:**
  - Misclicked a button? Tap **UNDO** to instantly revert the ball and update the stream.
- **Auto-Calculations:**
  - Batsman runs, balls, 4s, and 6s.
  - Bowler overs, runs conceded, and wickets.
  - Current Run Rate (CRR) & Projected Score.
  - Current over ball-by-ball bubbles (`[•] [6] [•] [WD] [•] [•] [6]`).

---

## 🚀 Deploying to Vercel in 1 Minute

1. Push this repository to GitHub.
2. Go to [vercel.com](https://vercel.com) and click **"Add New Project"**.
3. Select this repository. The included `vercel.json` automatically configures the `web/` folder.
4. Click **Deploy**.
5. Copy your Vercel deployment URL (e.g. `https://my-cricket.vercel.app`) and paste it into the Android App settings.
