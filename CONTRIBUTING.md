# Contributing to Koda

First off, thank you for considering contributing to **Koda**! It's people like you that make the open-source community such an amazing place to learn, inspire, and create.

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
- Fork the repository and create your branch from `main`.
- If you've added code that should be tested, add tests.
- Ensure the project builds successfully with `./gradlew assembleDebug`.
- Follow the existing code style (Kotlin coding conventions).
- Open a PR with a clear title and description.

#### PR Troubleshooting (Codex)
- If you see: **"Codex does not currently support updating PRs that are updated outside of Codex"**, create a **new PR** instead of trying to update the existing one.
- Recommended flow:
  1. Create a fresh branch from the latest `main`.
  2. Cherry-pick or re-apply your commits.
  3. Open a new PR with a short note linking the old PR for context.

## Development Setup

1. **Clone the repo**: `git clone https://github.com/Ivorisnoob/Koda.git`
2. **Open in Android Studio**: Use the latest stable version of Android Studio (Ladybug or newer).
3. **Sync Gradle**: Let the dependencies download.
4. **Run**: You're ready to start building!

## Code of Conduct
This project and everyone participating in it is governed by the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). By participating, you are expected to uphold this code.

## License
By contributing, you agree that your contributions will be licensed under the project's **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)** license.
