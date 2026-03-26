package net.shard.seconddawnrp.character;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.sql.*;

/**
 * Write-only archive of deceased characters.
 *
 * <p>When a character dies, {@link net.shard.seconddawnrp.playerdata.PlayerProfileService}
 * calls {@link #archive} to preserve a permanent historical snapshot in the
 * {@code character_profiles} table before resetting the live fields on
 * {@link PlayerProfile}.
 *
 * <p>This table is append-only — records are never updated or deleted.
 * Future phases (Roster historical view, Phase 9.5) will read from it.
 */
public class CharacterArchiveRepository {

    private final DatabaseManager databaseManager;

    public CharacterArchiveRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Write a permanent snapshot of the deceased character.
     *
     * @param profile     the PlayerProfile at time of death
     * @param transferred rank points transferred to the next character
     */
    public void archive(PlayerProfile profile, int transferred) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO character_profiles ("
                            + "character_id, player_uuid, character_name, species, bio, status, "
                            + "universal_translator, permadeath_consent, active_long_term_injury_id, "
                            + "deceased_at, progression_transfer, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {

                ps.setString(1, profile.getCharacterId());
                ps.setString(2, profile.getPlayerId().toString());
                ps.setString(3, profile.getCharacterName());
                setNullable(ps, 4, profile.getSpecies());
                setNullable(ps, 5, profile.getBio());
                ps.setString(6, "DECEASED");
                ps.setInt(7,    profile.hasUniversalTranslator() ? 1 : 0);
                ps.setInt(8,    profile.isPermadeathConsent() ? 1 : 0);
                setNullable(ps, 9, profile.getActiveLongTermInjuryId());
                ps.setLong(10,  System.currentTimeMillis());
                ps.setInt(11,   transferred);
                ps.setLong(12,  profile.getCharacterCreatedAt());
                ps.executeUpdate();
            }

            // Archive languages too
            if (!profile.getKnownLanguages().isEmpty()) {
                try (PreparedStatement lps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO character_known_languages (character_id, language_id) VALUES (?,?)")) {
                    for (String lang : profile.getKnownLanguages()) {
                        lps.setString(1, profile.getCharacterId());
                        lps.setString(2, lang);
                        lps.addBatch();
                    }
                    lps.executeBatch();
                }
            }
        } catch (SQLException e) {
            // Non-fatal — log and continue, death still executes
            System.err.println("[SecondDawnRP] Failed to archive deceased character "
                    + profile.getCharacterId() + ": " + e.getMessage());
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value != null) ps.setString(idx, value); else ps.setNull(idx, Types.VARCHAR);
    }
}