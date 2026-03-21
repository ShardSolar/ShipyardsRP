package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

public record OpsPadRefreshS2CPacket(
        List<TaskEntry> tasks
) implements CustomPayload {

    public static final CustomPayload.Id<OpsPadRefreshS2CPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("ops_pad_refresh"));

    public static final PacketCodec<RegistryByteBuf, OpsPadRefreshS2CPacket> CODEC =
            PacketCodec.of(OpsPadRefreshS2CPacket::write, OpsPadRefreshS2CPacket::read);

    public OpsPadRefreshS2CPacket {
        tasks = List.copyOf(tasks);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeInt(tasks.size());

        for (TaskEntry entry : tasks) {
            buf.writeString(entry.taskId());
            buf.writeString(entry.title());
            buf.writeString(entry.status());
            buf.writeString(entry.assigneeLabel());
            buf.writeString(entry.divisionLabel());
            buf.writeString(entry.progressLabel());

            List<String> detailLines = entry.detailLines();
            buf.writeInt(detailLines.size());
            for (String line : detailLines) {
                buf.writeString(line);
            }
        }
    }

    private static OpsPadRefreshS2CPacket read(RegistryByteBuf buf) {
        int taskCount = buf.readInt();
        List<TaskEntry> tasks = new ArrayList<>(taskCount);

        for (int i = 0; i < taskCount; i++) {
            String taskId = buf.readString();
            String title = buf.readString();
            String status = buf.readString();
            String assigneeLabel = buf.readString();
            String divisionLabel = buf.readString();
            String progressLabel = buf.readString();

            int detailCount = buf.readInt();
            List<String> detailLines = new ArrayList<>(detailCount);
            for (int j = 0; j < detailCount; j++) {
                detailLines.add(buf.readString());
            }

            tasks.add(new TaskEntry(
                    taskId,
                    title,
                    status,
                    assigneeLabel,
                    divisionLabel,
                    progressLabel,
                    detailLines
            ));
        }

        return new OpsPadRefreshS2CPacket(tasks);
    }

    public record TaskEntry(
            String taskId,
            String title,
            String status,
            String assigneeLabel,
            String divisionLabel,
            String progressLabel,
            List<String> detailLines
    ) {
        public TaskEntry {
            detailLines = List.copyOf(detailLines);
        }
    }
}