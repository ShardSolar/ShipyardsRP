package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;

public record EditTaskC2SPacket(
        String taskId,
        String displayName,
        String description,
        String divisionName,
        int requiredAmount,
        int rewardPoints,
        boolean officerConfirmationRequired
) implements CustomPayload {

    public static final CustomPayload.Id<EditTaskC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("edit_task"));

    public static final PacketCodec<RegistryByteBuf, EditTaskC2SPacket> CODEC =
            PacketCodec.of(EditTaskC2SPacket::write, EditTaskC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(taskId);
        buf.writeString(displayName);
        buf.writeString(description);
        buf.writeString(divisionName);
        buf.writeInt(requiredAmount);
        buf.writeInt(rewardPoints);
        buf.writeBoolean(officerConfirmationRequired);
    }

    private static EditTaskC2SPacket read(RegistryByteBuf buf) {
        return new EditTaskC2SPacket(
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public Division getDivision() {
        return Division.valueOf(divisionName);
    }
}