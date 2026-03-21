package net.shard.seconddawnrp.gmevent.data;

public enum SpawnBehaviour {
    INSTANT,   // spawn all mobs at once within radius
    TIMED,     // spawn mobs at intervals until total reached
    ON_ACTIVATE // spawn only when block is activated by GM
}