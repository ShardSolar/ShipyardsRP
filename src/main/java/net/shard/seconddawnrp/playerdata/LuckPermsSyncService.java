package net.shard.seconddawnrp.playerdata;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsSyncService implements ProfileSyncService {

    private static final String DIVISION_PREFIX = "st.division.";
    private static final String RANK_PREFIX = "st.rank.";
    private static final String BILLET_PREFIX = "st.billet.";
    private static final String CERT_PREFIX = "st.cert.";

    private final LuckPerms luckPerms;
    private final LuckPermsGroupMapper groupMapper;

    public LuckPermsSyncService(LuckPerms luckPerms, LuckPermsGroupMapper groupMapper) {
        this.luckPerms = luckPerms;
        this.groupMapper = groupMapper;
    }

    public static LuckPermsSyncService create(Object luckPerms, LuckPermsGroupMapper groupMapper) {
        return new LuckPermsSyncService((LuckPerms) luckPerms, groupMapper);
    }

    @Override
    public CompletableFuture<Void> syncProfile(ServerPlayerEntity player, PlayerProfile profile) {
        if (player == null || profile == null) {
            return CompletableFuture.completedFuture(null);
        }

        return loadUser(player.getUuid()).thenAccept(user -> {
            syncDivisionInternal(user, profile.getDivision());
            syncRankInternal(user, profile.getRank());
            syncBilletsInternal(user, profile.getBillets());
            syncCertificationsInternal(user, profile.getCertifications());
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncDivision(ServerPlayerEntity player, Division division) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        return loadUser(player.getUuid()).thenAccept(user -> {
            syncDivisionInternal(user, division);
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncRank(ServerPlayerEntity player, Rank rank) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        return loadUser(player.getUuid()).thenAccept(user -> {
            syncRankInternal(user, rank);
            luckPerms.getUserManager().saveUser(user);
        });
    }

    /**
     * Compatibility shim.
     * Since billet removal cannot be represented by a single billet argument,
     * the preferred path is syncProfile(...) or syncBillets(...).
     *
     * This method only ensures the given billet exists if it is not NONE.
     */
    @Override
    public CompletableFuture<Void> syncBillet(ServerPlayerEntity player, Billet billet) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        return loadUser(player.getUuid()).thenAccept(user -> {
            if (billet != null && billet != Billet.NONE) {
                user.data().add(InheritanceNode.builder(groupMapper.getBilletGroup(billet)).build());
                luckPerms.getUserManager().saveUser(user);
            }
        });
    }

    /**
     * Preferred billet sync path: sync the full current billet set.
     */
    public CompletableFuture<Void> syncBillets(ServerPlayerEntity player, Set<Billet> billets) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        return loadUser(player.getUuid()).thenAccept(user -> {
            syncBilletsInternal(user, billets != null ? billets : Collections.emptySet());
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncCertifications(ServerPlayerEntity player, Set<Certification> certifications) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        return loadUser(player.getUuid()).thenAccept(user -> {
            syncCertificationsInternal(user, certifications != null ? certifications : Collections.emptySet());
            luckPerms.getUserManager().saveUser(user);
        });
    }

    private CompletableFuture<User> loadUser(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user != null) {
            return CompletableFuture.completedFuture(user);
        }
        return luckPerms.getUserManager().loadUser(uuid);
    }

    private void syncDivisionInternal(User user, Division division) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && inheritanceNode.getGroupName().startsWith(DIVISION_PREFIX));

        if (division != null && division != Division.UNASSIGNED) {
            user.data().add(InheritanceNode.builder(groupMapper.getDivisionGroup(division)).build());
        }
    }

    private void syncRankInternal(User user, Rank rank) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && inheritanceNode.getGroupName().startsWith(RANK_PREFIX));

        if (rank != null) {
            user.data().add(InheritanceNode.builder(groupMapper.getRankGroup(rank)).build());
        }
    }

    private void syncBilletsInternal(User user, Set<Billet> billets) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && inheritanceNode.getGroupName().startsWith(BILLET_PREFIX));

        for (Billet billet : billets) {
            if (billet != null && billet != Billet.NONE) {
                user.data().add(InheritanceNode.builder(groupMapper.getBilletGroup(billet)).build());
            }
        }
    }

    private void syncCertificationsInternal(User user, Set<Certification> certifications) {
        user.data().clear(node -> node.getKey().startsWith(CERT_PREFIX));

        for (Certification certification : certifications) {
            if (certification != null) {
                Node node = Node.builder(groupMapper.getCertificationNode(certification))
                        .value(true)
                        .build();
                user.data().add(node);
            }
        }
    }
}