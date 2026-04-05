package net.shard.seconddawnrp.progression;

import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;
import java.util.UUID;

/**
 * Facade for writing and reading service record entries.
 * All career events funnel through here.
 */
public class ServiceRecordService {

    private final ServiceRecordRepository repository;

    public ServiceRecordService(ServiceRecordRepository repository) {
        this.repository = repository;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void logCommendation(UUID playerUuid, String divisionContext,
                                int points, String reason,
                                String actorUuid, String actorName) {
        repository.save(ServiceRecordEntry.create(
                playerUuid,
                points >= 0 ? ServiceRecordEntry.Type.COMMENDATION
                        : ServiceRecordEntry.Type.DEMERIT,
                points, actorUuid, actorName, reason, divisionContext));
    }

    public void logPromotion(UUID playerUuid, String divisionContext,
                             String newRank, String actorUuid, String actorName) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.PROMOTION, 0,
                actorUuid, actorName, "Promoted to " + newRank, divisionContext));
    }

    public void logDemotion(UUID playerUuid, String divisionContext,
                            String newRank, String actorUuid, String actorName) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.DEMOTION, 0,
                actorUuid, actorName, "Demoted to " + newRank, divisionContext));
    }

    public void logTransfer(UUID playerUuid, String fromDivision, String toDivision,
                            String actorUuid, String actorName) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.TRANSFER, 0,
                actorUuid, actorName,
                "Transferred from " + fromDivision + " to " + toDivision,
                toDivision));
    }

    public void logEnlistment(UUID playerUuid, String divisionContext) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.ENLISTMENT, 0,
                null, "System", "Enlisted", divisionContext));
    }

    public void logCadetEnrolled(UUID playerUuid, String divisionContext,
                                 String actorUuid, String actorName) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.CADET_ENROLLED, 0,
                actorUuid, actorName, "Enrolled in cadet programme", divisionContext));
    }

    public void logCadetGraduated(UUID playerUuid, String divisionContext,
                                  String startingRank, String actorUuid, String actorName) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.CADET_GRADUATED, 0,
                actorUuid, actorName, "Graduated — commissioned as " + startingRank,
                divisionContext));
    }

    public void logDismissal(UUID playerUuid, String divisionContext,
                             String actorUuid, String actorName, String reason) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.DISMISSED, 0,
                actorUuid, actorName,
                reason != null && !reason.isBlank() ? reason : "Dismissed",
                divisionContext));
    }

    public void logNote(UUID playerUuid, String divisionContext,
                        String actorUuid, String actorName, String note) {
        repository.save(ServiceRecordEntry.create(
                playerUuid, ServiceRecordEntry.Type.NOTE, 0,
                actorUuid, actorName, note, divisionContext));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<ServiceRecordEntry> getAll(UUID playerUuid) {
        return repository.loadForPlayer(playerUuid);
    }

    public List<ServiceRecordEntry> getCommendations(UUID playerUuid) {
        return repository.loadCommendations(playerUuid);
    }

    public List<ServiceRecordEntry> getDemerits(UUID playerUuid) {
        return repository.loadDemerits(playerUuid);
    }

    public List<ServiceRecordEntry> getCareerEvents(UUID playerUuid) {
        return repository.loadCareerEvents(playerUuid);
    }
}