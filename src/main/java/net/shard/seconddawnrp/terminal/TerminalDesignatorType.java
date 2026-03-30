package net.shard.seconddawnrp.terminal;

/**
 * All terminal types that can be designated onto any world block.
 * Each type maps to an existing screen/handler pair and a LuckPerms permission node.
 */
public enum TerminalDesignatorType {

    // Phase 1 — complete
    OPS_TERMINAL(
            "Operations Terminal",
            "seconddawnrp.terminal.ops",
            0x4A90D9,
            true
    ),

    // Phase 4 — complete
    ENGINEERING_CONSOLE(
            "Engineering Console",
            "seconddawnrp.terminal.engineering",
            0xF5A623,
            true
    ),

    // Phase 5.5 — complete
    ROSTER_CONSOLE(
            "Roster Console",
            "seconddawnrp.terminal.roster",
            0xFFD700,
            true
    ),

    // Phase 4.75 — planned
    LIBRARY_TERMINAL(
            "Library Terminal",
            "seconddawnrp.terminal.library",
            0x9B59B6,
            false
    ),

    // Phase 8 — Medical (implemented)
    MEDICAL_CONSOLE(
            "Medical Console",
            "seconddawnrp.terminal.medical",
            0x2ECC71,
            true   // was false — activated in Phase 8
    ),

    // Phase 9 — planned
    SECURITY_CONSOLE(
            "Security Console",
            "seconddawnrp.terminal.security",
            0xE74C3C,
            false
    ),

    // Phase 10 — planned
    SCIENCE_CONSOLE(
            "Science Console",
            "seconddawnrp.terminal.science",
            0x1ABC9C,
            false
    ),

    // Phase 6.5 — planned
    MISSION_CONSOLE(
            "Mission Console",
            "seconddawnrp.terminal.mission",
            0xF39C12,
            false
    ),

    // Phase 6.5 — planned
    RESOURCE_TERMINAL(
            "Resource Terminal",
            "seconddawnrp.terminal.resource",
            0x95A5A6,
            false
    ),

    // Phase 12 — planned
    TACTICAL_CONSOLE(
            "Tactical Console",
            "seconddawnrp.terminal.tactical",
            0xC0392B,
            false
    );

    private final String displayName;
    private final String permissionNode;
    private final int glowColor;
    private final boolean implemented;

    TerminalDesignatorType(String displayName, String permissionNode,
                           int glowColor, boolean implemented) {
        this.displayName    = displayName;
        this.permissionNode = permissionNode;
        this.glowColor      = glowColor;
        this.implemented    = implemented;
    }

    public String  getDisplayName()    { return displayName; }
    public String  getPermissionNode() { return permissionNode; }
    public int     getGlowColor()      { return glowColor; }
    public boolean isImplemented()     { return implemented; }
}