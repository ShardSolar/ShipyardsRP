package net.shard.seconddawnrp.playerdata;

import net.shard.seconddawnrp.character.MedicalCondition;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.progression.ShipPosition;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;

import java.util.*;

/**
 * Single source of truth for everything about a player on Second Dawn RP.
 *
 * Phase 5.5 additions: mustang flag, shipPosition.
 * Phase 8: activeMedicalConditionIds supports multiple simultaneous conditions.
 * Downed state is session-only for now.
 */
public class PlayerProfile {

    // ── Minecraft account identity ────────────────────────────────────────────

    private final UUID playerId;
    private String serviceName;

    // ── Division / progression ────────────────────────────────────────────────

    private Division division;
    private ProgressionPath progressionPath;
    private Rank rank;
    private int rankPoints;
    private long serviceRecord;
    private final Set<Billet> billets;
    private final Set<Certification> certifications;
    private DutyStatus dutyStatus;
    private UUID supervisorId;

    // ── Career path ───────────────────────────────────────────────────────────

    private boolean mustang;
    private ShipPosition shipPosition;

    // ── Character identity ────────────────────────────────────────────────────

    private String characterId;
    private String characterName;
    private String species;
    private String bio;
    private CharacterStatus characterStatus;

    // ── Language ──────────────────────────────────────────────────────────────

    private final List<String> knownLanguages;
    private boolean universalTranslator;

    // ── Death / injury ────────────────────────────────────────────────────────

    private boolean permadeathConsent;
    private final List<String> activeMedicalConditionIds;
    private Long deceasedAt;
    private int progressionTransfer;
    private long characterCreatedAt;

    // ── Downed state (session-only) ───────────────────────────────────────────

    private boolean downed;
    private long downedAt;
    private boolean downedEventMode;

    // ── Session-only medical cache ────────────────────────────────────────────

    private final List<MedicalCondition> activeMedicalConditions = new ArrayList<>();

    // ── Task state ────────────────────────────────────────────────────────────

    private List<ActiveTask> activeTasks = new ArrayList<>();
    private List<CompletedTaskRecord> completedTasks = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlayerProfile(
            UUID playerId,
            String serviceName,
            Division division,
            ProgressionPath progressionPath,
            Rank rank,
            int rankPoints,
            long serviceRecord,
            Set<Billet> billets,
            Set<Certification> certifications,
            DutyStatus dutyStatus,
            UUID supervisorId,
            String characterId,
            String characterName,
            String species,
            String bio,
            CharacterStatus characterStatus,
            List<String> knownLanguages,
            boolean universalTranslator,
            boolean permadeathConsent,
            List<String> activeMedicalConditionIds,
            Long deceasedAt,
            int progressionTransfer,
            long characterCreatedAt,
            boolean mustang,
            ShipPosition shipPosition
    ) {
        this.playerId = playerId;
        this.serviceName = serviceName;
        this.division = division;
        this.progressionPath = progressionPath;
        this.rank = rank;
        this.rankPoints = rankPoints;
        this.serviceRecord = serviceRecord;
        this.billets = new HashSet<>(billets);
        this.certifications = new HashSet<>(certifications);
        this.dutyStatus = dutyStatus;
        this.supervisorId = supervisorId;
        this.characterId = characterId;
        this.characterName = characterName;
        this.species = species;
        this.bio = bio;
        this.characterStatus = characterStatus != null ? characterStatus : CharacterStatus.ACTIVE;
        this.knownLanguages = new ArrayList<>(knownLanguages != null ? knownLanguages : List.of());
        this.universalTranslator = universalTranslator;
        this.permadeathConsent = permadeathConsent;
        this.activeMedicalConditionIds = new ArrayList<>(
                activeMedicalConditionIds != null ? activeMedicalConditionIds : List.of());
        this.deceasedAt = deceasedAt;
        this.progressionTransfer = progressionTransfer;
        this.characterCreatedAt = characterCreatedAt;
        this.mustang = mustang;
        this.shipPosition = shipPosition != null ? shipPosition : ShipPosition.NONE;

        this.downed = false;
        this.downedAt = 0L;
        this.downedEventMode = false;
    }

    // ── Downed state ──────────────────────────────────────────────────────────

    public boolean isDowned() { return downed; }
    public void setDowned(boolean downed) { this.downed = downed; }

    public long getDownedAt() { return downedAt; }
    public void setDownedAt(long downedAt) { this.downedAt = downedAt; }

    public boolean isDownedEventMode() { return downedEventMode; }
    public void setDownedEventMode(boolean downedEventMode) { this.downedEventMode = downedEventMode; }

    // ── Character helpers ─────────────────────────────────────────────────────

    public boolean isCharacterCreationComplete() {
        return species != null && !species.isBlank();
    }

    public String getDisplayName() {
        return (characterName != null && !characterName.isBlank()) ? characterName : serviceName;
    }

    // ── Language helpers ──────────────────────────────────────────────────────

    public boolean knowsLanguage(String languageId) {
        return universalTranslator || knownLanguages.contains(languageId);
    }

    public void addLanguage(String languageId) {
        if (!knownLanguages.contains(languageId)) knownLanguages.add(languageId);
    }

    public void removeLanguage(String languageId) {
        knownLanguages.remove(languageId);
    }

    public List<String> getKnownLanguages() {
        return Collections.unmodifiableList(knownLanguages);
    }

    // ── Active medical condition IDs ──────────────────────────────────────────

    public List<String> getActiveMedicalConditionIds() {
        return Collections.unmodifiableList(activeMedicalConditionIds);
    }

    public void addMedicalConditionId(String conditionId) {
        if (!activeMedicalConditionIds.contains(conditionId)) {
            activeMedicalConditionIds.add(conditionId);
        }
    }

    public void removeMedicalConditionId(String conditionId) {
        activeMedicalConditionIds.remove(conditionId);
    }

    public void clearMedicalConditionIds() {
        activeMedicalConditionIds.clear();
    }

    public boolean hasMedicalConditionId(String conditionId) {
        return activeMedicalConditionIds.contains(conditionId);
    }

    // ── Session-only medical cache ────────────────────────────────────────────

    public void cacheMedicalCondition(MedicalCondition condition) {
        activeMedicalConditions.removeIf(c -> c.getConditionId().equals(condition.getConditionId()));
        activeMedicalConditions.add(condition);
    }

    public void uncacheMedicalCondition(String conditionId) {
        activeMedicalConditions.removeIf(c -> c.getConditionId().equals(conditionId));
    }

    public void clearMedicalConditionCache() {
        activeMedicalConditions.clear();
    }

    public List<MedicalCondition> getCachedMedicalConditions() {
        return Collections.unmodifiableList(activeMedicalConditions);
    }

    public boolean hasCachedCondition(String conditionId) {
        return activeMedicalConditions.stream().anyMatch(c -> c.getConditionId().equals(conditionId));
    }

    // ── Rank points ───────────────────────────────────────────────────────────

    public void addRankPoints(int amount) {
        this.rankPoints += amount;
        this.serviceRecord += amount;
    }

    // ── Account ───────────────────────────────────────────────────────────────

    public UUID getPlayerId() { return playerId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String n) { this.serviceName = n; }

    // ── Progression ───────────────────────────────────────────────────────────

    public Division getDivision() { return division; }
    public void setDivision(Division d) { this.division = d; }

    public ProgressionPath getProgressionPath() { return progressionPath; }
    public void setProgressionPath(ProgressionPath p) { this.progressionPath = p; }

    public Rank getRank() { return rank; }
    public void setRank(Rank r) { this.rank = r; }

    public int getRankPoints() { return rankPoints; }
    public void setRankPoints(int pts) { this.rankPoints = pts; }

    public long getServiceRecord() { return serviceRecord; }
    public void setServiceRecord(long sr) { this.serviceRecord = sr; }

    public Set<Billet> getBillets() { return Collections.unmodifiableSet(billets); }
    public boolean hasBillet(Billet b) { return billets.contains(b); }
    public boolean addBillet(Billet b) { return billets.add(b); }
    public boolean removeBillet(Billet b) { return billets.remove(b); }

    public Set<Certification> getCertifications() { return Collections.unmodifiableSet(certifications); }
    public boolean hasCertification(Certification c) { return certifications.contains(c); }
    public boolean addCertification(Certification c) { return certifications.add(c); }
    public boolean removeCertification(Certification c) { return certifications.remove(c); }

    public DutyStatus getDutyStatus() { return dutyStatus; }
    public void setDutyStatus(DutyStatus d) { this.dutyStatus = d; }

    public UUID getSupervisorId() { return supervisorId; }
    public void setSupervisorId(UUID id) { this.supervisorId = id; }

    // ── Career ────────────────────────────────────────────────────────────────

    public boolean isMustang() { return mustang; }
    public void setMustang(boolean mustang) { this.mustang = mustang; }

    public ShipPosition getShipPosition() { return shipPosition != null ? shipPosition : ShipPosition.NONE; }
    public void setShipPosition(ShipPosition pos) { this.shipPosition = pos != null ? pos : ShipPosition.NONE; }

    // ── Character ─────────────────────────────────────────────────────────────

    public String getCharacterId() { return characterId; }
    public void setCharacterId(String id) { this.characterId = id; }

    public String getCharacterName() { return characterName; }
    public void setCharacterName(String n) { this.characterName = n; }

    public String getSpecies() { return species; }
    public void setSpecies(String s) { this.species = s; }

    public String getBio() { return bio; }
    public void setBio(String b) { this.bio = b; }

    public CharacterStatus getCharacterStatus() { return characterStatus; }
    public void setCharacterStatus(CharacterStatus s) { this.characterStatus = s; }

    public boolean hasUniversalTranslator() { return universalTranslator; }
    public void setUniversalTranslator(boolean v) { this.universalTranslator = v; }

    public boolean isPermadeathConsent() { return permadeathConsent; }
    public void setPermadeathConsent(boolean v) { this.permadeathConsent = v; }

    public Long getDeceasedAt() { return deceasedAt; }
    public void setDeceasedAt(Long ts) { this.deceasedAt = ts; }

    public int getProgressionTransfer() { return progressionTransfer; }
    public void setProgressionTransfer(int pts) { this.progressionTransfer = pts; }

    public long getCharacterCreatedAt() { return characterCreatedAt; }
    public void setCharacterCreatedAt(long ts) { this.characterCreatedAt = ts; }

    // ── Task state ────────────────────────────────────────────────────────────

    public List<ActiveTask> getActiveTasks() { return activeTasks; }
    public void setActiveTasks(List<ActiveTask> t) { this.activeTasks = t != null ? t : new ArrayList<>(); }

    public List<CompletedTaskRecord> getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(List<CompletedTaskRecord> t) { this.completedTasks = t != null ? t : new ArrayList<>(); }
}