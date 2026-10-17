# Validation

Environment: Windows x64, Temurin JDK 21, JavaFX 21.0.12, Maven 3.9.11.
All tests use isolated temporary leagues. The earlier versions' source and ZIPs are unchanged.

## Server and network: 49 checks passed

Real TLS socket clients exercised registration, bad certificate rejection, saved verifier
storage, duplicate-login rejection, approximately 30 snapshots/s, physics progress through
four concurrent login attempts, ready/start, team-change readiness, fixed rosters, player
separation, head-on shielding, wall constraints, stale input, kicks/cooldown, goal-line and
post rules, goals/own goals, ranked disconnect/reconnect, Elo, final readiness reset,
labelled reports, replay downloads, rejected replay paths, chat after a failed request,
balancing, settings bounds/NaN rejection, standard ranked geometry, file reload, seasons,
duplicate-ID protection, tournament roster enforcement, save failure/retry, completed
fixture protection, abandoned results, final replay phase and host shutdown.

Measured snapshot delivery was **29.5–30.0 snapshots/second** in the final iterations,
over a roughly 2.1-second localhost sampling interval per run. This establishes the new
cadence on this computer, not a universal Wi-Fi or input-to-photon latency guarantee.
The independent renderer interpolates snapshots; local prediction is not implemented.

## JavaFX UI: 15 checks passed

Verified all four sound assets load, secure host flow, rapid team-switch readiness,
locked match controls, simultaneous synthetic touch movement/kick and independent release,
alternate keys, input clearing, narrow viewport layout, result readiness reset, rendering
of the league/history/match lab/tournament/replay screens, interpolation, teleport handling
and leave/reset. The visual pass corrected a cramped phone header and exposed a stale
snapshot selection race, which now has explicit control acknowledgements.

Screenshots are desktop JavaFX renders, including the phone-width view. They are not
physical-phone screenshots. Sound assets loaded successfully; subjective sound quality
and speaker output were not assessed by a person.

## Scope not verified

- Native Android/iOS build, signing, installation, multi-touch on real hardware and mobile TLS/audio behavior.
- Two separate computers or phones over a real Wi-Fi access point and firewall configuration.
- Human enjoyment, fairness, accessibility, long-session performance or retention.
- Exhaustive v3 parity; see the comparison document for known omissions.

The separately supplied regression harness is testing material, excluded from the
application line count. The main application archive contains no hidden test implementation.
