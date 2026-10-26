# LanBall v9

26 October gameplay update: faster input delivery, reliable quick kick taps, smoother
phone ball movement, and a quieter desktop toolbar. **Hide room** expands the pitch.
See `docs/SMOOTH-GAMEPLAY-REVIEW.txt` for changes, measurements and test limitations.

2D top-down LAN football for 1v1 up to 3v3. One laptop hosts; other laptops join with the
desktop client, and phones (iPhone or Android) join from Safari or Chrome. Every player,
on any device, uses the same Java match rules on the host.

## Requirements

- Java 17 or newer on every laptop (`java -version`). Phones need only a browser.
- All devices on the same network (Wi-Fi or the host laptop's hotspot).
- No internet and no extra libraries are needed.

## Start

| What | Windows | macOS / Linux |
|---|---|---|
| Play | `run.bat` (or double-click `LanBall-v9.jar`) | `sh run.sh` |
| Rehearse on one laptop (two windows) | `demo.bat` | `sh demo.sh` |
| Rebuild the JAR (JDK 17+) | `build.bat` | `sh build.sh` |
| Run all tests | `test.bat` (`test.bat deep` adds the long physics search) | - |

## Host a match (laptop)

1. Enter a player name and password. Keep **Create account** ticked the first time (untick it to sign in later), then click **Host**.
2. The window shows the host IP. Other laptops type that IP and click **Join**.
3. Each player picks **Red**, **Blue** or **Watch** and ticks **Ready**.
4. The host sets seconds (30-600) and first-to goals (1-20) and clicks **Start match**.

Controls: **WASD** or arrow keys to move, **Space** to kick.

## Join from a phone

1. On the host laptop click **Phones**. It lists the address, normally `http://HOST_IP:45890/`.
2. Put the phone on the same Wi-Fi as the laptop, open Safari or Chrome, type that address.
3. Sign in (or create an account), choose a team, tick Ready, close the menu.
4. Turn the phone to landscape. Left thumb: move stick. Right thumb: tap or hold **KICK**.

If the phone cannot open the page:

- Windows may ask to allow Java through the firewall. Allow it on **Private** networks only.
  The laptop's network must be set to Private, not Public.
- Campus or guest Wi-Fi often blocks device-to-device traffic. Turn on the laptop's
  **Mobile hotspot** (Windows Settings > Network > Mobile hotspot), join it from the phone and
  the other laptops, then click **Phones** again: the hotspot address is usually `192.168.137.1`.

## Other features

- **Chat, League, History, Profile** tabs on the right of the laptop window.
- **Elo + seasons:** everyone starts at 1000 Elo (K = 24). The host's **Season** button starts a
  new season; old seasons stay saved.
- **Match summary:** goals and assists for each player after every match.
- **Replay:** pick a match in **History** and click **Replay** (**Summary** shows its goals and assists).
- **Pitch editor:** host clicks **Pitch editor**; click to add a bumper, right-click to remove,
  **Save .pitch**, then **Use pitch**. All players receive it.

## Data and ports

| Item | Normal | Demo (`demo.bat`) |
|---|---|---|
| Data folder (accounts, matches, replays, pitches) | `lanball-data\` next to where you start it | `demo-data\` |
| Laptop TCP port | 45888 | 45889 |
| Phone web port | 45890 | 45891 |

Delete `lanball-data` to reset everything. Only one host may use a data folder at a time.

Demo accounts (demo mode only): `DemoRed` and `DemoBlue`, password `demo123`.

## Security limits (read this)

This is a classroom project for a trusted local network.

- Passwords are stored on the host as PBKDF2-SHA256 verifiers (120,000 rounds), not as text.
- Laptops log in with a challenge-response proof, but there is **no TLS**.
- Phones send the password to the host over plain local HTTP.
- **Use demo passwords. Do not reuse a real password.** Do not expose the ports to the internet.

## Project layout

```
LanBall-v9.jar        runnable game (Main-Class lanball.LanBall)
src/lanball/          16 production classes
resources/mobile/     phone page: index.html, style.css, game.js (packed into the JAR)
tests/lanball/        Java tests (run by test.bat)
tests/browser/        optional Playwright phone test (needs Node + Playwright)
screenshots/          real captures from the automated test runs
docs/                 TALK.md (10-min talk for 5 speakers), DEMO-AND-QA.md
```

| Class | Job |
|---|---|
| `Circle` | position, velocity, radius; parent of `Player` and `Ball` |
| `Player`, `Ball` | one footballer / the ball |
| `Match` | rules and physics only: no sockets, Swing or files |
| `Server` | authoritative host: owns the match, accounts, league, replays |
| `MobileServer` | serves the phone page and turns phone input into the same `Player` input |
| `LanBall` | laptop window (Swing) |
| `Pitch` | draws a snapshot; it never decides goals |
| `Accounts`, `League`, `Replay`, `Arena`, `Files` | storage: accounts, Elo/history, replays, pitches, safe file writes |
| `PitchEditor`, `ReplayWindow`, `Demo` | editor, replay viewer, rehearsal launcher |

### Optional browser test

Start the test host, then run the script (Chrome, or `LB_ENGINE=webkit` for the Safari engine):

```
java -cp "LanBall-v9.jar;<test classes>" lanball.BrowserHost browser-data
node tests/browser/browser-test.cjs
```
