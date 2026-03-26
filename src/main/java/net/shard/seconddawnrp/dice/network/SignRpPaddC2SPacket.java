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

/**
 * Sent client → server when the player clicks the Sign button in the RP PADD GUI.
 * Signs the first unsigned PADD with a log in the player's inventory.
 */
public record SignRpPaddC2SPacket() implements CustomPayload {

    public static final Id<SignRpPaddC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "sign_rp_padd"));

    public static final PacketCodec<RegistryByteBuf, SignRpPaddC2SPacket> CODEC =
            PacketCodec.of((value, buf) -> {}, buf -> new SignRpPaddC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static void handle(SignRpPaddC2SPacket packet, ServerPlayerEntity player) {
        boolean signed = RpPaddItem.sign(player);
        if (signed) {
            player.sendMessage(Text.literal(
                            "[RP PADD] PADD signed. Submit it at a Submission Box.")
                    .formatted(Formatting.GREEN), false);
        } else {
            player.sendMessage(Text.literal(
                            "[RP PADD] No unsigned PADD with entries found in your inventory.")
                    .formatted(Formatting.RED), false);
        }
    }
}