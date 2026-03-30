package net.shard.seconddawnrp.gmevent.service;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.character.MedicalCondition;
import net.shard.seconddawnrp.gmevent.data.EnvFireMode;
import net.shard.seconddawnrp.gmevent.data.EnvironmentalEffectEntry;
import net.shard.seconddawnrp.gmevent.repository.JsonEnvironmentalEffectRepository;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.*;

/**
 * Core service for Environmental Effect Blocks.
 *
 * <p>Each server tick:
 * <ol>
 *   <li>For each active block, find all players within radius
 *   <li>Apply configured vanilla effects and/or medical conditions
 *   <li>Track per-player linger timers and ON_ENTRY cooldowns
 *   <li>Clear effects from players who have left and whose linger has expired
 * </ol>
 *
 * <p>Phase 8: medical condition application uses the renamed cache methods
 * on PlayerProfile ({@code hasCachedCondition} / {@code cacheMedicalCondition}).
 * Full condition persistence is handled by MedicalService — the Effect Block
 * only applies session-cache conditions for RP hazard effects.
 */
public class EnvironmentalEffectService {

    private final JsonEnvironmentalEffectRepository repository;

    private final Map<String, EnvironmentalEffectEntry> entries     = new LinkedHashMap<>();
    private final Map<String, Long>                     playerEntryTimestamps = new HashMap<>();
    private final Map<String, Set<UUID>>                playersInRadius       = new HashMap<>();
    private final Map<String, Long>                     lingerExpiry          = new HashMap<>();

    private long serverTick = 0;

    public EnvironmentalEffectService(JsonEnvironmentalEffectRepository repository) {
        this.repository = repository;
    }

    public void reload() {
        entries.clear();
        for (EnvironmentalEffectEntry e : repository.loadAll()) {
            entries.put(e.getEntryId(), e);
        }
        System.out.println("[SecondDawnRP] Loaded " + entries.size()
                + " environmental effect blocks.");
    }

    public void saveAll() {
        repository.save(entries.values());
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        serverTick++;

        for (EnvironmentalEffectEntry entry : entries.values()) {
            if (!entry.isActive()) continue;

            ServerWorld world = resolveWorld(server, entry.getWorldKey());
            if (world == null) continue;

            BlockPos pos    = BlockPos.fromLong(entry.getBlockPosLong());
            double radius   = entry.getRadiusBlocks();
            Set<UUID> currentlyInRadius = new HashSet<>();

            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!player.getBlockPos().isWithinDistance(pos, radius)) continue;
                currentlyInRadius.add(player.getUuid());
                String key = entry.getEntryId() + ":" + player.getUuid();

                if (entry.getFireMode() == EnvFireMode.ON_ENTRY) {
                    Set<UUID> wasIn = playersInRadius.getOrDefault(entry.getEntryId(), Set.of());
                    if (wasIn.contains(player.getUuid())) {
                        long lastApplied = playerEntryTimestamps.getOrDefault(key, 0L);
                        if (serverTick - lastApplied < entry.getOnEntryCooldownTicks()) continue;
                    }
                    playerEntryTimestamps.put(key, serverTick);
                }

                applyEffects(player, entry);
                lingerExpiry.remove(key);
            }

            // Handle players who left radius
            Set<UUID> wasIn = playersInRadius.getOrDefault(entry.getEntryId(), Set.of());
            for (UUID uuid : wasIn) {
                if (currentlyInRadius.contains(uuid)) continue;
                String key = entry.getEntryId() + ":" + uuid;

                switch (entry.getLingerMode()) {
                    case IMMEDIATE -> {
                        clearEffectsForPlayer(server, uuid, entry);
                        lingerExpiry.remove(key);
                    }
                    case LINGER -> {
                        long expiry = lingerExpiry.getOrDefault(key, -1L);
                        if (expiry < 0) {
                            lingerExpiry.put(key, serverTick + entry.getLingerDurationTicks());
                        } else if (serverTick >= expiry) {
                            clearEffectsForPlayer(server, uuid, entry);
                            lingerExpiry.remove(key);
                        } else {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                            if (player != null) applyEffects(player, entry);
                        }
                    }
                    case PERSISTENT -> {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                        if (player != null) applyEffects(player, entry);
                    }
                }
            }

            playersInRadius.put(entry.getEntryId(), currentlyInRadius);
        }
    }

    // ── Effect application ────────────────────────────────────────────────────

    private void applyEffects(ServerPlayerEntity player, EnvironmentalEffectEntry entry) {
        // Vanilla status effects
        for (String effectStr : entry.getVanillaEffects()) {
            applyVanillaEffect(player, effectStr);
        }

        // Medical condition — apply to session cache only.
        // Phase 8: hasCachedCondition / cacheMedicalCondition replace
        // the old hasCondition / addMedicalCondition names.
        if (entry.getMedicalConditionId() != null && SecondDawnRP.PROFILE_MANAGER != null) {
            PlayerProfile profile =
                    SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
            if (profile != null
                    && !profile.hasCachedCondition(entry.getMedicalConditionId())) {
                profile.cacheMedicalCondition(new MedicalCondition(
                        entry.getMedicalConditionId(),
                        entry.getMedicalConditionSeverity(),
                        System.currentTimeMillis(),
                        "Environmental hazard at "
                                + BlockPos.fromLong(entry.getBlockPosLong())
                ));
            }
        }
    }

    private void applyVanillaEffect(ServerPlayerEntity player, String effectStr) {
        // Format: "minecraft:effect_name:amplifier:durationTicks"
        String[] parts = effectStr.split(":");
        if (parts.length < 2) return;
        int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
        int duration  = parts.length > 3 ? parseInt(parts[3], 200) : 200;

        StatusEffect effect = Registries.STATUS_EFFECT
                .get(Identifier.of(parts[0], parts[1]));
        if (effect == null) return;
        player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(effect),
                duration, amplifier, false, true));
    }

    private void clearEffectsForPlayer(MinecraftServer server, UUID uuid,
                                       EnvironmentalEffectEntry entry) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;

        for (String effectStr : entry.getVanillaEffects()) {
            String[] parts = effectStr.split(":");
            if (parts.length < 2) continue;
            StatusEffect effect = Registries.STATUS_EFFECT
                    .get(Identifier.of(parts[0], parts[1]));
            if (effect != null) player.removeStatusEffect(
                    Registries.STATUS_EFFECT.getEntry(effect));
        }
        // Medical conditions persist until treated — intentional
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public EnvironmentalEffectEntry register(String worldKey, long blockPosLong,
                                             UUID registeredBy) {
        boolean exists = entries.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong);
        if (exists) throw new IllegalStateException("Already registered at this position.");

        String id = "env_" + Long.toHexString(blockPosLong & 0xFFFFFFL)
                + "_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFL);
        EnvironmentalEffectEntry entry =
                new EnvironmentalEffectEntry(id, worldKey, blockPosLong, registeredBy);
        entries.put(id, entry);
        repository.save(entries.values());
        return entry;
    }

    public boolean unregister(String worldKey, long blockPosLong) {
        Optional<EnvironmentalEffectEntry> opt = entries.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
        if (opt.isEmpty()) return false;
        entries.remove(opt.get().getEntryId());
        repository.save(entries.values());
        return true;
    }

    public boolean toggle(String entryId, boolean active) {
        EnvironmentalEffectEntry e = entries.get(entryId);
        if (e == null) return false;
        e.setActive(active);
        repository.save(entries.values());
        return true;
    }

    public void saveEntry(EnvironmentalEffectEntry entry) {
        entries.put(entry.getEntryId(), entry);
        repository.save(entries.values());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Collection<EnvironmentalEffectEntry> getAll()         { return entries.values(); }
    public Optional<EnvironmentalEffectEntry> getById(String id) { return Optional.ofNullable(entries.get(id)); }

    public Optional<EnvironmentalEffectEntry> getByPosition(String worldKey, long posLong) {
        return entries.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == posLong)
                .findFirst();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerWorld resolveWorld(MinecraftServer server, String worldKey) {
        for (ServerWorld w : server.getWorlds())
            if (w.getRegistryKey().getValue().toString().equals(worldKey)) return w;
        return null;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}