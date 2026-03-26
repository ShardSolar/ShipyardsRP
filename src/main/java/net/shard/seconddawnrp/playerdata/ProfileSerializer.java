package net.shard.seconddawnrp.playerdata;

import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProfileSerializer {

    public ProfileSaveData toSaveData(PlayerProfile profile) {
        ProfileSaveData data = new ProfileSaveData();
        data.playerId      = profile.getPlayerId().toString();
        data.serviceName   = profile.getServiceName();
        data.division      = profile.getDivision().name();
        data.progressionPath = profile.getProgressionPath().name();
        data.rank          = profile.getRank().name();
        data.rankPoints    = profile.getRankPoints();
        data.dutyStatus    = profile.getDutyStatus().name();
        data.supervisorId  = profile.getSupervisorId() == null
                ? null : profile.getSupervisorId().toString();

        for (Billet b : profile.getBillets())             data.billets.add(b.name());
        for (Certification c : profile.getCertifications()) data.certifications.add(c.name());

        return data;
    }

    public PlayerProfile fromSaveData(ProfileSaveData data) {
        UUID playerId          = UUID.fromString(data.playerId);
        String serviceName     = data.serviceName == null ? "" : data.serviceName;
        Division division      = data.division == null
                ? Division.UNASSIGNED : Division.valueOf(data.division);
        ProgressionPath path   = data.progressionPath == null
                ? ProgressionPath.ENLISTED : ProgressionPath.valueOf(data.progressionPath);
        Rank rank              = data.rank == null
                ? Rank.JUNIOR_CREWMAN : Rank.valueOf(data.rank);
        DutyStatus dutyStatus  = data.dutyStatus == null
                ? DutyStatus.OFF_DUTY : DutyStatus.valueOf(data.dutyStatus);
        UUID supervisorId      = data.supervisorId == null || data.supervisorId.isBlank()
                ? null : UUID.fromString(data.supervisorId);

        Set<Billet> billets = new HashSet<>();
        if (data.billets != null)
            for (String b : data.billets) billets.add(Billet.valueOf(b));

        Set<Certification> certs = new HashSet<>();
        if (data.certifications != null)
            for (String c : data.certifications) certs.add(Certification.valueOf(c));

        return new PlayerProfile(
                playerId,
                serviceName,
                division,
                path,
                rank,
                data.rankPoints,
                0L,                          // serviceRecord — not in save data, starts at 0
                billets,
                certs,
                dutyStatus,
                supervisorId,
                // Character fields — blank, creation terminal fills these in
                UUID.randomUUID().toString(), // characterId
                null,                        // characterName
                null,                        // species
                null,                        // bio
                CharacterStatus.ACTIVE,
                List.of(),                   // knownLanguages
                false,                       // universalTranslator
                false,                       // permadeathConsent
                null,                        // activeLongTermInjuryId
                null,                        // deceasedAt
                0,                           // progressionTransfer
                System.currentTimeMillis()   // characterCreatedAt
        );
    }
}