package net.shard.seconddawnrp.playerdata.persistence;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.*;

import java.sql.*;
import java.util.*;

public final class SqlProfileRepository implements ProfileRepository {

    private final DatabaseManager databaseManager;

    public SqlProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ── ProfileRepository ─────────────────────────────────────────────────────

    @Override
    public Optional<PlayerProfile> load(UUID playerUuid) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM players WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs, conn));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load profile for " + playerUuid, e);
        }
    }

    @Override
    public void save(PlayerProfile profile) {
        Connection conn = null;
        boolean originalAutoCommit = true;
        try {
            conn = databaseManager.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            upsertPlayer(profile, conn);
            replaceBillets(profile, conn);
            replaceCertifications(profile, conn);
            replaceLanguages(profile, conn);

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException re) { e.addSuppressed(re); } }
            throw new RuntimeException("Failed to save profile for " + profile.getPlayerId(), e);
        } finally {
            if (conn != null) { try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { throw new RuntimeException(e); } }
        }
    }

    @Override
    public void delete(UUID playerUuid) {
        Connection conn = null;
        boolean originalAutoCommit = true;
        try {
            conn = databaseManager.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            deleteBillets(playerUuid, conn);
            deleteCertifications(playerUuid, conn);
            deleteLanguages(playerUuid, conn);

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM players WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString()); ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException re) { e.addSuppressed(re); } }
            throw new RuntimeException("Failed to delete profile for " + playerUuid, e);
        } finally {
            if (conn != null) { try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { throw new RuntimeException(e); } }
        }
    }

    @Override
    public Collection<PlayerProfile> loadAll() {
        List<PlayerProfile> profiles = new ArrayList<>();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) profiles.add(mapRow(rs, conn));
            }
            return profiles;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all profiles", e);
        }
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    private void upsertPlayer(PlayerProfile p, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (player_uuid, player_name, division_id, progression_path_id, "
                        + "rank_id, rank_points, service_record, duty_status, supervisor_uuid, "
                        + "character_id, character_name, species, bio, character_status, "
                        + "universal_translator, permadeath_consent, active_long_term_injury_id, "
                        + "deceased_at, progression_transfer, character_created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(player_uuid) DO UPDATE SET "
                        + "player_name=excluded.player_name, division_id=excluded.division_id, "
                        + "progression_path_id=excluded.progression_path_id, rank_id=excluded.rank_id, "
                        + "rank_points=excluded.rank_points, service_record=excluded.service_record, "
                        + "duty_status=excluded.duty_status, supervisor_uuid=excluded.supervisor_uuid, "
                        + "character_id=excluded.character_id, character_name=excluded.character_name, "
                        + "species=excluded.species, bio=excluded.bio, "
                        + "character_status=excluded.character_status, "
                        + "universal_translator=excluded.universal_translator, "
                        + "permadeath_consent=excluded.permadeath_consent, "
                        + "active_long_term_injury_id=excluded.active_long_term_injury_id, "
                        + "deceased_at=excluded.deceased_at, "
                        + "progression_transfer=excluded.progression_transfer, "
                        + "character_created_at=excluded.character_created_at")) {

            ps.setString(1, p.getPlayerId().toString());
            ps.setString(2, p.getServiceName() != null ? p.getServiceName() : "");
            ps.setString(3, p.getDivision() == null ? Division.UNASSIGNED.name() : p.getDivision().name());
            ps.setString(4, p.getProgressionPath() == null ? ProgressionPath.ENLISTED.name() : p.getProgressionPath().name());
            ps.setString(5, p.getRank() == null ? Rank.JUNIOR_CREWMAN.name() : p.getRank().name());
            ps.setInt(6,    p.getRankPoints());
            ps.setLong(7,   p.getServiceRecord());
            ps.setString(8, p.getDutyStatus() == null ? DutyStatus.OFF_DUTY.name() : p.getDutyStatus().name());
            setNullableString(ps, 9,  p.getSupervisorId() != null ? p.getSupervisorId().toString() : null);
            setNullableString(ps, 10, p.getCharacterId());
            setNullableString(ps, 11, p.getCharacterName());
            setNullableString(ps, 12, p.getSpecies());
            setNullableString(ps, 13, p.getBio());
            ps.setString(14, p.getCharacterStatus() == null ? CharacterStatus.ACTIVE.name() : p.getCharacterStatus().name());
            ps.setInt(15,   p.hasUniversalTranslator() ? 1 : 0);
            ps.setInt(16,   p.isPermadeathConsent() ? 1 : 0);
            setNullableString(ps, 17, p.getActiveLongTermInjuryId());
            if (p.getDeceasedAt() != null) ps.setLong(18, p.getDeceasedAt()); else ps.setNull(18, Types.INTEGER);
            ps.setInt(19,   p.getProgressionTransfer());
            ps.setLong(20,  p.getCharacterCreatedAt());
            ps.executeUpdate();
        }
    }

    // ── Languages ─────────────────────────────────────────────────────────────

    private void replaceLanguages(PlayerProfile p, Connection conn) throws SQLException {
        deleteLanguages(p.getPlayerId(), conn);
        if (p.getKnownLanguages().isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_known_languages (player_uuid, language_id) VALUES (?,?)")) {
            for (String lang : p.getKnownLanguages()) {
                ps.setString(1, p.getPlayerId().toString());
                ps.setString(2, lang);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteLanguages(UUID playerUuid, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM player_known_languages WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString()); ps.executeUpdate();
        }
    }

    private List<String> loadLanguages(UUID playerUuid, Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT language_id FROM player_known_languages WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("language_id"));
            }
        }
        return result;
    }

    // ── Billets / Certifications ──────────────────────────────────────────────

    private void replaceBillets(PlayerProfile p, Connection conn) throws SQLException {
        deleteBillets(p.getPlayerId(), conn);
        if (p.getBillets().isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_billets (player_uuid, billet_id) VALUES (?,?)")) {
            for (Billet b : p.getBillets()) {
                ps.setString(1, p.getPlayerId().toString()); ps.setString(2, b.name()); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceCertifications(PlayerProfile p, Connection conn) throws SQLException {
        deleteCertifications(p.getPlayerId(), conn);
        if (p.getCertifications().isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_certifications (player_uuid, certification_id) VALUES (?,?)")) {
            for (Certification c : p.getCertifications()) {
                ps.setString(1, p.getPlayerId().toString()); ps.setString(2, c.name()); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteBillets(UUID uuid, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_billets WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString()); ps.executeUpdate();
        }
    }

    private void deleteCertifications(UUID uuid, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_certifications WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString()); ps.executeUpdate();
        }
    }

    private Set<Billet> loadBillets(UUID uuid, Connection conn) throws SQLException {
        Set<Billet> result = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT billet_id FROM player_billets WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("billet_id");
                    if (id != null && !id.isBlank()) result.add(Billet.valueOf(id));
                }
            }
        }
        return result;
    }

    private Set<Certification> loadCertifications(UUID uuid, Connection conn) throws SQLException {
        Set<Certification> result = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT certification_id FROM player_certifications WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("certification_id");
                    if (id != null && !id.isBlank()) result.add(Certification.valueOf(id));
                }
            }
        }
        return result;
    }

    // ── Row mapping ───────────────────────────────────────────────────────────

    private PlayerProfile mapRow(ResultSet rs, Connection conn) throws SQLException {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));

        // Nullable longs
        long deceasedAtRaw = rs.getLong("deceased_at");
        Long deceasedAt = rs.wasNull() ? null : deceasedAtRaw;

        return new PlayerProfile(
                playerId,
                rs.getString("player_name"),
                Division.valueOf(rs.getString("division_id") != null ? rs.getString("division_id") : Division.UNASSIGNED.name()),
                ProgressionPath.valueOf(rs.getString("progression_path_id") != null ? rs.getString("progression_path_id") : ProgressionPath.ENLISTED.name()),
                Rank.valueOf(rs.getString("rank_id") != null ? rs.getString("rank_id") : Rank.JUNIOR_CREWMAN.name()),
                rs.getInt("rank_points"),
                rs.getLong("service_record"),
                loadBillets(playerId, conn),
                loadCertifications(playerId, conn),
                DutyStatus.valueOf(rs.getString("duty_status") != null ? rs.getString("duty_status") : DutyStatus.OFF_DUTY.name()),
                nullableUuid(rs.getString("supervisor_uuid")),
                // Character fields
                rs.getString("character_id"),
                rs.getString("character_name"),
                rs.getString("species"),
                rs.getString("bio"),
                charStatus(rs.getString("character_status")),
                loadLanguages(playerId, conn),
                rs.getInt("universal_translator") == 1,
                rs.getInt("permadeath_consent") == 1,
                rs.getString("active_long_term_injury_id"),
                deceasedAt,
                rs.getInt("progression_transfer"),
                rs.getLong("character_created_at")
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value != null) ps.setString(idx, value); else ps.setNull(idx, Types.VARCHAR);
    }

    private static UUID nullableUuid(String s) {
        return (s != null && !s.isBlank()) ? UUID.fromString(s) : null;
    }

    private static CharacterStatus charStatus(String s) {
        if (s == null || s.isBlank()) return CharacterStatus.ACTIVE;
        try { return CharacterStatus.valueOf(s); } catch (IllegalArgumentException e) { return CharacterStatus.ACTIVE; }
    }
}