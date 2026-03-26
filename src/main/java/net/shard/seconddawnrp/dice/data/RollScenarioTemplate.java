package net.shard.seconddawnrp.dice.data;

import net.shard.seconddawnrp.division.Division;

/**
 * A named scenario a GM saves for the current session.
 * Scenarios do NOT persist through server restart — by design.
 *
 * <p>Example: "Plasma Conduit Stabilization" dc:18 div:OPERATIONS
 * When called for a player, applies the division context bonus and
 * sets the DC, then requests a roll.
 */
public class RollScenarioTemplate {

    private final String name;
    private final int dc;
    private final Division division;    // null = no division context bonus
    private final int divisionBonus;    // bonus when rolling player matches division

    public RollScenarioTemplate(String name, int dc, Division division, int divisionBonus) {
        this.name          = name;
        this.dc            = dc;
        this.division      = division;
        this.divisionBonus = divisionBonus;
    }

    public String getName()         { return name; }
    public int getDc()              { return dc; }
    public Division getDivision()   { return division; }
    public int getDivisionBonus()   { return divisionBonus; }
}