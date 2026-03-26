# Second Dawn RP — Fabric 1.21.1

A Star Trek-themed roleplay server mod for Fabric 1.21.1. Built for the Second Dawn RP server.

**Mod ID:** `seconddawnrp` | **Package:** `net.shard.seconddawnrp` | **Loom:** 1.15.5 | **Java:** 21

---

## Completed Phases

### Phase 1–3 — Core Profile & Task System
- Player profiles with division, rank, progression path, billets, certifications, duty status
- LuckPerms sync — rank and division pushed to permission groups on change
- Task system — active tasks, completion, officer approval, reward points
- Task terminals — physical blocks that filter available tasks by division
- SQLite persistence via JDBC, JSON backup layer
- `/profile` — unified command showing all player + character data

### Phase 4 — Engineering Degradation
- Component registration via tool — any block can be a tracked component
- Four-state health: NOMINAL → DEGRADED → CRITICAL → OFFLINE
- Automatic repair task generation when components degrade
- Engineering PAD item — code-drawn GUI showing all components, health bars, warp core status
- `/engineering` commands — register, locate, repair, set health

### Phase 4.5 — GM Tools
- **Environmental Effect Block** — radius-based vanilla status effect + medical condition application, configurable fire mode (continuous/on-entry) and linger mode
- **Trigger Block** — WALK_OVER and INTERACT modes, fires GM events or chat messages
- **Anomaly Marker** — visual markers for ongoing GM events
- **Tool Visibility Service** — GM items auto-show/hide based on held item
- **GM Registry** — persistent named location registry

### Phase 5 — Warp Core
- Multi-core support — multiple warp cores registered per server
- Fuel rods, resonance coils, containment cells as physical items
- State machine: OFFLINE → STARTING → ONLINE → UNSTABLE → CRITICAL → FAILED
- Power output pushed to TREnergy network each tick
- Physical controller block with GUI, warp core monitor screen
- `/warpcore` commands — register, fuel, start, stop, status

### Phase 5.5 — Character System
- **CharacterProfile merged into PlayerProfile** — one class, one repository, one save call
- Character fields: `characterName`, `species`, `bio`, `characterStatus`, `knownLanguages`, `universalTranslator`, `permadeathConsent`, `activeLongTermInjuryId`, `deceasedAt`, `progressionTransfer`, `serviceRecord`
- **SpeciesRegistry** — JSON-driven at `data/seconddawnrp/species/`, ships with `human.json`, GM-extensible
- **CharacterCreationTerminalBlock** — physical block, 3-tab code-drawn GUI (Identity → Bio → Confirm)
- Species locked after creation — GM override only via `/gm character set species`
- Starting languages seeded from species definition on creation
- **LongTermInjuryService** — three tiers (MINOR/MODERATE/SEVERE), tick refresh every 5 minutes, 24hr treatment cooldown, Medical treatment API
- **RdmDetectionService** — automatic RDM flag generation, GM notification
- **CharacterArchiveRepository** — write-only death snapshots, historical record preserved permanently
- `/profile` — unified command replacing all old character/profile commands, GM view shows UUID, character ID, permadeath, timestamps
- `/gm character kill` — two-step death with progression transfer percentage
- `/gm character set` — name, bio, species, permadeath overrides
- `/gm injury modify` — expiry adjustment in days
- **DB migrations:** V4 (character_profiles, long_term_injuries, rdm_flags), V5 (character columns on players, player_known_languages, service_record)

### Phase 6 — Dice + RP PADD System

#### Dice Engine
- `/roll` — d20 with rank bonus, certification bonuses, demerit penalties. Result held server-side, player sees immediately, others see nothing until GM broadcasts
- `/rp [action]` — third-person narration, bold gold formatting, broadcasts to all players, captured by active PADD sessions
- `/gm rolls public/private` — toggle auto-broadcast vs hold-and-broadcast mode
- `/gm roll broadcast [player]` — push specific held result to scene
- `/gm roll broadcastall` — push all held results at once
- `/gm roll group [players...]` — group roll session, 60-second timeout, GM sees highest/lowest/average/sum
- `/gm dc set [value]` — scene DC, per-player DC override supported
- `/gm dc clear`
- `/gm scenario create [Name] dc:[n] div:[division]` — session-only named scenarios (not persisted through restart)
- `/gm scenario call [player] [Name]` — call scenario for player, sets their DC and prompts them to roll
- `/gm scenario list`
- **RollModifierConfig** — JSON at `config/assets/seconddawnrp/roll_modifiers.json`, auto-created on boot, defines rank bonuses and cert bonuses

#### RP PADD Item
- Physical item — right-click opens code-drawn GUI
- GUI shows: recording status (pulsing red/green dot), entry count, live log with color-coded entries (amber=ROLL, white=RP), scrollable
- **Start/Stop button** — begins/ends recording session
- **Sign button** — locks the PADD, stamped with player name
- `/rp record start` — begin recording (supports `radius:N` and `players:name1,name2` options)
- `/rp record stop` — end recording, writes log to PADD item in hotbar

#### Submission Box Block
- Physical block — accepts signed RP PADDs on right-click
- Saves submission to `rp_padd_submissions` database table
- Notifies all online officers (op level 2+) via chat

#### Officer Review — Ops PADD PADS Tab
- Fifth tab on the Ops PADD: **PADS**
- Tab shows pending count badge: `PADS [3]`
- **Stacked layout:** submission list (4 rows, full width) on top, full-width log viewer below (~60 chars/line readable), action buttons at bottom
- Status dots: amber=PENDING, green=CONFIRMED, red=DISPUTED
- **CONFIRM** — marks resolved, notifies submitting player, generates Archive PADD item in officer inventory
- **DISPUTE** — inline note input (type + Enter), marks disputed with note, generates Archive PADD
- **Archive PADD** — physical signed item with stamped header (outcome, officer name, date, original log). Officer can hand it back to player or file it
- **Archive OFF/ON toggle** — bottom-right of button row, shows only PENDING by default, toggle shows all resolved submissions
- **7-day auto-cleanup** — resolved submissions purged after 7 days, PENDING never expire
- **DB migration:** V6 (rp_padd_submissions table)

---

## Architecture

```
UI / Commands / Events
        ↓
    Services
        ↓
   Repositories
        ↓
  Storage (SQLite + JSON)
```

### Key Singletons (`SecondDawnRP.java`)
| Singleton | Purpose |
|---|---|
| `DATABASE_MANAGER` | SQLite connection pool |
| `PROFILE_MANAGER` | In-memory profile cache + dirty tracking |
| `PROFILE_SERVICE` | All player + character operations |
| `PERMISSION_SERVICE` | LuckPerms wrapper |
| `TASK_SERVICE` | Task assignment, completion, rewards |
| `TASK_REWARD_SERVICE` | Points calculation |
| `TASK_PERMISSION_SERVICE` | Task access control |
| `TERMINAL_MANAGER` | Physical terminal block registry |
| `GM_EVENT_SERVICE` | Encounter templates, spawn blocks |
| `GM_PERMISSION_SERVICE` | GM access control |
| `DEGRADATION_SERVICE` | Component health, repair tasks |
| `WARP_CORE_SERVICE` | Warp core state machine + energy output |
| `CHARACTER_ARCHIVE` | Write-only death snapshot repository |
| `LONG_TERM_INJURY_SERVICE` | LTI application, tick refresh, treatment |
| `RDM_DETECTION_SERVICE` | RDM flag generation + GM notification |
| `SPECIES_REGISTRY` | JSON species definitions |
| `ROLL_SERVICE` | Dice engine, DC, scenarios, group rolls |
| `RP_PADD_SERVICE` | Active recording session tracking |
| `RP_PADD_SUBMISSION_SERVICE` | Submission save, review, archive |
| `RP_PADD_ITEM` | Cast reference for archive PADD generation |
| `ENV_EFFECT_SERVICE` | Environmental effect blocks |
| `TRIGGER_SERVICE` | Trigger blocks |
| `ANOMALY_SERVICE` | Anomaly markers |
| `GM_TOOL_VISIBILITY_SERVICE` | GM item auto-show/hide |
| `GM_REGISTRY_SERVICE` | Named location registry |

### Database Schema
| Table | Purpose |
|---|---|
| `players` | All player + character data (V1 + V5) |
| `player_billets` | Player billet assignments |
| `player_certifications` | Player certifications |
| `player_known_languages` | Live character languages (V5) |
| `player_active_tasks` | In-progress task state |
| `player_completed_tasks` | Task completion history |
| `ops_task_pool` | Officer-created task pool |
| `task_terminals` | Terminal block registrations |
| `components` | Degradation component registry |
| `character_profiles` | Deceased character archive (write-only) |
| `character_known_languages` | Languages at time of death (archive) |
| `long_term_injuries` | Active and historical LTIs |
| `rdm_flags` | RDM detection flags |
| `rp_padd_submissions` | RP PADD review queue (V6) |
| `schema_version` | Migration tracking |

### Critical Notes
1. **1.21.1 NBT API** — use `DataComponentTypes.CUSTOM_DATA` + `NbtComponent.of()`. `getOrCreateNbt()`, `setNbt()`, `getNbt()` are removed.
2. **BlockWithEntity subclasses** — must implement `getCodec()` returning `MapCodec`. Use `MapCodec.unit(ClassName::new)`.
3. **Command registration** — always in `onInitialize()` via `CommandRegistrationCallback`. Never inside `SERVER_STARTED`.
4. **LuckPerms block** — call `PROFILE_SERVICE.setProfileSyncService(syncService)`. Do NOT replace `PROFILE_SERVICE` instance.
5. **Payload registration** — must call `registerPayloads()` before `registerServerReceivers()` or crash on startup.
6. **TREnergy** — push-based. Use `Transaction.openOuter()` + `target.insert()` each tick, always commit.
7. **ProfileLtiCallback** — LongTermInjuryService uses this interface instead of CharacterRepository to update `activeLongTermInjuryId` on PlayerProfile.

---

## Config Files (auto-created on first boot)
- `config/assets/seconddawnrp/roll_modifiers.json` — rank and cert bonuses for dice rolls
- `config/assets/seconddawnrp/degradation_config.json` — component drain rates
- `config/assets/seconddawnrp/warp_core_config.json` — warp core parameters
- `data/seconddawnrp/species/*.json` — species definitions (ships with `human.json`)

## Resource Files Required
- `assets/seconddawnrp/textures/block/submission_box.png` (16×16)
- `assets/seconddawnrp/textures/block/character_creation_terminal.png` (16×16)
- `assets/seconddawnrp/blockstates/submission_box.json`
- `assets/seconddawnrp/blockstates/character_creation_terminal.json`
- `assets/seconddawnrp/models/block/submission_box.json`
- `assets/seconddawnrp/models/block/character_creation_terminal.json`
- `assets/seconddawnrp/models/item/submission_box.json`
- `assets/seconddawnrp/models/item/character_creation_terminal.json`
- `assets/seconddawnrp/models/item/rp_padd.json`
- `assets/seconddawnrp/textures/gui/operations_pad.png`

---

## Tech Stack
- Fabric 1.21.1
- TREnergy 4.1.0 (bundled)
- Tech Reborn + Energized Power (optional runtime)
- LuckPerms (optional — graceful fallback)
- SQLite via JDBC
- Loom 1.15.5 / Java 21