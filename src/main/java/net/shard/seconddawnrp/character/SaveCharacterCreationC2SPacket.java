package net.shard.seconddawnrp.character;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

/**
 * Sent client → server when the player confirms their character creation form.
 */
public record SaveCharacterCreationC2SPacket(
        String characterName,
        String speciesId,
        String bio
) implements CustomPayload {

    public static final Id<SaveCharacterCreationC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "save_character_creation"));

    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_BIO_LENGTH  = 512;

    public static final PacketCodec<RegistryByteBuf, SaveCharacterCreationC2SPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.characterName());
                        buf.writeString(value.speciesId());
                        buf.writeString(value.bio());
                    },
                    buf -> new SaveCharacterCreationC2SPacket(
                            buf.readString(), buf.readString(), buf.readString())
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    // ── Server-side handler ───────────────────────────────────────────────────

    public static void handle(SaveCharacterCreationC2SPacket packet, ServerPlayerEntity player) {
        String name    = packet.characterName().trim();
        String species = packet.speciesId().trim();
        String bio     = packet.bio().trim();

        // Validation
        if (name.isBlank()) {
            player.sendMessage(Text.literal("[Character] Name cannot be blank.").formatted(Formatting.RED), false);
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            player.sendMessage(Text.literal("[Character] Name too long (max " + MAX_NAME_LENGTH + ").").formatted(Formatting.RED), false);
            return;
        }
        if (species.isBlank()) {
            player.sendMessage(Text.literal("[Character] You must select a species.").formatted(Formatting.RED), false);
            return;
        }
        if (!SecondDawnRP.SPECIES_REGISTRY.exists(species)) {
            player.sendMessage(Text.literal("[Character] Unknown species: " + species).formatted(Formatting.RED), false);
            return;
        }
        if (bio.length() > MAX_BIO_LENGTH) {
            player.sendMessage(Text.literal("[Character] Bio too long (max " + MAX_BIO_LENGTH + ").").formatted(Formatting.RED), false);
            return;
        }

        // Check species lock — species cannot change after first set (GM override only)
        PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
        if (profile != null
                && profile.getSpecies() != null
                && !profile.getSpecies().isBlank()
                && !profile.getSpecies().equals(species)) {
            player.sendMessage(Text.literal(
                            "[Character] Species is locked after creation. A GM can override this.")
                    .formatted(Formatting.RED), false);
            return;
        }

        // Apply via PROFILE_SERVICE
        boolean success = SecondDawnRP.PROFILE_SERVICE.completeCreation(
                player.getUuid(), name, species, bio);

        if (success) {
            // Seed starting languages from species registry
            SecondDawnRP.SPECIES_REGISTRY.get(species).ifPresent(def -> {
                for (String langId : def.getStartingLanguages()) {
                    SecondDawnRP.PROFILE_SERVICE.grantLanguage(player.getUuid(), langId);
                }
            });

            player.sendMessage(
                    Text.literal("[Character] Character created: ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(name).formatted(Formatting.GOLD))
                            .append(Text.literal(" (" + species + ")").formatted(Formatting.GRAY)),
                    false
            );
        } else {
            player.sendMessage(Text.literal("[Character] Failed to save. Contact an admin.").formatted(Formatting.RED), false);
        }
    }
}