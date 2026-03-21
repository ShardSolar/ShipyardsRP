package net.shard.seconddawnrp.tasksystem.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class TaskTargetMatcher {

    private TaskTargetMatcher() {
    }

    public static boolean blockMatches(Block block, String targetId) {
        if (block == null || targetId == null || targetId.isBlank()) {
            return false;
        }

        Identifier blockId = Registries.BLOCK.getId(block);
        if (blockId == null) {
            return false;
        }

        String normalizedTarget = normalizeBlockTarget(targetId);
        return normalizedTarget.equals(blockId.toString());
    }

    private static String normalizeBlockTarget(String raw) {
        String value = raw.trim().toLowerCase();

        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }

        return value;
    }
}