package net.shard.seconddawnrp.tasksystem.network;

import java.util.List;

public record AdminTaskSyncEntry(
        String taskId,
        String title,
        String status,
        String assigneeLabel,
        String divisionLabel,
        String progressLabel,
        List<String> detailLines
) {
}