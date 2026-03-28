package net.shard.seconddawnrp.progression;

/**
 * Ship-wide command positions — assigned by Captain or admin.
 * Independent of rank. Stored on PlayerProfile.
 *
 * First and Second Officer can confirm any certification on the ship
 * regardless of division. Both require LT_COMMANDER rank minimum.
 */
public enum ShipPosition {
    NONE,
    FIRST_OFFICER,
    SECOND_OFFICER
}