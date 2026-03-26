package net.shard.seconddawnrp.playerdata;

/**
 * Lifecycle state of a player's current character.
 * Stored directly on {@link PlayerProfile}.
 */
public enum CharacterStatus {
    ACTIVE,
    DECEASED,
    SPECTATOR
}