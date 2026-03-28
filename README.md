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

### Phase 5.25 — Terminal Designator + ComputerCraft Integration
- **Terminal Designator Tool** — designates any placed block in the world as a typed terminal. No custom blocks needed — build teams choose the aesthetic, the mod provides the function
- **Terminal types:** OPS_TERMINAL, ENGINEERING_CONSOLE, ROSTER_CONSOLE (active) + MEDICAL, SECURITY, SCIENCE, TACTICAL, MISSION, RESOURCE, LIBRARY (stubbed, activate as phases complete)
- **Colored block outlines** — server-side DustParticleEffect wireframe rendered around designated terminals when the tool is held. Color-coded per type
- **Action bar prompt** — looking at any designated terminal shows its name and "Right-click to open" above the XP bar for all players
- **Persistent JSON registry** — `config/assets/seconddawnrp/terminal_designations.json`
- **ComputerCraft integration** — optional, zero impact if CC absent. `WarpCorePeripheral`, `DegradationPeripheral`, `OpsPeripheral` expose live ship data to Lua programs via `@LuaFunction` annotations
- **DB migration:** V7 (mustang, ship_position columns, officer_slot_queue table)

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
- `/profile` — unified command replacing all old character/profile commands
- `/gm character kill` — two-step death with progression transfer percentage
- `/gm character set` — name, bio, species, permadeath overrides
- `/gm injury modify` — expiry adjustment in days
- **DB migrations:** V4 (character_profiles, long_term_injuries, rdm_flags), V5 (character columns on players, player_known_languages, service_record)

### Phase 5.5 — Career Path Infrastructure
- **Cadet track** — CADET_1 through CADET_4 ranks, division declaration gate at CADET_2, officer-approved promotion at each step
- **Graduation flow** — two-step: instructor proposes starting rank → Captain approves. Session-only pending proposals
- **Officer slot caps** — configurable per-rank maximums. Full ranks queue eligible players ordered by service record then time at rank
- **Officer progression points** — automatic point awards for administrative actions (task approval, PADD review, cert confirmation). All values JSON-configurable
- **Commendation system** — variable-point manual awards requiring written reason. Authority-gated to Commander+
- **Ship positions** — FIRST_OFFICER and SECOND_OFFICER designations. Independent of rank. Grant shipwide certification confirmation authority
- **Mustang flag** — permanent notation on PlayerProfile and Roster when a player crosses from enlisted to commissioned
- **Group tasks** — tasks with participant capacity > 1, shared progress, distributed rewards
- **Commands:** `/cadet enrol/promote/graduate/approve/status`, `/officer commend`, `/admin slots list/set/queue`, `/admin position set/clear`
- **Config files:** `cadet_config.json`, `officer_slots.json`, `officer_progression.json`

### Phase 5.5 — Roster GUI
- **Roster PAD item** — right-click to open the division roster screen. Any block can also be designated as a ROSTER_CONSOLE terminal (gold glow)
- **Two-panel layout** — scrollable member list (left) + selected member detail panel (right)
- **Member list** — online indicator dot, character name, rank abbreviation, division badge. Sorted online-first then by rank authority
- **Detail panel** — character name, Minecraft username, rank, division, progression path, ship position, rank points, service record, certifications/billets, mustang flag
- **Action buttons** — authority-gated, render only for viewers with sufficient rank:
    - **Promote / Demote** — LT_COMMANDER+ (checks slot caps, queues if full)
    - **Cadet Enrol / Promote / Propose Grad / Approve Grad** — officer level
    - **Transfer / Dismiss** — LIEUTENANT+
    - **Commend** — COMMANDER+
- **Inline input overlay** — graduation rank entry and commend reason/points input directly in the screen, no commands needed
- **Feedback bar** — action result shown at screen bottom for 4 seconds after any roster action
- **Live refresh** — server pushes updated roster data after every action, screen re-renders without closing
- Read-only view for all division members regardless of rank

### Phase 6 — Dice + RP PADD System

#### Dice Engine
- `/roll` — d20 with rank bonus, certification bonuses, demerit penalties
- `/rp [action]` — third-person narration, bold gold formatting, broadcasts to all players, captured by active PADD sessions
- `/gm rolls public/private` — toggle auto-broadcast vs hold-and-broadcast mode
- `/gm roll broadcast [player]` / `broadcastall`
- `/gm roll group [players...]` — group roll session, 60-second timeout
- `/gm dc set [value]` / `clear` / per-player override
- `/gm scenario create/call/list`
- **RollModifierConfig** — JSON at `config/assets/seconddawnrp/roll_modifiers.json`

#### RP PADD Item
- Physical item — right-click opens code-drawn GUI with recording status, live log, Start/Stop, Sign
- `/rp record start` (supports `radius:N` and `players:` options) / `stop`

#### Submission Box Block
- Accepts signed RP PADDs on right-click, saves to database, notifies online officers

#### Officer Review — Ops PADD PADS Tab
- Fifth tab on the Ops PADD showing pending submissions
- CONFIRM / DISPUTE with inline note input, generates Archive PADD item, awards officer progression points
- 7-day auto-cleanup of resolved submissions
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
| `TERMINAL_DESIGNATOR_REGISTRY` | Designated terminal block positions + types |
| `TERMINAL_DESIGNATOR_SERVICE` | Terminal interact dispatch + glow + action bar |
| `CADET_SERVICE` | Cadet rank track, graduation flow |
| `OFFICER_SLOT_SERVICE` | Slot caps, queue management, promotion notification |
| `OFFICER_PROGRESSION_SERVICE` | Automatic point awards for officer admin actions |
| `COMMENDATION_SERVICE` | Manual commendation issuance |
| `SHIP_POSITION_SERVICE` | First/Second Officer designation |
| `GROUP_TASK_SERVICE` | Group task sessions, shared progress, reward distribution |
| `ROSTER_SERVICE` | Roster data building, all roster actions |

### Database Schema
| Table | Purpose |
|---|---|
| `players` | All player + character data (V1 + V5 + V7) |
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
| `officer_slot_queue` | Players queued for promotion when rank is full (V7) |
| `schema_version` | Migration tracking |

### Critical Notes
1. **1.21.1 NBT API** — use `DataComponentTypes.CUSTOM_DATA` + `NbtComponent.of()`. `getOrCreateNbt()`, `setNbt()`, `getNbt()` are removed.
2. **BlockWithEntity subclasses** — must implement `getCodec()` returning `MapCodec`. Use `MapCodec.unit(ClassName::new)`.
3. **Command registration** — always in `onInitialize()` via `CommandRegistrationCallback`. Never inside `SERVER_STARTED`.
4. **LuckPerms block** — call `PROFILE_SERVICE.setProfileSyncService(syncService)`. Do NOT replace `PROFILE_SERVICE` instance.
5. **Payload registration** — must call `registerPayloads()` before `registerServerReceivers()` or crash on startup.
6. **TREnergy** — push-based. Use `Transaction.openOuter()` + `target.insert()` each tick, always commit.
7. **ProfileLtiCallback** — LongTermInjuryService uses this interface instead of CharacterRepository to update `activeLongTermInjuryId` on PlayerProfile.
8. **Extended screens** — screens that carry data to the client must use `ExtendedScreenHandlerFactory` (not `SimpleNamedScreenHandlerFactory`) so `getScreenOpeningData()` fires the codec. See `RosterScreenHandlerFactory` / `TerminalScreenHandlerFactory`.
9. **CC integration** — all CC classes isolated in `.cc` package. `CCPeripheralRegistry` checks `FabricLoader.isModLoaded("computercraft")` before touching any CC API. Mod loads and runs fully without CC.
10. **Tick loop placement** — per-player calls like `tickActionBarPrompt` must be INSIDE the `for (var player : ...)` loop, not after the closing brace.
11. **Java 21** — required by Minecraft 1.21.1 and CC:Tweaked 1.116+. Build target is `release = 21`.

---

## Config Files (auto-created on first boot)
- `config/assets/seconddawnrp/roll_modifiers.json` — rank and cert bonuses for dice rolls
- `config/assets/seconddawnrp/degradation_config.json` — component drain rates
- `config/assets/seconddawnrp/warp_core_config.json` — warp core parameters
- `config/assets/seconddawnrp/cadet_config.json` — cadet rank point thresholds and graduation rules
- `config/assets/seconddawnrp/officer_slots.json` — slot caps per commissioned rank
- `config/assets/seconddawnrp/officer_progression.json` — point values for officer admin actions
- `config/assets/seconddawnrp/terminal_designations.json` — registered terminal block positions (delete on world wipe)
- `config/assets/seconddawnrp/cc_programs/` — example Lua programs for CC monitors
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
- `assets/seconddawnrp/models/item/roster_pad.json`
- `assets/seconddawnrp/models/item/terminal_designator_tool.json`
- `assets/seconddawnrp/textures/gui/operations_pad.png`

---

## Tech Stack
- Fabric 1.21.1
- TREnergy 4.1.0 (bundled)
- Tech Reborn + Energized Power (optional runtime)
- LuckPerms (optional — graceful fallback)
- CC:Tweaked 1.116.0+ (optional — compile-only, graceful fallback)
- SQLite via JDBC
- Loom 1.15.5 / Java 21