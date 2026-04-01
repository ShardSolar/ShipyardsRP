package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ProfileSyncService {
    CompletableFuture<Void> syncProfile(ServerPlayerEntity player, PlayerProfile profile);

    CompletableFuture<Void> syncDivision(ServerPlayerEntity player, Division division);

    CompletableFuture<Void> syncRank(ServerPlayerEntity player, Rank rank);

    /**
     * Legacy single-billet sync hook.
     * Prefer syncBillets(...) or syncProfile(...) for correct add/remove behavior.
     */
    CompletableFuture<Void> syncBillet(ServerPlayerEntity player, Billet billet);

    CompletableFuture<Void> syncBillets(ServerPlayerEntity player, Set<Billet> billets);

    CompletableFuture<Void> syncCertifications(ServerPlayerEntity player, Set<Certification> certifications);
}