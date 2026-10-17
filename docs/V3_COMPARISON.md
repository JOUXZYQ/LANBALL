# V3 comparison — no blanket parity claim

The 1,000-line request conflicts with reproducing every behavior in v3's substantially
larger implementation while keeping the code readable. This release repairs the seven
reported v4 defects and restores the main feature families. These remaining differences
are explicit; v3 remains available unchanged.

| V3 area | V4.1 status |
|---|---|
| Desktop host/join and LAN discovery | Implemented; discovery finds the first responding host; direct IP remains available |
| Mobile input | Shared JavaFX touch client; Android/iOS native builds and device tests still outstanding |
| Lobby, 1v1–3v3, spectators, ready, rematch | Implemented; settings changes return players to spectators |
| Practice | Implemented as an isolated temporary host; choose a team and Start |
| Simulation, contacts, goals, posts, kick cooldown | Implemented compactly, with body blocking and 60 Hz simulation; not bit-for-bit v3 physics |
| Ranked/casual | Implemented, with standard geometry for ranked |
| Disconnect/reconnect | Ranked pause/30-second abandonment; casual continuation; client makes up to three reconnect attempts |
| Host closure | Active non-practice match saved as abandoned; no host migration |
| Authentication | Replaced with TLS 1.3 certificate pinning and encrypted PIN login; separate v4.1 profiles |
| Statistics | Goals, assists, own goals, touches, kicks, presence and before/after Elo in history |
| League and seasons | Names, persistent records, Elo, points, season/player filters; season selection is typed rather than a full season manager |
| Player profiles | Registration/club/shirt, filtered totals/history, rating chart and comparisons; no profile rename/editor |
| Head-to-head | Implemented over the unfiltered history supplied to the view |
| Team balancing | Implemented by checking equal-size splits of up to six players |
| Replays | Recorded snapshots with final phase, host download, seek, play/pause and next-goal; no full replay library/cancel/progress UI |
| Pitch editor/library | Named dimensions, validation, live match preview and overwrite confirmation; separate pitch theme choices and full unsaved-change workflow missing |
| Tournament | Four named teams, rosters, six pairings, roster enforcement, saved fixture results and points/GD/GF table; one current tournament instead of v3's multi-tournament archive |
| Chat | Implemented; recent eight messages are shown, not a persistent chat archive |
| Sounds/settings | Four effects, volume/mute, reduced motion, interpolation toggle, text size, high contrast and primary key rebinding; no binding-conflict analyzer |
| Offline host data | Last league report cached and marked offline; no per-host league/replay library |
| File resilience | Per-file validation/reporting, preserved bad files, atomic replacement, backups, duplicate-ID rejection, save/retry |
| Legacy v1/v2/v3 data migration | Not implemented; existing data is untouched and the earlier app remains the way to open it |
| Diagnostics/accessibility | Basic labels, keyboard/touch support and readable status; not full v3 diagnostics or assistive-technology parity |

If exact v3 parity is mandatory, the source cap must be relaxed or some requirements must
be prioritized. This package does not claim that restoring a screen also restores all of
v3's edge cases or platform guarantees.
