# Second Dawn RP

A custom Fabric mod for Minecraft 1.21.1 implementing a persistent science fiction roleplay operating system. Players crew a single ship or station, organized into divisions with ranks, driven by a task-based gameplay loop and live GM events.

---

## Overview

Second Dawn RP is not a typical Minecraft mod. It is a roleplay engine — a suite of interconnected systems that turn a Minecraft server into a persistent sci-fi crew simulation. The mod handles player profiles, division assignments, rank progression, task creation and tracking, in-world terminals, GM event tools, and engineering maintenance — all wired together through a strict layered architecture.

**Platform:** Minecraft Java 1.21.1 — Fabric  
**Mod ID:** `seconddawnrp`  
**Setting:** Original IP — Star Trek inspired universe  
**Crew capacity:** 300 players aboard a single vessel  

---

## Architecture

All systems follow a strict layered contract. No layer may skip a level.

```
UI / Commands / Events
        ↓
    Services
        ↓
  Repositories
        ↓
Storage (JSON or SQLite)
```

The following static singletons on `SecondDawnRP` are the authoritative instances. Never instantiate these yourself — always access through the static fields.

| Field | Type | Purpose |
|---|---|---|
| `PROFILE_MANAGER` | `PlayerProfileManager` | Load, cache, save player profiles |
| `PROFILE_SERVICE` | `PlayerProfileService` | Profile lifecycle + LuckPerms sync |
| `TASK_SERVICE` | `TaskService` | Full task lifecycle |
| `TASK_REWARD_SERVICE` | `TaskRewardService` | Grant rank points on completion |
| `TASK_PERMISSION_SERVICE` | `TaskPermissionService` | Permission checks for task operations |
| `TERMINAL_MANAGER` | `TaskTerminalManager` | Terminal registration and interaction |
| `DATABASE_MANAGER` | `DatabaseManager` | SQLite connection |
| `PERMISSION_SERVICE` | `PermissionService` | LuckPerms wrapper |

---

## Divisions

Players belong to one of six divisions. Division membership is stored in the player profile and synced with LuckPerms groups.

| Division | Identity | Primary Loop |
|---|---|---|
| Command | Leadership, diplomacy | Direct crew, approve tasks, manage events |
| Operations | Logistics, coordination | Create/assign tasks, manage pool |
| Engineering | Ship systems, maintenance | Monitor reactor, repair components |
| Medical | Crew survival | Scan, diagnose, treat, follow-up care |
| Science | Research, exploration | Investigate anomalies, run experiments |
| Security | Awareness, threat response | Monitor cameras, scan, respond to threats |

---

## Task System

The primary gameplay loop. Tasks are created by officers, distributed through terminals or direct assignment, completed through world interaction, and reviewed before rewards are granted.

### Lifecycle

```
CREATE → ASSIGN → ACCEPT → IN_PROGRESS → AWAITING_REVIEW → COMPLETED
                                       ↘ FAILED / CANCELED / RETURNED
```

### Objective Types

| Type | Trigger | Target Format |
|---|---|---|
| `BREAK_BLOCK` | Block break event | `minecraft:block_id` or `block_id` |
| `COLLECT_ITEM` | Inventory delta (per tick) | `minecraft:item_id` or `item_id` |
| `VISIT_LOCATION` | Position check (every 20 ticks) | `x,y,z` or `x,y,z,radius` |
| `MANUAL_CONFIRM` | Player PADD submit button | Any descriptive string |

### Commands

```
/task assign <player> <taskId>        — Officer assigns task directly
/task accept <taskId>                 — Player self-accepts from pool
/task list [player]                   — View active tasks
/task progress [player]               — View task progress
/task approve <player> <taskId>       — Officer approves manual confirm task
/task completed [player]              — View completed task history
/task history [player]                — Alias for completed
/task debug advance <player> <id> <n> — Dev: advance progress by n
```

### Task Templates (JSON)

Static task definitions loaded from `data/seconddawnrp/tasks/`. These are read-only reference tasks. Dynamic tasks are created through the Ops PADD and stored in the database.

```json
{
  "id": "repair_conduit",
  "displayName": "Repair Plasma Conduit",
  "description": "Locate and repair the damaged conduit on Deck 4.",
  "division": "ENGINEERING",
  "objectiveType": "BREAK_BLOCK",
  "targetId": "minecraft:damaged_block",
  "requiredAmount": 5,
  "rewardPoints": 25,
  "officerConfirmationRequired": false
}
```

---

## Task Terminals

World blocks registered by an admin using the **Task Terminal Tool** item. Players right-click to open the terminal GUI and accept available tasks.

### Terminal Types

| Type | Shows |
|---|---|
| `PUBLIC_BOARD` | Tasks with status `PUBLIC` |
| `DIVISION_BOARD` | `UNASSIGNED` tasks filtered by allowed divisions |

### Admin Tool Controls

| Action | Result |
|---|---|
| Right-click air | Cycle terminal type |
| Sneak + right-click air | Cycle division filter |
| Right-click block | Register terminal with current config |
| Sneak + right-click block | Remove terminal |

---

## Items

| Item | ID | Purpose |
|---|---|---|
| Task PADD | `task_pad` | Player's personal mission display |
| Operations PADD | `operations_pad` | Officer task management interface |
| Task Terminal Tool | `task_terminal_tool` | Admin tool for registering terminals |

---

## Storage

| Data | Backend | Location |
|---|---|---|
| Player profiles | SQLite | `config/seconddawnrp/seconddawnrp.db` → `players` table |
| Active tasks | SQLite | `player_active_tasks` table |
| Completed tasks | SQLite | `player_completed_tasks` table |
| Ops task pool | SQLite | `ops_task_pool` table |
| Task terminals | SQLite | `task_terminals` table |
| Task templates | JSON (read-only) | `data/seconddawnrp/tasks/` |
| JSON backups | JSON | `config/assets/seconddawnrp/` |

### Schema Version

Current schema version: **2**

| Version | Changes |
|---|---|
| 1 | players, player_billets, player_certifications, player_active_tasks, player_completed_tasks |
| 2 | ops_task_pool, task_terminals |

---

## Dependencies

- **Fabric API** `0.116.9+1.21.1`
- **LuckPerms** `5.4.140` — optional, degrades gracefully to `NoOpProfileSyncService`
- **SQLite JDBC** — bundled, no external install needed
- **Lithium** — performance, compatible

---

## Package Structure

```
net.shard.seconddawnrp
├── SecondDawnRP.java                  Mod initializer, static singletons
├── SecondDawnRPClient.java            Client screen registration
├── database/
│   ├── DatabaseManager                SQLite connection manager (auto-reconnect)
│   ├── DatabaseConfig                 JDBC URL builder
│   ├── DatabaseBootstrap              Runs migrations on startup
│   └── DatabaseMigrations             Versioned schema migrations
├── divison/
│   └── Division                       Enum: COMMAND, OPERATIONS, ENGINEERING,
│                                            SCIENCE, MEDICAL, SECURITY
├── playerdata/
│   ├── PlayerProfile                  Per-player data model
│   ├── PlayerProfileManager           In-memory cache + load/save
│   ├── PlayerProfileService           Login/logout lifecycle
│   ├── LuckPermsSyncService           Syncs division/rank to LP groups
│   ├── PermissionService              LP permission check wrapper
│   └── persistence/
│       ├── ProfileRepository          Interface
│       └── SqlProfileRepository       SQLite implementation
├── tasksystem/
│   ├── command/
│   │   └── TaskCommands               /task command tree
│   ├── data/
│   │   ├── ActiveTask                 Runtime task state per player
│   │   ├── CompletedTaskRecord        Immutable completion record
│   │   ├── OpsTaskPoolEntry           Dynamic pool task entry
│   │   ├── TaskTemplate               Static task definition
│   │   ├── TaskObjectiveType          Enum: BREAK_BLOCK, COLLECT_ITEM,
│   │   │                                    VISIT_LOCATION, MANUAL_CONFIRM
│   │   ├── OpsTaskStatus              Enum: UNASSIGNED → COMPLETED
│   │   └── TaskAssignmentSource       Enum: ADMIN, OFFICER, SELF
│   ├── event/
│   │   ├── TaskEventRegistrar         Registers all task trigger listeners
│   │   ├── BlockBreakTaskListener     BREAK_BLOCK trigger
│   │   ├── CollectItemTaskListener    COLLECT_ITEM trigger (tick-based delta)
│   │   └── VisitLocationTaskListener  VISIT_LOCATION trigger (tick-based)
│   ├── loader/
│   │   └── TaskJsonLoader             Loads static templates from data pack
│   ├── network/
│   │   ├── ModNetworking              C2S/S2C packet registration + handlers
│   │   ├── CreateTaskC2SPacket
│   │   ├── AssignTaskC2SPacket
│   │   ├── ReviewTaskActionC2SPacket
│   │   ├── EditTaskC2SPacket
│   │   ├── AcceptTerminalTaskC2SPacket
│   │   ├── SubmitManualConfirmC2SPacket
│   │   └── OpsPadRefreshS2CPacket
│   ├── pad/
│   │   ├── TaskPadScreen              Player PADD client screen
│   │   ├── TaskPadScreenHandler       Player PADD screen handler
│   │   ├── TaskPadItem                Player PADD item
│   │   ├── OperationsPadScreen        Ops PADD client screen
│   │   ├── AdminTaskScreenHandler     Ops PADD screen handler
│   │   └── OperationsPadItem          Ops PADD item
│   ├── registry/
│   │   └── TaskRegistry               In-memory static template registry
│   ├── repository/
│   │   ├── TaskStateRepository        Interface
│   │   ├── SqlTaskStateRepository     SQLite implementation
│   │   ├── JsonTaskStateRepository    JSON backup implementation
│   │   ├── OpsTaskPoolRepository      Interface
│   │   ├── SqlOpsTaskPoolRepository   SQLite implementation
│   │   └── JsonOpsTaskPoolRepository  JSON backup implementation
│   ├── service/
│   │   ├── TaskService                Core task lifecycle logic
│   │   ├── TaskRewardService          Rank point grants
│   │   └── TaskPermissionService      Permission checks
│   ├── terminal/
│   │   ├── TaskTerminalManager        Registration + interaction + GUI open
│   │   ├── TaskTerminalEntry          Terminal data model
│   │   ├── TaskTerminalToolItem       Admin config item
│   │   ├── TerminalType               Enum: PUBLIC_BOARD, DIVISION_BOARD
│   │   ├── TerminalScreen             Terminal GUI client screen
│   │   ├── TerminalScreenHandler      Terminal GUI screen handler
│   │   ├── TerminalScreenOpenData     Packet data for screen open
│   │   ├── TerminalScreenHandlerFactory
│   │   ├── TerminalInteractListener   UseBlockCallback handler
│   │   ├── TaskTerminalRepository     Interface
│   │   ├── SqlTerminalRepository      SQLite implementation
│   │   └── JsonTaskTerminalRepository JSON backup implementation
│   └── util/
│       └── TaskTargetMatcher          Block/item/location matching logic
└── registry/
    ├── ModItems                        Item registration
    └── ModScreenHandlers               Screen handler registration
```

---

## Resource Files

```
src/main/resources/assets/seconddawnrp/
├── textures/
│   ├── gui/
│   │   ├── task_pad.png               Player PADD GUI (380×190 in 512×256)
│   │   ├── operations_pad.png         Ops PADD GUI (420×210 in 512×256)
│   │   └── terminal.png               Terminal GUI (380×190 in 512×256)
│   └── item/
│       └── task_terminal_tool.png     Tool item texture (16×16)
└── models/item/
    ├── task_pad.json
    ├── operations_pad.json
    └── task_terminal_tool.json

src/main/resources/data/seconddawnrp/
└── tasks/                             Static task template JSON files
```

---

## Development Roadmap

| Phase | Status | Description |
|---|---|---|
| 1 — Core gameplay | ✅ Complete | Profiles, divisions, full task system, PADDs, terminals, triggers, textures |
| 2 — SQL persistence | ✅ Complete | All repositories migrated to SQLite, JSON kept as backup |
| 3 — GM event system | 🔧 In Progress | Spawn blocks, spawn item, encounter templates, event-linked tasks |
| 4 — Engineering degradation | 📋 Planned | Component marking, health drain, auto tasks, repair interaction |
| 5 — Warp core | 📋 Planned | M/ARA multiblock, reactor states, fuel system, fault events |
| 6 — Medical systems | 📋 Planned | Injury system, downed state, gurney, tricorder, treatment tools |
| 7 — Security systems | 📋 Planned | Camera network, sensor network, alert system, containment |
| 8 — Science & polish | 🔮 Future | Research terminals, anomaly system, rank progression, certifications |

---

## Known Limitations

- `CollectItemTaskListener` uses inventory snapshot delta (tick-based) — items consumed by crafting affect the count. A mixin-based approach is planned for V2.
- Task approval requires the assigned player to be online. Offline approval is not supported.
- `VisitLocationTaskListener` checks every 20 ticks — adequate for most cases but imprecise for tight radius requirements.

---

## Contributing

This is a solo development project. Architecture rules are non-negotiable:

1. UI / Commands / Events must never call repositories directly
2. Services must never access Minecraft client APIs
3. Every new system gets a repository interface before any implementation
4. `PlayerProfileManager`, `TaskService`, `ProfileRepository`, and `TaskStateRepository` must never be refactored without full impact analysis
