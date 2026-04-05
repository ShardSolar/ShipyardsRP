package net.shard.seconddawnrp.dimension;

/**
 * Immutable data loaded from data/seconddawnrp/dimensions/[id].json
 *
 * JSON format:
 * {
 *   "dimensionId": "colony_alpha",
 *   "displayName": "Colony Alpha",
 *   "description": "A temperate Class-M colony planet.",
 *   "defaultEntryX": 0.5,
 *   "defaultEntryY": 64.0,
 *   "defaultEntryZ": 0.5,
 *   "taskPoolIsolated": true,
 *   "proximityRequired": false
 * }
 *
 * proximityRequired: Phase 12 hook. When true, the tactical map proximity
 * check must pass before this dimension is reachable. Currently ignored —
 * LocationService.proximityCheck() always returns true until Phase 12.
 */
public record LocationDefinition(
        String dimensionId,
        String displayName,
        String description,
        double defaultEntryX,
        double defaultEntryY,
        double defaultEntryZ,
        boolean taskPoolIsolated,
        boolean proximityRequired
) {
    /** The Minecraft dimension key, e.g. "seconddawnrp:colony_alpha" */
    public String dimensionKey() {
        return "seconddawnrp:" + dimensionId;
    }
}