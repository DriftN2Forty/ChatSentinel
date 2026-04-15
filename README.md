# ChatSentry

A multi-layered chat moderation plugin for Minecraft Paper servers. ChatSentry intercepts player chat messages and runs them through a three-layer moderation pipeline — local trie filter, moderation API, and LLM deep review — to catch toxic, harmful, or rule-breaking content in real time.

**GitHub:** [https://github.com/DriftN2Forty/ChatSentry](https://github.com/DriftN2Forty/ChatSentry)

---

## How It Works

```
Player Message
      │
      ▼
┌─────────────┐
│   Layer 0   │  Local trie filter (zero latency, no API call)
│   Instant   │  Dictionary tree loaded with all 28 LDNOOBW languages.
│             │  Expands known abbreviations (stfu, fml, etc.),
│             │  normalizes leet-speak, then single-pass trie scan.
└─────┬───────┘
      │
      │  Blocked ────────────────────► Action taken immediately
      │
      ▼  Clean
┌─────────────┐
│   Layer 1   │  OpenAI Moderation API (fast, cheap, high-throughput)
│   Gateway   │  Flags categories: hate, harassment, self-harm,
│             │  sexual, violence, etc.
└─────┬───────┘
      │
      │  Clean ──────────────────────► Message allowed through
      │
      ▼  Flagged / Uncertain
┌─────────────┐
│   Layer 2   │  LLM deep analysis (GPT-4o / configurable model)
│   Review    │  Context-aware judgment with server-specific rules,
│             │  prior history, and nuanced understanding
└─────┬───────┘
      │
      ├── Allow ─────────────────────► Message allowed through
      ├── Warn ──────────────────────► Message allowed + player warned
      ├── Mute ──────────────────────► Message blocked + temp mute
      └── Escalate ──────────────────► Alert staff / log for review
```

### Message Handling: Block vs Mask

When a message is flagged, ChatSentry can either **block** it entirely (the sender sees an error, nobody else sees anything) or **mask** offensive portions (replace matched words with `***` and deliver the censored message). Controlled by `pipeline.message-mode` in config.

- **`block`** (default) — Message is cancelled. Only the sender sees a warning. Simplest and safest — no risk of partial leaks.
- **`mask`** — Offensive tokens are replaced with asterisks (`f***`) and the modified message is delivered. Useful for lighter moderation, but can produce awkward output when multiple words are masked.

Layer 0 (trie) drives masking because it identifies exact character spans. Layers 1–2 operate on the full message and cannot identify individual token positions. If `message-mode: mask` is configured but the flagging layer is Layer 1 or 2, the message **falls back to block** since there are no character spans to mask. Only Layer 0 catches produce masked output.

### Beyond Chat: Signs, Books & Anvils

Players routinely bypass chat filters by writing offensive content on **signs**, in **books**, and through **anvil renames**. ChatSentry intercepts all three. Players with the `chatsentry.bypass` permission skip all moderation — including signs, books, and anvils.

| Surface | Event | How it works |
|---|---|---|
| Signs | `SignChangeEvent` | All four sign lines are concatenated and run through Layer 0. Flagged signs are blanked and the player is warned. API layers are not invoked (signs are local, low-risk). |
| Books | `PlayerEditBookEvent` | On book sign (finalize), all pages are concatenated and run through the full pipeline (Layer 0 → 1 → 2). Flagged books are reverted to the previous version. |
| Anvil renames | `InventoryClickEvent` | When a player takes an item from an anvil result slot, the custom name is checked against Layer 0. Flagged renames are stripped. |

Signs and anvil renames only use Layer 0 to avoid API costs for high-volume, low-risk surfaces. Books use the full pipeline because they can contain lengthy, nuanced content.

### Whisper Moderation

Whispers (`/msg`, `/tell`, `/w`) go through the **full pipeline** (Layer 0 → 1 → 2), the same as regular chat. Private messages are a common vector for targeted harassment and abuse — they deserve equal scrutiny. Flagged whispers are blocked (or masked per `pipeline.message-mode` config, with the same Layer 1/2 fallback-to-block behavior). The sender sees a warning; the recipient never receives the message.

### Layer 0 Scoring

When Layer 0 catches a message, it adds **`warn`-level score points** (default: 1 point per the `escalation.score-weights.warn` setting) to the player's cumulative score. There is no separate `filter.action` — the escalation engine handles all enforcement. This means:

- 1st–2nd Layer 0 catches: score 1–2 → no threshold hit → message blocked, no further action
- 3rd catch: score 3 → hits first threshold → **warn**
- 6th catch: score 6 → hits second threshold → **mute 5 minutes**
- And so on up the escalation ladder

This keeps all punishment logic in one place (the escalation engine) regardless of which layer flagged the message.

### Why a Trie?

Layer 0 uses a **trie (prefix tree)** rather than a compiled regex for the local word filter:

- **O(k) lookup** — Cost depends only on word length, not dictionary size. A regex alternation `\b(w1|w2|...|wN)\b` degrades as N grows into thousands of words.
- **Single-pass scanning** — Slide through the message character by character, walking the trie at each position. Catches profanity embedded anywhere in one pass.
- **Leet-speak normalization** — Characters are normalized (`@→a`, `0→o`, `$→s`, `3→e`, `1→i`) before trie lookup, defeating trivial evasion.
- **Abbreviation expansion** — A separate dictionary maps common foul abbreviations (`stfu`, `fml`, `sybau`, `fk`, `gtfo`, `kys`, etc.) to their expanded forms, which are then checked against the trie. Abbreviations are matched as **whole words only** (not substrings) to avoid false positives on short tokens like `fk` or `bs`.
- **Multi-language** — All 28 LDNOOBW language files are loaded into a single trie at startup. A player typing profanity in any language is caught by the same scan.
- **Always available** — Runs locally with zero network dependency. Acts as the safety net when API layers are unreachable.

#### Memory footprint

Each trie node uses a **compact sorted `char[]` + `TrieNode[]`** (binary-searched children) rather than a `HashMap` per node or a flat ASCII array. With ~4,000–5,000 words across 28 languages (~15,000–20,000 nodes after prefix sharing), this keeps the trie at **~1–2 MB** of heap — under 0.01% of a typical 4–16 GB Paper server allocation. The abbreviation map adds ~10–20 KB on top.

| Alternative considered | Bytes/node | Total estimate | Why not |
|---|---|---|---|
| `HashMap<Character, TrieNode>` | ~250 | ~5 MB | Excessive object overhead per node |
| `TrieNode[128]` ASCII array | ~550 | ~11 MB | Sparse, wastes memory on non-ASCII languages |
| Double-array trie / DAFSA | ~8–16 | ~200–300 KB | Complex to implement, hard to modify at runtime |
| **Sorted `char[]` + `TrieNode[]`** | **~50–80** | **~1–2 MB** | **Chosen — simple, compact, pure Java** |

## Project Structure

```
ChatSentry/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # GitHub Actions — build + test on push/PR
│       └── publish.yml               # GitHub Actions — publish to Modrinth + Hangar on tag/dev push
├── build.gradle.kts              # Gradle build config (Paper 1.21.11, Java 21, version source of truth)
├── settings.gradle.kts           # Project settings
├── gradle.properties             # API coords, dependency versions
├── gradle/
│   └── wrapper/                  # Gradle wrapper (9.4.1)
│
└── src/
    └── main/
        ├── java/io/github/driftn2forty/chatsentry/
        │   ├── ChatSentry.java                    # Plugin entry point (extends JavaPlugin)
        │   ├── config/
        │   │   └── PluginConfig.java                # Typed config wrapper (YAML-backed)
        │   │
        │   ├── listener/
        │   │   ├── ChatListener.java                # AsyncChatEvent handler, dispatches to pipeline
        │   │   ├── WhisperListener.java              # Intercepts /msg, /tell, /w for moderation + context capture
        │   │   ├── SignListener.java                  # SignChangeEvent — Layer 0 filter on sign text
        │   │   ├── BookListener.java                  # PlayerEditBookEvent — full pipeline on book content
        │   │   └── AnvilListener.java                 # InventoryClickEvent — Layer 0 filter on anvil renames
        │   │
        │   ├── filter/
        │   │   ├── ProfanityTrie.java               # Trie data structure for O(k) word lookup
        │   │   ├── ChatNormalizer.java               # Leet-speak & unicode normalization
        │   │   ├── AbbreviationExpander.java         # Maps abbreviations → expansions before trie check
        │   │   └── LocalFilterLayer.java             # Layer 0: scans messages against the trie
        │   │
        │   ├── moderation/
        │   │   ├── ModerationPipeline.java          # Orchestrates Layer 0 → 1 → 2 flow
        │   │   ├── ModerationResult.java            # Verdict enum + metadata
        │   │   ├── layer1/
        │   │   │   └── OpenAIModerationClient.java  # Calls POST /v1/moderations
        │   │   └── layer2/
        │   │       └── LLMReviewClient.java         # Calls chat completions endpoint
        │   │
        │   ├── action/
        │   │   ├── ActionDispatcher.java            # Maps verdicts → enforcement actions
        │   │   ├── MuteManager.java                 # Tracks mute state, enforces mutes (DB-backed)
        │   │   ├── ScoreCalculator.java              # Computes cumulative player score with decay
        │   │   ├── EscalationEngine.java             # Escalates punishment based on score thresholds
        │   │   └── StaffNotifier.java               # Sends alerts to online staff
        │   │
        │   ├── storage/
        │   │   ├── PlayerRepository.java             # Interface: load/save player data, log events
        │   │   ├── PlayerData.java                   # Player state POJO (score, mute expiry, settings)
        │   │   ├── ModerationEntry.java              # Single moderation event POJO
        │   │   ├── SQLiteRepository.java             # Default backend — embedded, zero-setup
        │   │   ├── MySQLRepository.java              # Optional backend for shared/network servers
        │   │   ├── PostgreSQLRepository.java         # Optional backend for larger deployments
        │   │   └── RetentionPurger.java              # Scheduled task — deletes logs older than retention window
        │   │
        │   ├── history/
        │   │   ├── PlayerHistoryTracker.java        # Per-player message ring buffer (in-memory, not persisted)
        │   │   ├── ContextAssembler.java             # Builds multi-player context payload for Layer 2
        │   │   ├── ChatMessage.java                  # Message record: type (chat|whisper), sender, target, text, timestamp
        │   │   └── ChatLogWriter.java                # Writes all messages to chatsentry_chat table (when enabled)
        │   │
        │   ├── command/
        │   │   └── ChatSentryCommand.java         # /chatsentry reload|status|history|purge
        │   │
        │   ├── hook/
        │   │   ├── PlaceholderAPIHook.java           # Registers %chatsentry_*% placeholders (soft dependency)
        │   │   └── BStatsHook.java                   # Anonymous usage metrics via bStats
        │   │
        │   └── util/
        │       ├── HttpUtil.java                    # Shared async HTTP client (java.net.http)
        │       ├── RateLimiter.java                 # Token-bucket rate limiter for API calls
        │       └── DebugLogger.java                  # Dispatches log messages (all levels) to console, file, and/or database
        │
        └── resources/
            ├── paper-plugin.yml                     # Paper plugin descriptor (modern format)
            ├── config.yml                           # Default configuration
            ├── words/                               # Static word lists checked into repo (LDNOOBW, CC-BY-4.0)
            │   ├── en.txt                            # English
            │   ├── es.txt                            # Spanish
            │   ├── fr.txt                            # French
            │   ├── de.txt                            # German
            │   ├── pt.txt                            # Portuguese
            │   ├── ru.txt                            # Russian
            │   ├── zh.txt                            # Chinese
            │   ├── ja.txt                            # Japanese
            │   ├── ko.txt                            # Korean
            │   └── ...                               # All 28 LDNOOBW languages
            └── abbreviations.txt                     # Hand-curated abbreviation → expansion mappings

    └── test/
        └── java/io/github/driftn2forty/chatsentry/
            ├── filter/
            │   ├── ProfanityTrieTest.java            # Trie insert, lookup, prefix sharing, unicode
            │   ├── ChatNormalizerTest.java            # Leet-speak → plain text conversion
            │   ├── AbbreviationExpanderTest.java      # Whole-word expansion, false positive avoidance
            │   └── LocalFilterLayerTest.java          # End-to-end Layer 0 scan with combined normalization
            │
            ├── moderation/
            │   └── ModerationPipelineTest.java       # Layer routing, timeout, fail-open behavior
            │
            ├── action/
            │   ├── ScoreCalculatorTest.java           # Score accumulation, decay over time, floor clamping
            │   ├── EscalationEngineTest.java          # Threshold transitions, duration escalation
            │   └── MuteManagerTest.java               # Mute apply/expire/check lifecycle
            │
            ├── storage/
            │   ├── SQLiteRepositoryTest.java          # CRUD operations, JSON round-tripping
            │   └── RetentionPurgerTest.java           # TTL-based deletion, edge cases (0 = keep forever)
            │
            ├── history/
            │   ├── PlayerHistoryTrackerTest.java      # Ring buffer overflow, whisper capture
            │   └── ContextAssemblerTest.java          # Multi-player assembly, score attachment, radius filtering
            │
            └── util/
                └── RateLimiterTest.java               # Token bucket refill, burst, exhaustion
```

### Static Resources — No Build-Time Downloads

All word lists and abbreviation mappings are **static files checked into the repository**. Nothing is downloaded at build time or runtime.

- **`resources/words/*.txt`** — Copied once from [LDNOOBW](https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words) (CC-BY-4.0). One file per language, one word per line. Updated manually when upstream releases new versions.
- **`resources/abbreviations.txt`** — Hand-curated by us. Maps offensive abbreviations to their expanded forms (`stfu=shut the fuck up`). ~50–100 entries. Server owners can add more via `custom-abbreviations` in config.

Both ship inside the plugin jar. At startup the plugin reads them from the classpath, loads words into the trie, and builds the abbreviation map. No network calls, no external dependencies.

### Database Architecture — UUID + JSON, No Migrations

All player and moderation data is stored using a **UUID + JSON blob** pattern. The schema is intentionally minimal and **never needs migration**:

```sql
-- Player state: score, mute status, preferences
CREATE TABLE chatsentry_players (
    uuid         CHAR(36)  PRIMARY KEY,
    data         TEXT       NOT NULL,    -- JSON blob (PlayerData)
    updated_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Moderation audit log: every flagged event
CREATE TABLE chatsentry_log (
    id           INTEGER    PRIMARY KEY AUTOINCREMENT,
    uuid         CHAR(36)  NOT NULL,
    data         TEXT       NOT NULL,    -- JSON blob (ModerationEntry)
    created_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_uuid ON chatsentry_log (uuid);
CREATE INDEX idx_log_created ON chatsentry_log (created_at);

-- Debug log: pipeline decisions, API calls, timing (only when debug.log-to-database is true)
CREATE TABLE chatsentry_debug (
    id           INTEGER    PRIMARY KEY AUTOINCREMENT,
    level        VARCHAR(8) NOT NULL,    -- DEBUG, INFO, WARN, ERROR
    source       VARCHAR(64) NOT NULL,   -- Class/component name
    message      TEXT       NOT NULL,
    created_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_debug_created ON chatsentry_debug (created_at);

-- Full chat log: all messages, not just flagged (only when chat-log.enabled is true)
CREATE TABLE chatsentry_chat (
    id           INTEGER    PRIMARY KEY AUTOINCREMENT,
    uuid         CHAR(36)  NOT NULL,
    player_name  VARCHAR(16) NOT NULL,
    source       VARCHAR(8) NOT NULL,    -- chat, whisper, sign, book, anvil
    message      TEXT       NOT NULL,
    created_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_uuid ON chatsentry_chat (uuid);
CREATE INDEX idx_chat_created ON chatsentry_chat (created_at);
```

**Why this approach over normalized tables:**

| Concern | Normalized schema | UUID + JSON |
|---|---|---|
| Add a new field | `ALTER TABLE` + migration script | Add field to Java class with default — done |
| Cross-DB portability | Dialect-specific DDL/types | Identical SQL across SQLite/MySQL/PostgreSQL |
| Migration framework | Required (Flyway, Liquibase, hand-rolled) | Not needed — schema never changes |
| Query by player | Fast | Fast (PK lookup) |
| Query across all players | Full SQL | Requires deserializing JSON (rarely needed) |
| Schema versioning | Must track version + run migrations on startup | N/A |

The **`PlayerData` JSON blob** contains:

```json
{
  "score": 4.5,
  "lastDecayTimestamp": "2026-04-14T00:00:00Z",
  "muteExpiry": "2026-04-14T18:30:00Z",
  "totalOffenses": 7,
  "lastOffense": "2026-04-14T18:00:00Z"
}
```

Recent messages are held **in memory only** (not persisted in the player JSON). They exist for Layer 2 context during the current server session and are discarded on restart. See the `history` config section.

Score decay uses **lazy evaluation**: each time a player record is accessed, the plugin checks `lastDecayTimestamp` against the current time. If enough time has passed (based on `escalation.decay.points-per-day`), the score is decayed proportionally and `lastDecayTimestamp` is updated. This avoids scheduled tasks iterating over offline players and guarantees scores are always current when read.

The **`ModerationEntry` JSON blob** (stored in `chatsentry_log`) contains:

```json
{
  "player": "Steve",
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "message": "the flagged message text",
  "source": "chat",
  "layer": 1,
  "verdict": "MUTE",
  "categories": ["harassment", "hate"],
  "moderationScore": 0.92,
  "playerScoreBefore": 3.0,
  "playerScoreAfter": 6.0,
  "actionTaken": "mute",
  "actionDuration": 300,
  "responseTimeMs": 245,
  "timestamp": "2026-04-14T18:00:00Z"
}
```

| Field | Description |
|---|---|
| `player` | Player name at time of offense |
| `uuid` | Player UUID |
| `message` | The original message text |
| `source` | Where it came from: `chat`, `whisper`, `sign`, `book`, `anvil` |
| `layer` | Which layer made the final decision: `0`, `1`, or `2` |
| `verdict` | The verdict: `WARN`, `MUTE`, `ESCALATE` |
| `categories` | Moderation categories flagged (Layer 1/2 only, empty for Layer 0) |
| `moderationScore` | API confidence score (Layer 1/2 only, `0.0` for Layer 0) |
| `playerScoreBefore` | Player's cumulative score before this offense |
| `playerScoreAfter` | Player's cumulative score after adding points |
| `actionTaken` | The actual enforcement action: `warn`, `mute`, `escalate` |
| `actionDuration` | Mute duration in seconds (0 if not a mute) |
| `responseTimeMs` | Pipeline processing time in milliseconds |
| `timestamp` | ISO 8601 timestamp |

When we add a new field (e.g. `"appealStatus"`), Gson deserializes old records with `null` / default for the missing field. No DDL, no migration, no versioning table. The schema is frozen at `CREATE TABLE` and never touched again.

**Redis consideration:** Evaluated and deferred. Redis adds a mandatory external service for a use case that doesn't need sub-millisecond reads. If future multi-server networks need shared caching, it would sit as a cache layer in front of SQL — not replace the durable store.

## Configuration (`config.yml`)

```yaml
# ── Layer 1: Moderation API ──────────────────────────────────────────
# Used for fast, cheap initial screening. Defaults to OpenAI Moderation API.
# The only hard requirement is an endpoint that accepts the OpenAI moderation
# request format and returns category scores.
layer1:
  enabled: false                         # Disabled by default — requires API key to use
  base-url: "https://api.openai.com"     # Base URL (no trailing slash, no path)
  api-key: "${OPENAI_API_KEY}"           # Supports env var substitution with ${VAR}
  model: "omni-moderation-latest"
  category-thresholds:                           # Per-category confidence override (default: pipeline.layer1-threshold)
    sexual/minors: 0.3                           # Strictest — flag with minimal confidence
    self-harm/instructions: 0.4
    self-harm/intent: 0.4
    hate/threatening: 0.5
    harassment/threatening: 0.5
    illicit/violent: 0.5
    self-harm: 0.6
    hate: 0.6
    violence/graphic: 0.6
    harassment: 0.7
    sexual: 0.7
    violence: 0.7
    illicit: 0.7
  category-weights:                              # Per-category score weight override (default: escalation.score-weights based on verdict)
    sexual/minors: 5                             # Immediate heavy scoring — hits mute threshold fast
    self-harm/instructions: 5
    hate/threatening: 4
    harassment/threatening: 4
    illicit/violent: 4
    self-harm/intent: 3
    self-harm: 3
    hate: 3
    violence/graphic: 3
    sexual: 2
    harassment: 1
    violence: 1
    illicit: 1
  category-commands: {}                          # Per-category commands fired when that category is flagged
  # Example:
  # category-commands:
  #   sexual/minors:
  #     - "kick %player% Inappropriate content"
  #   hate/threatening:
  #     - "tempban %player% 1h Hate speech"

# ── Layer 2: LLM Deep Review ─────────────────────────────────────────
# Used for context-aware analysis of flagged messages. Defaults to OpenAI,
# but any provider offering an OpenAI-compatible /v1/chat/completions
# endpoint works: Ollama, LM Studio, vLLM, Azure OpenAI, Together, Groq, etc.
layer2:
  enabled: false                         # Disabled by default — requires API key to use
  base-url: "https://api.openai.com"     # Change for other providers (see examples below)
  api-key: "${OPENAI_API_KEY}"           # Can be a different key/provider than Layer 1
  model: "gpt-4o"
  system-prompt: |
    You are a Minecraft chat moderator. You will receive a flagged message
    along with recent chat context from multiple players (with identifiers
    and current moderation scores). Evaluate the flagged message in context.
    Respond with a JSON verdict: ALLOW, WARN, MUTE, or ESCALATE.
  max-tokens: 150
  temperature: 0.0

# ── Example: Layer 2 with Ollama (local, free) ───────────────────────
# layer2:
#   base-url: "http://localhost:11434"
#   api-key: ""                           # Ollama doesn't require a key
#   model: "llama3"

# ── Example: Layer 2 with Azure OpenAI ───────────────────────────────
# layer2:
#   base-url: "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT"
#   api-key: "${AZURE_OPENAI_KEY}"
#   model: "gpt-4o"

filter:
  enabled: true               # Enable/disable Layer 0 local filter
  languages: "*"              # "*" = all languages, or list: [en, es, de, fr]
  custom-words: []            # Additional words added by server owner
  whitelist: []               # Words to exclude from filtering (reduce false positives)
  leet-speak: true            # Normalize leet-speak before lookup
  abbreviations: true         # Expand known foul abbreviations before checking
  custom-abbreviations: {}    # Extra mappings, e.g. { "smfh": "shaking my f***ing head" }

pipeline:
  layer1-threshold: 0.7       # Score above which Layer 2 is invoked
  async: true                 # Process moderation off the main thread
  timeout-ms: 3000            # Max wait before allowing message through (hard ceiling — retries happen within this window)
  fail-open: true             # If API unreachable, Layer 0 still active
  message-mode: "block"       # block = cancel message entirely | mask = replace offensive words with ***
  retry:
    max-attempts: 3           # Total attempts per API call (1 = no retry). Retries are opportunistic within timeout-ms.
    base-delay-ms: 500        # Initial backoff delay (doubles each retry: 500 → 1000 → 2000)
    max-delay-ms: 5000        # Backoff cap (only relevant with generous timeout-ms values)

actions:
  warn:
    message: "&cYour message was flagged. Please keep chat respectful."
  mute:
    duration-seconds: 300
    message: "&cYou have been muted for 5 minutes."
  escalate:
    staff-permission: "chatsentry.staff"
    log-to-file: true

# ── Escalation: automatic punishment scaling ─────────────────────────
# Player scores increase with each offense and decay over time.
# Thresholds map cumulative scores to actions.
escalation:
  enabled: true
  score-weights:                         # Points added per verdict type
    warn: 1
    mute: 3
    escalate: 5
  decay:
    points-per-day: 0.5                  # Score decays by this amount daily
    min-score: 0                         # Score floor
  thresholds:                            # Cumulative score → action
    - { score: 3,  action: "warn", commands: [] }
    - { score: 6,  action: "mute", duration-seconds: 300, commands: [] }
    - { score: 12, action: "mute", duration-seconds: 1800, commands: [] }
    - { score: 20, action: "mute", duration-seconds: 86400, commands: [] }
    - { score: 30, action: "escalate", commands: [] }  # Permanent action — staff review
  # Example with commands:
  # thresholds:
  #   - { score: 3,  action: "warn", commands: ["say %player% was warned by ChatSentry"] }
  #   - { score: 30, action: "escalate", commands: ["ban %player% Repeated violations"] }

# ── Storage ──────────────────────────────────────────────────────────
storage:
  backend: "sqlite"                      # sqlite | mysql | postgresql

  sqlite:
    file: "chatsentry.db"              # Relative to plugin data folder

  mysql:
    host: "localhost"
    port: 3306
    database: "chatsentry"
    username: "${DB_USER}"
    password: "${DB_PASS}"
    pool-size: 5

  postgresql:
    host: "localhost"
    port: 5432
    database: "chatsentry"
    username: "${DB_USER}"
    password: "${DB_PASS}"
    pool-size: 5

history:
  messages-per-player: 10     # Recent messages stored per player in the in-memory ring buffer (lost on restart)
  context-window: 25          # Total messages (across all nearby players) sent to Layer 2 per review
  include-whispers: true      # Capture /msg, /tell, /w for context (still moderated by full pipeline)
  include-scores: true        # Attach each player's current score to the Layer 2 context payload
  context-radius: -1          # Only include messages from players within this block radius (-1 = same world, no limit)

# ── Chat Log ───────────────────────────────────────────────────────────
chat-log:
  enabled: false              # Store ALL messages (not just flagged) in chatsentry_chat table
  ttl-days: 30                # Delete chat log entries older than this (0 = keep forever)

retention:
  log-ttl-days: 90            # Delete moderation log entries older than this (0 = keep forever)
  player-ttl-days: 180        # Remove player records with no activity for this many days (0 = keep forever)
  debug-ttl-days: 7           # Delete debug log DB entries older than this (0 = keep forever, caution: grows fast)
  purge-interval-hours: 24    # How often the purge task runs (minimum: 1)

rate-limit:
  requests-per-second: 20     # Global API rate limit

# ── Debug / Logging ────────────────────────────────────────────────────────────
logging:
  log-to-console: true        # Print info/warning/error messages to server console
  log-to-file: true           # Write info/warning/error messages to plugins/ChatSentry/chatsentry.log (daily rotation)
  log-to-database: false      # Store info/warning/error entries in chatsentry_debug table
  debug:
    enabled: false            # When true, include DEBUG-level messages in all enabled outputs above
    verbose-layers: false     # Log full API request/response bodies for Layer 1 and Layer 2
    verbose-trie: false       # Log every trie match attempt (very noisy — use only for troubleshooting)

```

## Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/chatsentry reload` | Reload config from disk | `chatsentry.admin` |
| `/chatsentry status` | Show pipeline health & stats | `chatsentry.admin` |
| `/chatsentry history <player>` | View recent flagged messages | `chatsentry.staff` |
| `/chatsentry purge [days]` | Manually purge logs older than N days (default: config value) | `chatsentry.admin` |

### `/chatsentry status` Output

```
ChatSentry v1.0.0
  API latency (Layer 1): avg 142ms / p99 310ms
  API latency (Layer 2): avg 580ms / p99 1120ms
  Messages processed: 12,847 total (214/hr)
  Layer 0 catches: 463 (3.6%)
  Layer 1 calls: 12,384 — flagged 87 (0.7%)
  Layer 2 calls: 87 — confirmed 41 (47.1%)
  API errors: 3 (last: 2m ago)
  Active mutes: 2
```

All counters reset on plugin reload.

| Permission | Description | Default |
|---|---|---|
| `chatsentry.bypass` | Skip all moderation (chat, whispers, signs, books, anvils) | `false` |
| `chatsentry.staff` | Receive escalation alerts | `op` |
| `chatsentry.admin` | Full admin access | `op` |

## Build & Run

**Requirements:** Java 21+, Gradle 9.4.1+

```bash
# Build the plugin jar (includes shadow/relocation)
./gradlew build

# Run all tests
./gradlew test

# Output: build/libs/ChatSentry-<version>.jar    (shaded, production-ready)
# Copy to your Paper server's plugins/ directory
```

The build uses the **Gradle Shadow plugin** to shade and relocate runtime dependencies (HikariCP, JDBC drivers, bStats) under `io.github.driftn2forty.chatsentry.lib.*`. This prevents version conflicts when other plugins bundle the same libraries. Shadow is configured to replace the default jar (`archiveClassifier.set("")`), so the single output jar is the deployable artifact.

## Dependencies

| Dependency | Purpose |
|---|---|
| Paper API 1.21.11 | Server API (provided at runtime) |
| `java.net.http.HttpClient` | Async HTTP calls to moderation APIs (JDK built-in) |
| Gson | JSON serialization (bundled with Paper) |
| HikariCP | JDBC connection pooling for MySQL/PostgreSQL (shaded + relocated) |
| `java.sql` / SQLite JDBC | Default embedded database (JDK + bundled driver) |
| MySQL Connector/J | Always shaded into the jar; only instantiated if `storage.backend: mysql` |
| PostgreSQL JDBC | Always shaded into the jar; only instantiated if `storage.backend: postgresql` |
| bStats | Anonymous usage metrics (shaded + relocated) |
| PlaceholderAPI | Optional soft dependency — exposes `%chatsentry_*%` placeholders |
| JUnit 5 | Unit testing framework (test only) |
| MockBukkit | Paper API mocking for unit tests (test only) |
| Gradle Shadow Plugin | Shades and relocates runtime dependencies into the plugin jar |
| Minotaur (`com.modrinth.minotaur`) | Gradle plugin — automated publishing to Modrinth (build only) |
| Hangar Publish (`io.papermc.hangar-publish-plugin`) | Gradle plugin — automated publishing to Hangar (build only) |

All shaded dependencies are **relocated** under `io.github.driftn2forty.chatsentry.lib.*` to prevent classpath conflicts with other plugins that bundle the same libraries. All drivers (SQLite, MySQL, PostgreSQL) and HikariCP are included in every build — the jar is self-contained. At runtime, only the configured backend's driver is instantiated; the others sit in the jar unused.

## CI/CD

The repository includes **two GitHub Actions workflows**:

### `ci.yml` — Build & Test (every push / PR)

1. **Build** — `./gradlew build` on Ubuntu with Java 21. The entire project must compile with zero warnings.
2. **Test** — `./gradlew test` runs the full JUnit 5 + MockBukkit suite. PRs with failing tests are blocked from merge.
3. **Lint** — (Future) Static analysis via SpotBugs or Error Prone can be added as a build step.

### `publish.yml` — Release & Alpha Publishing

| Trigger | Version | Channel | Platforms |
|---|---|---|---|
| Tag push (`v*.*.*`) | Tag name (e.g. `1.0.0`) | **Release** | Modrinth + Hangar + GitHub Release |
| Push to `dev` branch | `<version>-alpha+<short-sha>` | **Alpha** | Modrinth + Hangar |

The publish workflow:
1. Runs the full build + test suite first (gate — never publishes broken code)
2. Publishes the shaded jar to **Modrinth** via the `com.modrinth.minotaur` Gradle plugin
3. Publishes the same jar to **Hangar** (PaperMC) via the `io.papermc.hangar-publish-plugin` Gradle plugin
4. On tagged releases, also creates a **GitHub Release** with the jar attached and auto-generated changelog

API tokens are stored as GitHub Actions secrets:
- `MODRINTH_TOKEN` — Modrinth API token
- `HANGAR_TOKEN` — Hangar API token

**SpigotMC** is intentionally excluded — it has no upload API, requiring manual web submissions. The audience overlaps heavily with Hangar. A SpigotMC listing can be added manually later if there is demand.

### Branch Protection (`main`)

- CI must pass (build + test green)
- At least one approving review

### Branch Protection (`dev`)

- CI must pass (build + test green)

This ensures no broken code lands on either long-lived branch. Contributors run `./gradlew build` locally before pushing, and CI provides a second safety net.

### Branching Strategy

The repository uses two permanent branches with a standard feature-branch workflow:

```
feature/add-signs  ──PR──┐
fix/trie-unicode   ──PR──┤
                         ▼
                        dev  ── alpha builds auto-publish on each merge ──
                         │
                         │  Ready for release? Open PR: dev → main
                         ▼
                        main ── tag v1.0.0 → release build publishes ──
```

- **`main`** — Always stable. Every commit on `main` is (or leads to) a tagged release. Nobody pushes directly to `main`.
- **`dev`** — Active development. All PRs from contributors target `dev`. Each merge triggers an alpha build. `dev` is permanent — it is **not** recreated after each release.
- **Feature/fix branches** — Contributors fork from `dev`, work on `feature/short-description` or `fix/short-description`, and open a PR back to `dev`.

**Release cycle:**
1. PRs are merged into `dev` throughout development. Each merge publishes an alpha.
2. When `dev` is feature-complete, submit a **version bump PR** to `dev` that updates `version` in `build.gradle.kts` (e.g. `1.0.0` → `1.1.0`). This is the signal that the cycle is ready to close.
3. Open a PR from `dev` → `main`. This requires a review and passing CI.
4. After merging, tag the commit on `main` (e.g. `v1.1.0`). The tag triggers the release publish.
5. Continue merging new PRs into `dev` for the next version.

### Version Management

The plugin version is defined in **one place** — `build.gradle.kts`:

```kotlin
version = "1.0.0"
```

`paper-plugin.yml` references it via token replacement so they stay in sync automatically:

```yaml
name: ChatSentry
version: ${version}
```

Gradle's `processResources` task replaces `${version}` at build time. You never edit the version in `paper-plugin.yml` directly.

**Alpha builds** on the `dev` branch use the same base version from `build.gradle.kts` but the publish workflow appends `-alpha+<short-sha>` at build time (without modifying the file), producing versions like `1.1.0-alpha+a3f4b2c` on Modrinth and Hangar.

### Release Checklist

All steps are performed on **GitHub's website** — no command line required.

1. **Version bump PR:** Submit a PR to `dev` updating `version` in `build.gradle.kts` to the target release version. Merge it.
2. **Open a PR:** `dev` → `main`. Review the combined diff of all changes since the last release.
3. **Merge the PR.** This lands all `dev` changes onto `main`.
4. **Create the release:** Go to **Releases** → **"Draft a new release"**.
5. **Tag it:** Click **"Choose a tag"** → type `v1.0.0` → **"Create new tag: v1.0.0 on publish"** → Target: `main`.
6. **Release notes:** Click **"Generate release notes"** (GitHub auto-fills from merged PRs) or write your own.
7. **Publish.** Click **"Publish release"**. This triggers `publish.yml` which builds, tests, and publishes the jar to Modrinth + Hangar automatically.
8. **Verify:** Check the workflow run under **Actions**, then confirm the new version appears on Modrinth and Hangar.

## Design Principles

- **Async by default** — All API calls and database I/O happen off the main server thread. Chat events are processed asynchronously to avoid tick lag.
- **Fail-open with local safety net** — If the moderation APIs are unreachable, Layer 0 (trie filter) still catches explicit profanity locally. Messages are never silently dropped.
- **Minimal footprint** — SQLite default requires zero external setup. All JDBC drivers are shaded into the jar but only the configured backend is instantiated at runtime.
- **UUID + JSON storage** — Player data is stored as `uuid (PK) + JSON blob`. Adding new fields means updating the Java class — Gson deserializes old records with defaults for missing fields. No schema migrations, no versioning table, ever.
- **Repository pattern** — A `PlayerRepository` interface abstracts storage. Adding a new database backend is a single class implementation. The rest of the codebase never touches SQL.
- **Escalating punishments** — Player scores accumulate with each offense and decay over time. Thresholds trigger progressively harsher actions automatically.
- **Configurable thresholds** — Server owners tune sensitivity, actions, prompts, score weights, and decay rates without touching code.
- **Rate-limited** — Built-in token-bucket rate limiter prevents API quota exhaustion under load.
- **Graceful shutdown** — On server stop, the plugin cancels all pending async tasks, flushes buffered player data and log entries to the database, and closes connection pools cleanly. No data is lost on `/stop` or `SIGTERM`.
- **Thread-safe player access** — Player data is accessed from async moderation threads. A per-player `ReentrantLock` ensures that concurrent events for the same player (e.g. rapid chat + whisper) are serialized. Different players are processed in parallel without contention.
- **Retry with exponential backoff** — Failed API calls retry up to `max-attempts` with exponentially increasing delays (500ms → 1s → 2s, capped at `max-delay-ms`). All retries happen **within** the `timeout-ms` window — the message is never held longer than that ceiling. If the remaining time is too short for another retry + backoff, the pipeline skips the retry and falls through immediately. Each failed attempt logs a `WARNING` to console so admins can spot degraded connectivity. After all retries exhaust or the timeout expires, the pipeline falls through to the next layer or fail-open as configured. With the default 3000ms timeout, expect 2–3 realistic attempts before the window closes.
- **Thoroughly tested** — Every testable component has unit tests: trie operations, normalization, score math, threshold transitions, DB round-tripping, retention purging, and pipeline routing. MockBukkit provides a headless Paper server for event-driven tests without a live Minecraft instance.
- **First-run friendly** — Layers 1 and 2 are **disabled by default**. Out of the box, only Layer 0 (local trie filter) is active — no API keys required. On startup, the plugin logs:
  - `[INFO] Layer 1 disabled — only Layer 0 (local filter) is active.` (when Layer 1 is disabled)
  - `[INFO] Layer 2 disabled.` (when Layer 2 is disabled)
  - `[WARN] Layer 1 enabled but api-key is not set — disabling Layer 1.` (when enabled with missing/empty key)
  - `[WARN] Layer 2 enabled but api-key is not set — disabling Layer 2.` (same for Layer 2)
  - `[WARN] Layer 2 is enabled without Layer 1. Every message passing Layer 0 will be sent to the LLM endpoint — this may incur significant API costs and latency. Consider enabling Layer 1 as a pre-filter.` (when Layer 2 is on but Layer 1 is off — valid config, but worth flagging)
  This ensures the plugin always starts cleanly, even with a default config.

## Integrations

### PlaceholderAPI

If [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is installed, ChatSentry registers the following placeholders automatically (soft dependency — the plugin works fine without it):

| Placeholder | Returns | Example |
|---|---|---|
| `%chatsentry_score%` | Player's current moderation score | `4.5` |
| `%chatsentry_muted%` | Whether the player is currently muted | `true` / `false` |
| `%chatsentry_mute_remaining%` | Time left on active mute (human-readable) | `4m 32s` / `—` |
| `%chatsentry_total_offenses%` | Lifetime offense count | `7` |
| `%chatsentry_last_offense%` | Time since last offense | `2h ago` / `never` |

These placeholders can be used in scoreboards, tab lists, holograms, or any plugin that supports PlaceholderAPI — letting staff see moderation state at a glance without running commands.

### Custom Commands

ChatSentry can execute arbitrary console commands when a violation is detected. Commands are dispatched on the main server thread via `Bukkit.dispatchCommand` as the console sender. Two hooks are available:

- **Per-threshold commands** — Defined in `escalation.thresholds[].commands`. Fired when a player's cumulative score crosses the threshold.
- **Per-category commands** — Defined in `layer1.category-commands.<category>`. Fired when Layer 1 flags a specific moderation category.

Category commands fire before threshold commands. Both support the following placeholder tokens:

| Placeholder | Resolves to | Example |
|---|---|---|
| `%player%` | Player name | `Notch` |
| `%uuid%` | Player UUID | `069a79f4-...` |
| `%score%` | Player score after this offense | `6.0` |
| `%score_before%` | Player score before this offense | `3.0` |
| `%category%` | Highest flagged category (or empty) | `hate/threatening` |
| `%moderation_score%` | Raw moderation API confidence score | `0.95` |
| `%source%` | Event source | `chat`, `whisper`, `book` |
| `%action%` | Escalation action taken | `warn`, `mute`, `escalate` |
| `%duration%` | Mute duration in seconds (0 if not muted) | `300` |
| `%layer%` | Layer that flagged the message | `0`, `1`, `2` |

Example configuration:
```yaml
layer1:
  category-commands:
    sexual/minors:
      - "kick %player% Inappropriate content"
    hate/threatening:
      - "tempban %player% 1h Hate speech"

escalation:
  thresholds:
    - { score: 3,  action: "warn", commands: ["say %player% was warned by ChatSentry"] }
    - { score: 30, action: "escalate", commands: ["ban %player% Repeated violations"] }
```

### bStats

ChatSentry includes [bStats](https://bstats.org) for anonymous, aggregate usage metrics. **No player data, messages, or API keys are ever transmitted.** bStats is controlled globally via `plugins/bStats/config.yml` — there is no plugin-level toggle (consistent with standard Paper plugin conventions).

In addition to bStats' built-in server metrics (Java version, server software, player count, etc.), ChatSentry submits the following **custom charts**:

| Chart | Type | What it reports |
|---|---|---|
| Layer 2 Model | Simple Pie | The `layer2.model` value (`gpt-4o`, `llama3`, etc.) |
| Layer 2 Provider | Simple Pie | Derived from `layer2.base-url` — `OpenAI`, `Ollama`, `Azure`, `Other` |
| Storage Backend | Simple Pie | `sqlite`, `mysql`, or `postgresql` |
| Message Mode | Simple Pie | `block` or `mask` |
| Active Layers | Simple Pie | Which layers are enabled: `0 only`, `0+1`, `0+2`, `0+1+2` |
| Filter Languages | Simple Pie | Bucketed count: `1`, `2–5`, `6–10`, `11+`, `all` |
| Messages Moderated/Hour | Simple Pie | Bucketed: `<100`, `100–500`, `500–1K`, `1K+` |
| Layer 0 Catch Rate | Simple Pie | % of flagged messages caught by trie before API: `<25%`, `25–50%`, `50–75%`, `75%+` |
| Escalation Enabled | Simple Pie | `true` or `false` |
| PlaceholderAPI Hooked | Simple Pie | `true` or `false` |

## Contributing

Contributions are welcome. Please follow these guidelines to keep the process smooth.

### Reporting Issues

1. **Search first.** Check [existing issues](../../issues) to avoid duplicates.
2. **Use the template.** Fill out every section — steps to reproduce, expected vs actual behavior, server version, Java version, and plugin version.
3. **Include logs.** Attach the relevant section of `logs/latest.log` with stack traces. Redact API keys and player IPs.
4. **One issue per report.** Don't bundle unrelated bugs into a single ticket.

### Pull Requests

1. **Open an issue first** for anything beyond a trivial fix. Discuss the approach before writing code.
2. **Fork and branch.** Create a feature branch from `dev` — name it `feature/short-description` or `fix/short-description`. All PRs target the `dev` branch, never `main` directly.
3. **Keep PRs small and focused.** One logical change per PR. Large refactors should be split into reviewable chunks.
4. **Follow existing style:**
   - Java 21 — use records, sealed interfaces, and pattern matching where appropriate.
   - Never wrap or break long lines of code. Keep statements on a single line.
   - No wildcard imports. No unused imports.
   - Use `final` for fields and local variables that don't change.
5. **Write tests.** New features need unit tests. Bug fixes need a regression test that fails without the fix.
6. **Run the build before pushing:**
   ```bash
   ./gradlew build
   ```
   All tests must pass and the build must succeed with zero warnings.
7. **Update documentation.** If your change affects behavior, update `README.md`, config examples, or command/permission tables accordingly. The README is the source of truth.
8. **Update CHANGELOG.md.** Add an entry under `[Unreleased]` in the appropriate category (`Added`, `Changed`, `Fixed`, etc.) following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
9. **Commit messages.** Use clear, imperative-mood summaries: `Add score decay scheduler`, `Fix NPE when player has no history`. No `WIP` or `misc` commits — squash before opening the PR.
10. **No generated files.** Don't commit IDE configs (`.idea/`, `*.iml`), build outputs, or OS metadata (`.DS_Store`, `Thumbs.db`).

### Word List & Abbreviation Contributions

- PRs that add words to `resources/words/*.txt` must cite a source or explain why the word qualifies.
- Abbreviation additions in `resources/abbreviations.txt` must follow the `abbreviation=expanded form` format and include only offensive abbreviations that would bypass the trie on their own.
- Do not add words that are only offensive in very narrow or ambiguous contexts — false positives hurt more than missed catches.

### Code of Conduct

Be respectful and constructive. Harassment, personal attacks, and bad-faith engagement will result in removal from the project.

## License

MIT
