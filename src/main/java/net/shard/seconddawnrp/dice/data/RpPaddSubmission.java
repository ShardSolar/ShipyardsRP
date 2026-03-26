package net.shard.seconddawnrp.dice.data;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * A submitted RP PADD stored in the database.
 */
public class RpPaddSubmission {

    public enum Status { PENDING, CONFIRMED, DISPUTED }

    private final String submissionId;
    private final UUID submitterUuid;
    private final String submitterName;
    private final long submittedAtMs;
    private final int entryCount;
    private final String logText;           // newline-separated serialized entries

    private Status status;
    private UUID reviewedByUuid;
    private Long reviewedAtMs;
    private String reviewNote;
    private String linkedTaskId;

    public RpPaddSubmission(String submissionId, UUID submitterUuid, String submitterName,
                            long submittedAtMs, int entryCount, String logText,
                            Status status, UUID reviewedByUuid, Long reviewedAtMs,
                            String reviewNote, String linkedTaskId) {
        this.submissionId   = submissionId;
        this.submitterUuid  = submitterUuid;
        this.submitterName  = submitterName;
        this.submittedAtMs  = submittedAtMs;
        this.entryCount     = entryCount;
        this.logText        = logText;
        this.status         = status;
        this.reviewedByUuid = reviewedByUuid;
        this.reviewedAtMs   = reviewedAtMs;
        this.reviewNote     = reviewNote;
        this.linkedTaskId   = linkedTaskId;
    }

    public static RpPaddSubmission createNew(UUID submitterUuid, String submitterName,
                                             List<String> entries) {
        String logText = String.join("\n", entries);
        return new RpPaddSubmission(
                UUID.randomUUID().toString(),
                submitterUuid, submitterName,
                System.currentTimeMillis(),
                entries.size(), logText,
                Status.PENDING, null, null, null, null
        );
    }

    public List<String> getEntries() {
        if (logText == null || logText.isBlank()) return List.of();
        return Arrays.asList(logText.split("\n"));
    }

    // Setters
    public void setStatus(Status s)          { this.status = s; }
    public void setReviewedByUuid(UUID u)    { this.reviewedByUuid = u; }
    public void setReviewedAtMs(Long ms)     { this.reviewedAtMs = ms; }
    public void setReviewNote(String note)   { this.reviewNote = note; }
    public void setLinkedTaskId(String id)   { this.linkedTaskId = id; }

    // Getters
    public String getSubmissionId()   { return submissionId; }
    public UUID getSubmitterUuid()    { return submitterUuid; }
    public String getSubmitterName()  { return submitterName; }
    public long getSubmittedAtMs()    { return submittedAtMs; }
    public int getEntryCount()        { return entryCount; }
    public String getLogText()        { return logText; }
    public Status getStatus()         { return status; }
    public UUID getReviewedByUuid()   { return reviewedByUuid; }
    public Long getReviewedAtMs()     { return reviewedAtMs; }
    public String getReviewNote()     { return reviewNote; }
    public String getLinkedTaskId()   { return linkedTaskId; }

    /** Short display label for the list view. */
    public String getDisplayLabel() {
        String name = submitterName != null ? submitterName : submitterUuid.toString().substring(0, 8);
        return name + " — " + entryCount + " entries";
    }

    // ── Repository ────────────────────────────────────────────────────────────

    public static final class Repository {

        private final DatabaseManager db;

        public Repository(DatabaseManager db) { this.db = db; }
        public void deleteResolvedBefore(long cutoffMs) {
            try {
                Connection c = db.getConnection();
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM rp_padd_submissions "
                                + "WHERE status != 'PENDING' AND reviewed_at_ms < ?")) {
                    ps.setLong(1, cutoffMs);
                    int deleted = ps.executeUpdate();
                    if (deleted > 0)
                        System.out.println("[SecondDawnRP] Purged " + deleted + " resolved RP PADD submissions.");
                }
            } catch (SQLException e) {
                System.err.println("[SecondDawnRP] Failed to purge old submissions: " + e.getMessage());
            }
        }
        public void save(RpPaddSubmission sub) {
            try {
                Connection c = db.getConnection();
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO rp_padd_submissions "
                                + "(submission_id, submitter_uuid, submitter_name, submitted_at_ms, "
                                + "entry_count, log_text, status, reviewed_by_uuid, reviewed_at_ms, "
                                + "review_note, linked_task_id) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                                + "ON CONFLICT(submission_id) DO UPDATE SET "
                                + "status=excluded.status, reviewed_by_uuid=excluded.reviewed_by_uuid, "
                                + "reviewed_at_ms=excluded.reviewed_at_ms, "
                                + "review_note=excluded.review_note, linked_task_id=excluded.linked_task_id")) {
                    ps.setString(1, sub.submissionId);
                    ps.setString(2, sub.submitterUuid.toString());
                    ps.setString(3, sub.submitterName);
                    ps.setLong(4,   sub.submittedAtMs);
                    ps.setInt(5,    sub.entryCount);
                    ps.setString(6, sub.logText);
                    ps.setString(7, sub.status.name());
                    setNullable(ps, 8,  sub.reviewedByUuid != null ? sub.reviewedByUuid.toString() : null);
                    if (sub.reviewedAtMs != null) ps.setLong(9, sub.reviewedAtMs); else ps.setNull(9, Types.INTEGER);
                    setNullable(ps, 10, sub.reviewNote);
                    setNullable(ps, 11, sub.linkedTaskId);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save RP PADD submission", e);
            }
        }

        public List<RpPaddSubmission> loadPending() {
            return loadByStatus("PENDING");
        }

        public List<RpPaddSubmission> loadAll() {
            return loadByStatus(null);
        }

        public Optional<RpPaddSubmission> loadById(String id) {
            try {
                Connection c = db.getConnection();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT * FROM rp_padd_submissions WHERE submission_id = ?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return Optional.empty();
                        return Optional.of(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load submission " + id, e);
            }
        }

        private List<RpPaddSubmission> loadByStatus(String status) {
            List<RpPaddSubmission> result = new ArrayList<>();
            try {
                Connection c = db.getConnection();
                String sql = status != null
                        ? "SELECT * FROM rp_padd_submissions WHERE status = ? ORDER BY submitted_at_ms DESC"
                        : "SELECT * FROM rp_padd_submissions ORDER BY submitted_at_ms DESC";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    if (status != null) ps.setString(1, status);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) result.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load submissions", e);
            }
            return result;
        }

        private RpPaddSubmission mapRow(ResultSet rs) throws SQLException {
            String reviewerStr = rs.getString("reviewed_by_uuid");
            long reviewedAtRaw = rs.getLong("reviewed_at_ms");
            Long reviewedAt = rs.wasNull() ? null : reviewedAtRaw;
            return new RpPaddSubmission(
                    rs.getString("submission_id"),
                    UUID.fromString(rs.getString("submitter_uuid")),
                    rs.getString("submitter_name"),
                    rs.getLong("submitted_at_ms"),
                    rs.getInt("entry_count"),
                    rs.getString("log_text"),
                    Status.valueOf(rs.getString("status")),
                    reviewerStr != null ? UUID.fromString(reviewerStr) : null,
                    reviewedAt,
                    rs.getString("review_note"),
                    rs.getString("linked_task_id")
            );
        }

        private void setNullable(PreparedStatement ps, int i, String v) throws SQLException {
            if (v != null) ps.setString(i, v); else ps.setNull(i, Types.VARCHAR);
        }
    }
}