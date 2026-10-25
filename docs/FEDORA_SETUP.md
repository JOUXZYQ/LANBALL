# LanBall on Fedora — Prerequisites and Setup

Use this on the Fedora laptop that runs LanBall at the review. Do one full test run the night before.

## 1. Install Java (the only real requirement)

```bash
sudo dnf install java-21-openjdk
```

- Install `java-21-openjdk`, **not** `java-21-openjdk-headless`. The headless package has no Swing, so the game window cannot open.
- To rebuild from source you also need the compiler:

```bash
sudo dnf install java-21-openjdk-devel
```

- Check the install:

```bash
java -version
```

Any version from Java 8 upwards works.

## 2. Copy these files to the laptop

| File / folder | Needed for |
|---|---|
| `LanBall-v7.jar` | playing (required) |
| `run.sh` | starting the game (required) |
| `src/` | showing or rebuilding the code |
| `build.sh` | rebuilding the JAR |
| `docs/` | slides content, screenshots, logo |

## 3. Start the game

```bash
sh run.sh
```

- `run.sh` starts the game from its own folder. The save folder `lanball-v4-data` (profiles and matches) is created next to the JAR.
- Use `sh run.sh`, not `./run.sh`. A USB stick often removes the execute permission.
- To rebuild after a code change:

```bash
sh build.sh
```

## 4. Firewall (host laptop only)

Fedora Workstation normally allows ports 1025–65535 already. To check:

```bash
sudo firewall-cmd --list-all
```

If friends cannot join, open the game port until the next reboot:

```bash
sudo firewall-cmd --add-port=45888/tcp
```

## 5. Network — the biggest risk

- College Wi-Fi often blocks devices from talking to each other (client isolation). The game then works on one laptop, but nobody can join.
- **Backup plan:** turn on a phone hotspot and connect every laptop to it.
- The host's screen shows the address to join, for example `192.168.43.20 (port 45888)`.
- Test this on the real network before the review.

## 6. Things that can look different on Fedora (no action needed)

| Item | What happens |
|---|---|
| Fonts | Segoe UI is a Windows font, so Java uses Fedora's default font. Text looks slightly different but stays readable. The league table uses Java's built-in monospaced font, so its columns line up. |
| Sound | Plays through PipeWire. If sound fails, the game plays silently and does not crash. |
| Wayland | Swing runs through XWayland, which Fedora has by default. |
| High-resolution screen | If the window looks tiny, start it with the command below. |

```bash
java -Dsun.java2d.uiScale=2 -jar LanBall-v7.jar
```

## 7. Review-day checklist

- [ ] `java -version` works
- [ ] `sh run.sh` opens the window
- [ ] HOST works and the screen shows an IP address
- [ ] A second laptop (or phone hotspot setup) can JOIN
- [ ] READY → START MATCH → a goal → the result screen appears
- [ ] Sound plays (or the Sound box is unticked)
- [ ] Laptop is charged; the hotspot phone is charged

## 8. If only one laptop is at the review

The bot players used in testing are a test tool, not part of the game. With one laptop, the demo can show the title screen and the lobby, but not a full match. Ask for `demo-bots.jar` if a one-laptop 2v2 demo is needed.
