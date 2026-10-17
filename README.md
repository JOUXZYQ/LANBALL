# LanBall 4.1 — After Hours

A separate revision of v4. Previous v3 and v4 packages are unchanged.

**995 physical lines of Java across six source files**, including imports, comments and
blank lines. The two Windows scripts add five lines: **1,000 application/launcher lines
in total**. Maven XML and iOS permission metadata are additional build configuration;
third-party JavaFX implementation, WAV audio assets, documentation and the separate test
harness are excluded. There is no HTML/JavaScript client, FXML, external stylesheet or
hidden generated game code. The application uses JavaFX controls and media libraries.

## Source checkout

This Git repository contains source, documentation and regression tests. Install JDK 21
and Maven, then run `build.bat` to create the JAR and download JavaFX into `lib`.
After the build, run `run.bat`. The prebuilt ZIP instructions below apply to release packages.

## Run

Extract the whole folder and open **`run.bat`**. Use **JDK 21** on Windows x64 (tested).
Hosting invokes the JDK's `keytool` once to create a local TLS identity, so a JRE alone
is insufficient for hosting. JavaFX libraries and the rebuilt JAR are included.

1. Fill in name, PIN, club and shirt; select Create profile for a new account.
2. Choose Host Match. The first launch creates a certificate and may take a few seconds.
3. Give friends the displayed host IP and SHA-256 fingerprint through a trusted channel.
   The host's Fingerprint field is also populated, so it can be selected and copied.
4. Friends on the same LAN enter that IP, paste the verified fingerprint, create/sign in
   with their own profile, and choose Join Match. Allow private-network TCP port 45888.
5. Choose teams, mark Ready, and let the host press Start. Match setup selects team size,
   time limit, target score, ranked mode and pitch. Applying settings clears team/readiness.
6. WASD/arrows move; Space/X kicks; Enter focuses chat. Touch buttons support movement
   plus kick together. Escape opens/closes the clubhouse. The clubhouse collapses during
   play to give the pitch and touch controls room; tap its title to reopen it.
7. The host can balance teams by Elo, start a rematch or abandon a round. Leaving a host
   requires confirmation and ends the session. Complete rounds are recorded automatically.

Practice creates an isolated temporary host and a practice profile. Choose a side and
press Start; practice does not create league match results. Stop it before hosting a league.

## What changed

| Reported issue | Repair |
|---|---|
| About 10 snapshots/s; choppy motion | Independent input and snapshot streams; 60 Hz simulation; 30 Hz paced snapshot writers; an independent JavaFX render loop interpolates positions |
| PIN hashing freezes physics | Profile validation and PBKDF2 run before acquiring the simulation lock; file writes run outside physics |
| A damaged file prevents hosting | Validate each file completely, skip/report damaged files, preserve their bytes, reject duplicate match IDs |
| PIN readable on Wi-Fi | TLS 1.3 and mandatory SHA-256 certificate pinning before credentials are sent; mismatches fail closed |
| Team changes retain Ready | Ready is an explicit command, cleared on team changes; control acknowledgements prevent stale snapshots undoing UI selections |
| Raw Java arrays in standings | Tables with named columns, player/season filters, history, Elo charts and head-to-head comparison |
| Players pass through each other | Iterative body separation, collision impulses and wall constraints; shielding/blocking now work |

The presentation adds a dark stadium, striped turf, coloured goals, numbered players,
scoreboard, goal confetti, kick/wall/whistle/goal audio, volume/mute, reduced motion,
larger text and high contrast. Audio is synthesized WAV content loaded with JavaFX.

## Restored features and remaining gaps

Restored in compact form: practice; host/join/discovery; 1v1–3v3 and spectators;
ready/countdown/goal pause/rematch; ranked disconnect pause and reconnect attempts;
abandoned records; assists/own goals/touches/kicks; Elo balancing; league and season
names; filtered tables; match reports; player rating charts; head-to-head; chat;
host-served replays with seek/play/pause/next-goal; named pitch dimensions; four-team
round-robin fixtures with roster checks and points tables; key bindings; sound and
display preferences; a cached offline league report; help.

**This is not complete v3 feature parity.** Full old-format import, v3's multi-tournament
archive, separate pitch visual themes, per-host offline libraries and some richer report
and lifecycle controls are not restored. See [the explicit comparison](docs/V3_COMPARISON.md).
The 1,000-line cap has been preserved rather than concealing more code in generated files.

## Records and identity

The host uses **`lanball-v41-data` beside the launcher**. It does not read or modify v3/v4
data folders. Keep these together when backing up with the host closed:

- `player-*.profile`: name, club, shirt, salt and PBKDF2 PIN verifier. No plaintext PIN.
- `*.match`: immutable completed/abandoned match records, participants, stats, ratings,
  scores, season, pitch dimensions and fixture identity.
- `*.replay`: recorded snapshots, including the final match phase and score.
- `league.properties`: league/season names, named pitches and the current tournament.
- `host.p12`: private host identity; `fingerprint.txt`: its public fingerprint.
- `*.bak`: previous versions of replaceable files; `.pending`: unfinished writes.

Local preferences and the most recently cached league report live in
`~/LanBall/v41-settings.properties` and `~/LanBall/league-cache.txt`. PINs are not saved
in client preferences. Protect the host identity file and verify its fingerprint before
joining. TLS confidentiality does not make a short PIN resistant to offline guessing if
someone obtains the profile files.

Match totals are rebuilt from valid match files. The host lists bad filenames after login
and leaves them unchanged. A failed result write pauses progression; Start retries it.
Abandoned matches are retained but do not count towards completed standings or Elo.
Only completed ranked rounds with everyone present change Elo. A ranked disconnection
pauses up to 30 seconds; a returning player resumes via kickoff. Casual rounds continue.

## Tournament and reports

Create four lines such as `Team A:Asha,Binu` using already registered players. Reopen
Tournament after creation, choose a fixture, put the listed players on the corresponding
Red/Blue sides, mark ready and start. Completed fixtures cannot be replayed; abandoned
ones can. Points are win 3/draw 1, sorted by points, goal difference, then goals scored.
Choose Ordinary match to clear the fixture. There is one actively managed tournament.

League queries use an optional exact player and season name. History without a player
filter contains all participants, which is needed for head-to-head comparisons. Select
a history row for that player's rating chart or the match's replay. Replays and reports
come from the connected host. Offline League displays the last cached report, marked offline.

## Mobile status

The shared JavaFX client includes touch controls, responsive layout, Android/iOS Maven
profiles and the iOS local-network permission. **No APK or signed iPhone app has been
built or tested here.** Phone installation requires a native build; a phone browser
cannot join. Android builds require the supported Linux/WSL toolchain; iPhone builds need
macOS/Xcode and signing. See [mobile build instructions](docs/MOBILE.md).

## Build and verification

Use Maven and JDK 21: `build.bat`, or `mvn package dependency:copy-dependencies -DoutputDirectory=lib`.
Desktop execution from source: `mvn javafx:run`. Other desktop platforms must resolve their
own JavaFX native libraries with Maven; the bundled `lib` directory targets Windows x64.

- [Validation](docs/VALIDATION.md): measurements, checks and limits.
- [Engagement design](docs/ENGAGEMENT.md): research-informed design choices, not a claim of proven addictiveness.
- [Source count](docs/LINE_COUNT.md): every delivered application source file.

The original assessment flow remains: accept profile/game/pitch/tournament input, store
records permanently, and process them into results, standings, ratings and comparisons.

