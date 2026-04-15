# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial project scaffold: Gradle build, plugin descriptor, default config
- Per-category confidence thresholds (`layer1.category-thresholds`) for Layer 1 moderation API (#2)
- Per-category score weights (`layer1.category-weights`) for differentiated scoring by category (#2)
- Custom command execution on violations — per-threshold `commands` list and per-category `layer1.category-commands` map with placeholder support (#3)
- Placeholder tokens for custom commands: `%player%`, `%uuid%`, `%score%`, `%score_before%`, `%category%`, `%moderation_score%`, `%source%`, `%action%`, `%duration%`, `%layer%` (#3)
