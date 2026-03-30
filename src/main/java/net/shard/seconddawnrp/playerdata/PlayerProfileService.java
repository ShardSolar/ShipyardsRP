package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Single service for all player + character operations.
 *
 * <p>Phase 5.5 merge: CharacterService is removed. All character lifecycle
 * methods now live here alongside the existing progression methods.
 *
 * <p>Phase 8: MedicalCondition session cache methods renamed to match
 * PlayerProfile's updated API (addMedicalCondition → cacheMedicalCondition,
 * removeMedicalCondition → uncacheMedicalCondition, etc.).
 * setActiveLongTermInjuryId replaced by addMedicalConditionId / clearMedicalConditionIds.
 */
public class PlayerProfileService {

    private final PlayerProfileManager profileManager;
    private ProfileSyncService profileSyncService;

    public PlayerProfileService(PlayerProfileManager profileManager,
                                ProfileSyncService profileSyncService) {
        this.profileManager     = profileManager;
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

        if (profile.getCharacterId() == null) {
            profile.setCharacterId(UUID.randomUUID().toString());
            profile.setCharacterCreatedAt(System.currentTimeMillis());
            profileManager.markDirty(player.getUuid());
        }

        return profile;
    }

    public PlayerProfile getLoaded(UUID playerId) {
        return profileManager.getLoadedProfile(playerId);
    }

    // ── Character creation terminal ───────────────────────────────────────────

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

    public void executeCharacterDeath(UUID playerUuid, float transferPercent,
                                      ServerPlayerEntity playerIfOnline,
                                      net.shard.seconddawnrp.character.CharacterArchiveRepository archive) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return;

        int transferred = (int) (profile.getRankPoints()
                * Math.max(0f, Math.min(1f, transferPercent)));

        if (archive != null) {
            archive.archive(profile, transferred);
        }

        profile.setCharacterStatus(CharacterStatus.DECEASED);
        profile.setDeceasedAt(System.currentTimeMillis());

        // Reset to blank character
        profile.setCharacterId(UUID.randomUUID().toString());
        profile.setCharacterName(null);
        profile.setSpecies(null);
        profile.setBio(null);
        profile.setCharacterStatus(CharacterStatus.ACTIVE);
        profile.setDeceasedAt(null);

        // Phase 8: clear medical condition IDs (replaces setActiveLongTermInjuryId(null))
        profile.clearMedicalConditionIds();
        profile.clearMedicalConditionCache();

        profile.setProgressionTransfer(transferred);
        profile.setCharacterCreatedAt(System.currentTimeMillis());

        for (String lang : new ArrayList<>(profile.getKnownLanguages())) {
            profile.removeLanguage(lang);
        }

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

    // ── Medical condition session cache ───────────────────────────────────────
    // These operate on the in-memory cache only (MedicalCondition objects).
    // Persistence is handled by MedicalService via MedicalRepository.

    public boolean applyCondition(UUID playerUuid,
                                  net.shard.seconddawnrp.character.MedicalCondition condition) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.cacheMedicalCondition(condition);
        return true;
    }

    public boolean removeCondition(UUID playerUuid, String conditionId) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.uncacheMedicalCondition(conditionId);
        return true;
    }

    public boolean clearConditions(UUID playerUuid) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        profile.clearMedicalConditionCache();
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