package net.shard.seconddawnrp.playerdata;

import net.shard.seconddawnrp.character.LongTermInjury;
import net.shard.seconddawnrp.character.MedicalCondition;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;

import java.util.*;

/**
 * Single source of truth for everything about a player on Second Dawn RP.
 *
 * <p>Phase 5.5 merge: CharacterProfile fields are now inline here.
 * CharacterService is removed — all character logic lives in
 * {@link PlayerProfileService}.
 *
 * <p>Persisted via {@link net.shard.seconddawnrp.playerdata.persistence.SqlProfileRepository}.
 */
public class PlayerProfile {

    // ── Minecraft account identity ────────────────────────────────────────────

    private final UUID playerId;
    private String serviceName;         // Minecraft username

    // ── Division / progression ────────────────────────────────────────────────

    private Division division;
    private ProgressionPath progressionPath;
    private Rank rank;
    private int rankPoints;
    private long serviceRecord;         // lifetime points — never decreases
    private final Set<Billet> billets;
    private final Set<Certification> certifications;
    private DutyStatus dutyStatus;
    private UUID supervisorId;

    // ── Character identity (merged from CharacterProfile) ─────────────────────

    /** This character's own UUID — distinct from the Minecraft account UUID. */
    private String characterId;

    /** In-character name shown in chat, Roster GUI, /rp output. */
    private String characterName;

    /** Species registry key — locked after creation (GM override via command). */
    private String species;

    /** Freeform biography — editable at any time. */
    private String bio;

    /** ACTIVE, DECEASED, or SPECTATOR. */
    private CharacterStatus characterStatus;

    // ── Language ──────────────────────────────────────────────────────────────

    private final List<String> knownLanguages;

    /**
     * Bypasses all language scrambling (spoken + written).
     * Does NOT bypass Wordle for encrypted comms.
     */
    private boolean universalTranslator;

    // ── Death / injury ────────────────────────────────────────────────────────

    /** Player opted into death without per-case GM confirmation. */
    private boolean permadeathConsent;

    /** ID of active {@link LongTermInjury} in long_term_injuries table, or null. */
    private String activeLongTermInjuryId;

    /** Wall-clock timestamp of character death. Null if alive. */
    private Long deceasedAt;

    /** Points carried forward from a previous deceased character. */
    private int progressionTransfer;

    /** Wall-clock timestamp when current character was created. */
    private long characterCreatedAt;

    // ── Session-only medical conditions (not persisted) ───────────────────────

    private final List<MedicalCondition> activeMedicalConditions = new ArrayList<>();

    // ── Task state (in-memory, saved separately) ──────────────────────────────

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
            // Character fields
            String characterId,
            String characterName,
            String species,
            String bio,
            CharacterStatus characterStatus,
            List<String> knownLanguages,
            boolean universalTranslator,
            boolean permadeathConsent,
            String activeLongTermInjuryId,
            Long deceasedAt,
            int progressionTransfer,
            long characterCreatedAt
    ) {
        this.playerId             = playerId;
        this.serviceName          = serviceName;
        this.division             = division;
        this.progressionPath      = progressionPath;
        this.rank                 = rank;
        this.rankPoints           = rankPoints;
        this.serviceRecord        = serviceRecord;
        this.billets              = new HashSet<>(billets);
        this.certifications       = new HashSet<>(certifications);
        this.dutyStatus           = dutyStatus;
        this.supervisorId         = supervisorId;
        this.characterId          = characterId;
        this.characterName        = characterName;
        this.species              = species;
        this.bio                  = bio;
        this.characterStatus      = characterStatus != null ? characterStatus : CharacterStatus.ACTIVE;
        this.knownLanguages       = new ArrayList<>(knownLanguages != null ? knownLanguages : List.of());
        this.universalTranslator  = universalTranslator;
        this.permadeathConsent    = permadeathConsent;
        this.activeLongTermInjuryId = activeLongTermInjuryId;
        this.deceasedAt           = deceasedAt;
        this.progressionTransfer  = progressionTransfer;
        this.characterCreatedAt   = characterCreatedAt;
    }

    // ── Character helpers ─────────────────────────────────────────────────────

    /**
     * True if the player has completed the Character Creation Terminal.
     * A fresh profile has species = null until creation is done.
     */
    public boolean isCharacterCreationComplete() {
        return species != null && !species.isBlank();
    }

    /** The display name to use in chat and GUIs — falls back to Minecraft name. */
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

    // ── Medical conditions (session-only) ─────────────────────────────────────

    public void addMedicalCondition(MedicalCondition condition) {
        activeMedicalConditions.removeIf(c -> c.getConditionId().equals(condition.getConditionId()));
        activeMedicalConditions.add(condition);
    }

    public void removeMedicalCondition(String conditionId) {
        activeMedicalConditions.removeIf(c -> c.getConditionId().equals(conditionId));
    }

    public void clearMedicalConditions() { activeMedicalConditions.clear(); }

    public List<MedicalCondition> getActiveMedicalConditions() {
        return Collections.unmodifiableList(activeMedicalConditions);
    }

    public boolean hasCondition(String conditionId) {
        return activeMedicalConditions.stream().anyMatch(c -> c.getConditionId().equals(conditionId));
    }

    // ── Rank points ───────────────────────────────────────────────────────────

    public void addRankPoints(int amount) {
        this.rankPoints   += amount;
        this.serviceRecord += amount;   // service record always grows
    }

    // ── Getters — account ─────────────────────────────────────────────────────

    public UUID getPlayerId()               { return playerId; }
    public String getServiceName()          { return serviceName; }
    public void setServiceName(String n)    { this.serviceName = n; }

    // ── Getters — progression ─────────────────────────────────────────────────

    public Division getDivision()           { return division; }
    public void setDivision(Division d)     { this.division = d; }

    public ProgressionPath getProgressionPath()             { return progressionPath; }
    public void setProgressionPath(ProgressionPath p)       { this.progressionPath = p; }

    public Rank getRank()                   { return rank; }
    public void setRank(Rank r)             { this.rank = r; }

    public int getRankPoints()              { return rankPoints; }
    public void setRankPoints(int pts)      { this.rankPoints = pts; }

    public long getServiceRecord()          { return serviceRecord; }
    public void setServiceRecord(long sr)   { this.serviceRecord = sr; }

    public Set<Billet> getBillets()                     { return Collections.unmodifiableSet(billets); }
    public boolean hasBillet(Billet b)                  { return billets.contains(b); }
    public boolean addBillet(Billet b)                  { return billets.add(b); }
    public boolean removeBillet(Billet b)               { return billets.remove(b); }

    public Set<Certification> getCertifications()           { return Collections.unmodifiableSet(certifications); }
    public boolean hasCertification(Certification c)        { return certifications.contains(c); }
    public boolean addCertification(Certification c)        { return certifications.add(c); }
    public boolean removeCertification(Certification c)     { return certifications.remove(c); }

    public DutyStatus getDutyStatus()           { return dutyStatus; }
    public void setDutyStatus(DutyStatus d)     { this.dutyStatus = d; }

    public UUID getSupervisorId()               { return supervisorId; }
    public void setSupervisorId(UUID id)        { this.supervisorId = id; }

    // ── Getters — character ───────────────────────────────────────────────────

    public String getCharacterId()                  { return characterId; }
    public void setCharacterId(String id)           { this.characterId = id; }

    public String getCharacterName()                { return characterName; }
    public void setCharacterName(String n)          { this.characterName = n; }

    public String getSpecies()                      { return species; }
    public void setSpecies(String s)                { this.species = s; }

    public String getBio()                          { return bio; }
    public void setBio(String b)                    { this.bio = b; }

    public CharacterStatus getCharacterStatus()             { return characterStatus; }
    public void setCharacterStatus(CharacterStatus s)       { this.characterStatus = s; }

    public boolean hasUniversalTranslator()         { return universalTranslator; }
    public void setUniversalTranslator(boolean v)   { this.universalTranslator = v; }

    public boolean isPermadeathConsent()            { return permadeathConsent; }
    public void setPermadeathConsent(boolean v)     { this.permadeathConsent = v; }

    public String getActiveLongTermInjuryId()           { return activeLongTermInjuryId; }
    public void setActiveLongTermInjuryId(String id)    { this.activeLongTermInjuryId = id; }

    public Long getDeceasedAt()                     { return deceasedAt; }
    public void setDeceasedAt(Long ts)              { this.deceasedAt = ts; }

    public int getProgressionTransfer()             { return progressionTransfer; }
    public void setProgressionTransfer(int pts)     { this.progressionTransfer = pts; }

    public long getCharacterCreatedAt()             { return characterCreatedAt; }
    public void setCharacterCreatedAt(long ts)      { this.characterCreatedAt = ts; }

    // ── Task state ────────────────────────────────────────────────────────────

    public List<ActiveTask> getActiveTasks()                        { return activeTasks; }
    public void setActiveTasks(List<ActiveTask> t)                  { this.activeTasks = t != null ? t : new ArrayList<>(); }
    public List<CompletedTaskRecord> getCompletedTasks()            { return completedTasks; }
    public void setCompletedTasks(List<CompletedTaskRecord> t)      { this.completedTasks = t != null ? t : new ArrayList<>(); }
}