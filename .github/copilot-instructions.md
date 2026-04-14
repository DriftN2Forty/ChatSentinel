Never wrap or break long lines of code. Keep statements on a single line regardless of length.

The README.md is the source of truth for project structure and intended functionality. It must be kept up to date at all times.

Maintain a CHANGELOG.md following the Keep a Changelog format (https://keepachangelog.com/en/1.1.0/). Record changes under the appropriate category: Added, Changed, Deprecated, Removed, Fixed, Security.

Target Java 21. Use records, sealed interfaces, and pattern matching where appropriate. Mark fields and local variables `final` when they don't change. No wildcard imports. No unused imports.

Package namespace: io.github.driftn2forty.chatsentinel

All shaded dependencies must be relocated under io.github.driftn2forty.chatsentinel.lib.* to avoid classpath conflicts.

All API calls and database I/O must run off the main server thread (async). Never block the main tick loop.

Database schema is frozen. Player and moderation data use the UUID + JSON blob pattern. To add new fields, add them to the Java class with a default — Gson handles missing fields automatically. Never write ALTER TABLE or migration scripts.

Every new feature or bug fix must include unit tests. Use JUnit 5 and MockBukkit for event-driven tests. The build must pass with zero warnings.

Commit messages use imperative mood: "Add score decay", "Fix NPE in history tracker". No WIP or misc commits.

When adding a new dependency, add it to the Dependencies table in README.md and ensure it is shaded + relocated if it ships in the plugin jar.
