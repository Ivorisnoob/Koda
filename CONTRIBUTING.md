# Contributing to Koda

Thank you for your interest in **Koda**.

> **Pull requests are open again, and they go to the `beta` branch, not `main`.** `beta` is where new work lands and what beta testers get builds from; `main` only moves when a tested `beta` is released. See [Pull Requests](#3-pull-requests) below.

## How Can I Contribute?

### 1. Reporting Bugs
- Before creating a bug report, please check that an issue hasn't already been reported.
- When creating a bug report, please include as many details as possible:
    - Steps to reproduce the bug.
    - Expected vs. actual behavior.
    - Device and Android version.
    - Screenshots or screen recordings (if applicable).

### 2. Suggesting Enhancements
- If you have an idea for a new feature or an improvement, please open an issue to discuss it first.
- Describe the feature, why it would be useful, and how it might work.

### 3. Pull Requests

1. **Branch from `beta` and open the pull request against `beta`.** GitHub picks `main` as the base by default, so change it in the "base" dropdown when you open the PR. A pull request opened against `main` will be retargeted before it is reviewed.
2. **For anything bigger than a small fix, open an issue first** so the approach can be agreed before you spend time on it. [ROADMAP.md](ROADMAP.md) lists what is planned and what is deliberately out of scope.
3. **Read [CLAUDE.md](CLAUDE.md) before you write code.** It is short, and it holds the rules that are bugs to break even when the build passes (no hardcoded colors, how streams are fetched, how a new setting is wired). The reasoning for each area is in [docs/](docs/), and UI work follows [DESIGN.md](DESIGN.md).
4. **Keep a pull request to one topic** and fill in the template, including screenshots or a recording for anything visible.
5. **If someone using the app would notice your change, end the commit message with a `Changelog:` section**: one `- ` bullet per change, each on a single line, in plain words. Those bullets are sent to beta testers with the build, so write them for a person using the app.

```text
Fix the queue jumping back to the top after a reorder

Changelog:
- Fixed the queue scrolling back to the top after you move a song.
```

A pull request from a fork does not get a CI build on its own; that is deliberate, because building it means running the fork's Gradle build with the project's runners. A maintainer builds it when reviewing. Once it is merged into `beta`, a signed build goes to the beta testers in the [Telegram chat](https://t.me/ivorisnoob_chat).

## Development Setup

1. **Clone the repo**: `git clone https://github.com/Ivorisnoob/Koda.git`
2. **Open in Android Studio**: Use the latest stable version of Android Studio (Ladybug or newer).
3. **Sync Gradle**: Let the dependencies download.
4. **Run**: You're ready to start building!

## Code of Conduct
This project and everyone participating in it is governed by the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). By participating, you are expected to uphold this code.

## License
By contributing, you agree that your contributions will be licensed under the project's **GNU General Public License v3.0 (GPL-3.0)**, the same license as the rest of Koda. See [LICENSE](LICENSE) for the full text.
