package net.shard.seconddawnrp.playerdata;

import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DefaultProfileFactory {

    public PlayerProfile create(UUID playerId, String playerName) {
        return new PlayerProfile(
                playerId,
                playerName,
                Division.UNASSIGNED,
                ProgressionPath.ENLISTED,
                Rank.JUNIOR_CREWMAN,
                0,
                0L,                         // serviceRecord
                Set.of(),
                Set.of(),
                DutyStatus.OFF_DUTY,
                null,                        // supervisorId
                // Character fields — blank until creation terminal
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