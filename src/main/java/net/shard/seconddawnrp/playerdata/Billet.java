package net.shard.seconddawnrp.playerdata;

public enum Billet {
    NONE("none"),

    // Command
    FIRST_OFFICER("first_officer"),
    EXECUTIVE_OFFICER("executive_officer"),
    COMMANDING_OFFICER("commanding_officer"),

    // Division chiefs
    CHIEF_MEDICAL_OFFICER("chief_medical_officer"),
    CHIEF_ENGINEER("chief_engineer"),
    CHIEF_OF_SECURITY("chief_of_security"),
    CHIEF_SCIENCE_OFFICER("chief_science_officer"),
    CHIEF_OPERATIONS_OFFICER("chief_operations_officer"),
    CHIEF_TACTICAL_OFFICER("chief_tactical_officer"),

    // General
    DEPARTMENT_HEAD("department_head"),
    ASSISTANT_DEPARTMENT_HEAD("assistant_department_head"),
    WATCH_OFFICER("watch_officer"),

    // Phase 8 — Medical
    SURGEON("surgeon"),

    // Phase 5.75 — Transporter System
    // Cosmetic distinction — earned by experienced operators.
    // Does NOT gate controller access (TRANSPORTER_OPERATOR cert does).
    TRANSPORTER_CHIEF("transporter_chief");

    private final String id;

    Billet(String id) { this.id = id; }

    public String getId() { return id; }
}