# Security Policy

## Supported versions

Koda is developed by a small team and ships as APKs from the [Releases](https://github.com/Ivorisnoob/Koda/releases) page. There are no long-term support branches.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Anything older | No, please update before reporting |

If a fix is needed, it goes into the next release rather than being backported.

## Reporting a vulnerability

**Please do not open a public issue, and do not post it in the Telegram chat.** Both are public, and a working exploit against the session storage would put people's Google accounts at risk before a fix exists.

Report privately through GitHub: go to the [Security tab](https://github.com/Ivorisnoob/Koda/security/advisories) and choose **Report a vulnerability**. This opens a private advisory visible only to the maintainers.

Useful things to include, roughly in order of value:

- What an attacker gains, and what access they need to start (installed malicious app, physical access, a crafted link, a rooted device).
- The Koda version and Android version you tested against.
- Steps to reproduce, or a proof of concept.
- Whether it affects signed-in users, signed-out users, or both.

You will get an initial response within about a week. Because this is a volunteer project, please treat that as a realistic estimate rather than a guarantee. If a report is confirmed, you will be credited in the release notes unless you would rather not be.

## What is in scope

The parts of Koda where a bug has real consequences:

- **Stored session data.** Koda keeps YouTube session cookies in `EncryptedSharedPreferences`. Anything that exposes them, reads them from another app, writes them to logs, or leaks them over the network is the most serious class of issue in this project.
- **Exported components.** `MainActivity` handles shared text and YouTube links from other apps, and both `MusicService` and `VideoPlaybackService` are exported so that media browsers, Android Auto and Assistant can reach them. Anything that lets another installed app extract data or trigger unintended behaviour through these belongs here.
- **Untrusted input parsing.** Shared links, imported subscription files and backup archives, playlists, caption and subtitle files, and stream manifests all originate outside the app. Path traversal, zip-slip, or code execution from any of these is in scope.
- **File writes.** Downloads land in shared storage, and the app holds broad storage permissions. Anything that lets crafted metadata write outside the intended location counts.
- **De-anonymisation of signed-out users.** Koda is usable without an account and that is a deliberate feature, so anything that silently attaches an identity to a signed-out session matters.

## What is not in scope

To save you the time of writing them up:

- **That Koda uses YouTube's internal API at all.** This is how the app works and it is documented. It is a design decision, not a vulnerability.
- **That Koda can download or play YouTube content.** That is the purpose of the app.
- **Missing hardening in the abstract** (no certificate pinning, no root detection, no obfuscation, debuggable builds), without a concrete exploit that the hardening would have prevented.
- **Attacks that require an already-compromised device**, such as a rooted phone with an attacker holding it unlocked, or malware that already has root.
- **Vulnerabilities in YouTube, Google, or other third-party services.** Report those to the vendor. If a YouTube-side change breaks Koda, that is a normal bug and belongs in a public issue.
- **Automated scanner output** with no demonstrated impact.

## A note on where you got the APK

Koda is distributed through GitHub Releases and the Telegram channel. An APK from anywhere else, including any listing claiming to be Koda on an app store, is not something this project publishes or can vouch for. A modified build could ship with the session-handling code changed, and you would have no way to tell from the outside.

If you are unsure, install from [Releases](https://github.com/Ivorisnoob/Koda/releases) and check that the signing certificate matches the build you had previously. Android will refuse to install an update signed with a different key, which is itself a useful signal.
