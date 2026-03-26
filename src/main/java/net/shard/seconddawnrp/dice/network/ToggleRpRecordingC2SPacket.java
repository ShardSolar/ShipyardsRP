package net.shard.seconddawnrp.dice.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.item.RpPaddItem;

import java.util.Set;

/**
 * Sent client → server when the player clicks Start/Stop in the RP PADD GUI.
 */
public record ToggleRpRecordingC2SPacket(boolean startRecording) implements CustomPayload {

    public static final Id<ToggleRpRecordingC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "toggle_rp_recording"));

    public static final PacketCodec<RegistryByteBuf, ToggleRpRecordingC2SPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeBoolean(value.startRecording()),
                    buf -> new ToggleRpRecordingC2SPacket(buf.readBoolean())
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    // ── Server handler ────────────────────────────────────────────────────────

    public static void handle(ToggleRpRecordingC2SPacket packet, ServerPlayerEntity player) {
        if (packet.startRecording()) {
            if (SecondDawnRP.RP_PADD_SERVICE.hasActiveSession(player.getUuid())) {
                player.sendMessage(Text.literal(
                        "[RP PADD] Already recording.").formatted(Formatting.YELLOW), false);
                return;
            }
            // Start personal recording (no radius, no named players)
            SecondDawnRP.RP_PADD_SERVICE.startSession(player.getUuid(), 0, Set.of());
            player.sendMessage(Text.literal(
                    "[RP PADD] Recording started.").formatted(Formatting.GREEN), false);
        } else {
            if (!SecondDawnRP.RP_PADD_SERVICE.hasActiveSession(player.getUuid())) {
                player.sendMessage(Text.literal(
                        "[RP PADD] No active recording.").formatted(Formatting.RED), false);
                return;
            }
            var log = SecondDawnRP.RP_PADD_SERVICE.stopSession(player.getUuid());
            boolean written = RpPaddItem.writeSessionLog(player, log);
            if (written) {
                player.sendMessage(Text.literal("[RP PADD] Recording stopped. "
                                + log.size() + " entries saved to PADD.")
                        .formatted(Formatting.GREEN), false);
            } else {
                player.sendMessage(Text.literal("[RP PADD] Recording stopped ("
                                + log.size() + " entries) but no unsigned PADD found in hotbar.")
                        .formatted(Formatting.YELLOW), false);
            }
        }
    }
}