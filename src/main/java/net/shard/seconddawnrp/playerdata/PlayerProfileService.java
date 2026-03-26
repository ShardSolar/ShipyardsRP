package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.character.MedicalCondition;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.util.UUID;

/**
 * Single service for all player + character operations.
 *
 * <p>Phase 5.5 merge: CharacterService is removed. All character lifecycle
 * methods now live here alongside the existing progression methods.
 */
public class PlayerProfileService {

    private final PlayerProfileManager profileManager;
    private ProfileSyncService profileSyncService;

    public PlayerProfileService(PlayerProfileManager profileManager,
                                ProfileSyncService profileSyncService) {
        this.profileManager    = profileManager;
        this.profileSyncService = profileSyncService;
    }

    /** Replace the sync service after LuckPerms loads. */
    public void setProfileSyncService(ProfileSyncService syncService) {
        this.profileSyncService = syncService;
    }

    // ── Load / join ───────────────────────────────────────────────────────────

    public PlayerProfile getOrLoad(ServerPlayerEntity player) {
        PlayerProfile profile = profileManager.getOrLoadProfile(
                player.getUuid(), player.getGameProfile().getName());

        // Ensure characterId is always set — handles rows created before V5
        if (profile.getCharacterId() == null) {
            profile.setCharacterId(UUID.randomUUID().toString());
            profile.setCharacterCreatedAt(System.currentTimeMillis());
            profileManager.markDirty(player.getUuid());
        }

        // Remind players who haven't completed creation
        if (!profile.isCharacterCreationComplete()) {
            player.sendMessage(
                    Text.literal("[Character] Welcome! Visit the Character Creation Terminal "
                                    + "to set your name, species, and biography.")
                            .formatted(Formatting.GOLD), false);
        }

        return profile;
    }

    public PlayerProfile getLoaded(UUID playerId) {
        return profileManager.getLoadedProfile(playerId);
    }

    // ── Character creation terminal ───────────────────────────────────────────

    /**
     * Called when the player confirms their character creation form.
     * Sets name, species, bio, and seeds starting languages.
     */
    public boolean completeCreation(UUID playerUuid, String characterName,
                                    String speciesId, String bio) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        if (profile.getCharacterStatus() == CharacterStatus.DECEASED) return false;

        profile.setCharacterName(characterName);
        profile.setSpecies(speciesId);
        profile.setBio(bio);
        profileManager.markDirty(playerUuid);
        return true;
    }

    // ── Character death ───────────────────────────────────────────────────────

    /**
     * Execute character death. Called from GmCharacterCommands.
     *
     * <p>Writes a snapshot to character_profiles archive, resets character
     * fields on PlayerProfile, sets division to UNASSIGNED.
     *
     * @param transferPercent 0.0–1.0 fraction of rank points to carry forward
     */
    public void executeCharacterDeath(UUID playerUuid, float transferPercent,
                                      ServerPlayerEntity playerIfOnline,
                                      net.shard.seconddawnrp.character.CharacterArchiveRepository archive) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return;

        int transferred = (int) (profile.getRankPoints()
                * Math.max(0f, Math.min(1f, transferPercent)));

        // Write archive snapshot before resetting
        if (archive != null) {
            archive.archive(profile, transferred);
        }

        // Reset character fields — keep division/rank/points but wipe character identity
        profile.setCharacterStatus(CharacterStatus.DECEASED);
        profile.setDeceasedAt(System.currentTimeMillis());

        // After a brief beat, reset to new blank character
        profile.setCharacterId(UUID.randomUUID().toString());
        profile.setCharacterName(null);
        profile.setSpecies(null);
        profile.setBio(null);
        profile.setCharacterStatus(CharacterStatus.ACTIVE);
        profile.setDeceasedAt(null);
        profile.setActiveLongTermInjuryId(null);
        profile.setProgressionTransfer(transferred);
        profile.setCharacterCreatedAt(System.currentTimeMillis());
        profile.getKnownLanguages(); // read access — languages cleared below
        for (String lang : new java.util.ArrayList<>(profile.getKnownLanguages())) {
            profile.removeLanguage(lang);
        }

        // Move to UNASSIGNED
        profile.setDivision(Division.UNASSIGNED);
        profileManager.markDirty(playerUuid);

        if (playerIfOnline != null) {
            playerIfOnline.sendMessage(
                    Text.literal("[Character] Your character has died. Visit the Character "
                                    + "Creation Terminal to begin a new character. "
                                    + transferred + " points have been carried forward.")
                            .formatted(Formatting.DARK_RED), false);
        }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    public boolean grantLanguage(UUID playerUuid, String languageId) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.addLanguage(languageId);
        profileManager.markDirty(playerUuid);
        return true;
    }

    public boolean revokeLanguage(UUID playerUuid, String languageId) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.removeLanguage(languageId);
        profileManager.markDirty(playerUuid);
        return true;
    }

    // ── Medical conditions ────────────────────────────────────────────────────

    public boolean applyCondition(UUID playerUuid, MedicalCondition condition) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.addMedicalCondition(condition);
        return true;
    }

    public boolean removeCondition(UUID playerUuid, String conditionId) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.removeMedicalCondition(conditionId);
        return true;
    }

    public boolean clearConditions(UUID playerUuid) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.clearMedicalConditions();
        return true;
    }

    // ── Progression ───────────────────────────────────────────────────────────

    public void setDivision(ServerPlayerEntity player, Division division) {
        PlayerProfile profile = getOrLoad(player);
        profile.setDivision(division);
        profileManager.markDirty(player.getUuid());
        profileSyncService.syncDivision(player, division);
    }

    public void setProgressionPath(ServerPlayerEntity player, ProgressionPath path) {
        PlayerProfile profile = getOrLoad(player);
        profile.setProgressionPath(path);
        profileManager.markDirty(player.getUuid());
    }

    public void setRank(ServerPlayerEntity player, Rank rank) {
        PlayerProfile profile = getOrLoad(player);
        profile.setRank(rank);
        profileManager.markDirty(player.getUuid());
        profileSyncService.syncRank(player, rank);
    }

    public void addBillet(ServerPlayerEntity player, Billet billet) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.addBillet(billet)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncProfile(player, profile);
        }
    }

    public void removeBillet(ServerPlayerEntity player, Billet billet) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.removeBillet(billet)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncProfile(player, profile);
        }
    }

    public void addRankPoints(ServerPlayerEntity player, int amount) {
        PlayerProfile profile = getOrLoad(player);
        profile.addRankPoints(amount);
        profileManager.markDirty(player.getUuid());
    }

    public void setDutyStatus(ServerPlayerEntity player, DutyStatus dutyStatus) {
        PlayerProfile profile = getOrLoad(player);
        profile.setDutyStatus(dutyStatus);
        profileManager.markDirty(player.getUuid());
    }

    public void setSupervisor(ServerPlayerEntity player, UUID supervisorId) {
        PlayerProfile profile = getOrLoad(player);
        profile.setSupervisorId(supervisorId);
        profileManager.markDirty(player.getUuid());
    }

    public void addCertification(ServerPlayerEntity player, Certification certification) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.addCertification(certification)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncCertifications(player, profile.getCertifications());
        }
    }

    public void removeCertification(ServerPlayerEntity player, Certification certification) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.removeCertification(certification)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncCertifications(player, profile.getCertifications());
        }
    }

    public void syncAll(ServerPlayerEntity player) {
        PlayerProfile profile = getOrLoad(player);
        profileSyncService.syncProfile(player, profile);
    }
}