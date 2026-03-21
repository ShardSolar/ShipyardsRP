# Second Dawn RP – Starship Roleplay System

Second Dawn RP is a Minecraft Fabric mod designed to power a persistent, crew-based sci-fi roleplay experience inspired by Star Trek-style gameplay.

The goal is to simulate life aboard a single capital ship or station, where players operate within divisions, complete tasks, and participate in dynamic events.

---

## 🚀 Core Features (V1)

### 🧑‍🚀 Player Systems
- Division-based gameplay (Operations, Engineering, Science, Medical, Security, Command)
- Rank progression system
- Persistent player profiles (SQL-backed)
- Task tracking (active + completed)

### 📋 Task System
- Create, assign, and manage tasks via the **Operations PADD**
- Task types:
  - Break Block
  - Collect Item
  - Visit Location
  - Manual Confirmation
- Task lifecycle:
  - Unassigned → Assigned → In Progress → Awaiting Review → Completed / Failed / Canceled
- Rewards tied to rank progression

### 🖥️ Operations PADD
Admin tool used to:
- Create tasks
- Assign tasks (player / division / public pool)
- Review and approve tasks
- Cancel / fail / return tasks
- Edit task details

### 🔐 Permission System
- Integrated with LuckPerms (optional)
- Rank-based fallback permissions
- Fine-grained control over:
  - task creation
  - assignment
  - approval
  - review actions

### 💾 Persistence
- Player profiles stored in SQL database
- Task states stored via JSON (V1)
- Ops task pool persisted separately

---

## 🧠 Design Philosophy

This mod is built around three pillars:

1. **Persistent World**
   - The ship/station continues evolving over time

2. **Division Gameplay**
   - Each division has meaningful responsibilities

3. **Live + Passive Systems**
   - Players have things to do even without staff
   - Staff can inject story through events

---

## 🛠️ Tech Stack

- Minecraft Fabric
- Java
- LuckPerms API (optional)
- SQL Database (profiles)
- JSON persistence (tasks)

---

## 📦 Current Status

V1 Progress:
- ✅ Task System (core complete)
- ✅ Operations PADD (complete)
- ✅ Permission System (complete)
- 🔄 Task Terminals (next)
- 🔄 Event System (planned)

---

## 🔜 Planned Features

- Task Terminal system (world-based task access)
- GM Event Spawn tools (block + item)
- Division-specific gameplay loops
- Engineering systems (reactor, power grid)
- Medical and science gameplay expansions

---

## 🧪 Development Notes

This project is actively in development and not yet production-ready.

Expect:
- breaking changes
- refactors
- evolving systems

---

## 🤝 Contributing

Not open for external contributions yet, but structure is being prepared for future collaboration.

---

## AI Use?
Yes, heavily due to the broad integration of each part of the mod. Deal with it.

## 📜 License

TBD
