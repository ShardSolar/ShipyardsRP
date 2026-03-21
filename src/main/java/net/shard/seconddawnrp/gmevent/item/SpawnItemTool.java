package net.shard.seconddawnrp.gmevent.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.screen.SpawnItemScreenHandlerFactory;
import net.shard.seconddawnrp.gmevent.screen.SpawnItemScreenOpenData;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

public class SpawnItemTool extends Item {

    public SpawnItemTool(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (world.isClient) return TypedActionResult.success(stack);
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return TypedActionResult.pass(stack);
        if (!(world instanceof ServerWorld)) return TypedActionResult.pass(stack);

        if (!hasPermission(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("[GM] No permission to trigger events."), false);
            return TypedActionResult.fail(stack);
        }

        // Get look target position
        var hitResult = player.raycast(50, 0, false);
        var pos = net.minecraft.util.math.BlockPos.ofFloored(hitResult.getPos());

        // Build template list for the GUI
        var templates = SecondDawnRP.GM_EVENT_SERVICE.getTemplates().stream()
                .map(t -> new net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket.TemplateEntry(
                        t.getId(), t.getDisplayName(), t.getMobTypeId(),
                        t.getMaxHealth(), t.getArmor(), t.getTotalSpawnCount(),
                        t.getMaxActiveAtOnce(), t.getSpawnRadiusBlocks(),
                        t.getSpawnIntervalTicks(), t.getSpawnBehaviour().name(),
                        t.getStatusEffects()))
                .toList();

        if (templates.isEmpty()) {
            serverPlayer.sendMessage(Text.literal("[GM] No templates defined. Add to encounter_templates.json"), false);
            // Still open the GUI so the GM can see the empty state
        }

        // Read last used template from NBT for pre-selection
        String currentTemplate = readSelectedTemplate(stack);

        var data = new SpawnItemScreenOpenData(
                templates, currentTemplate,
                pos.getX(), pos.getY(), pos.getZ(),
                world.getRegistryKey().getValue().toString()
        );

        serverPlayer.openHandledScreen(new SpawnItemScreenHandlerFactory(data));
        return TypedActionResult.success(stack);
    }

    private String readSelectedTemplate(ItemStack stack) {
        var component = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        var nbt = component.copyNbt();
        return nbt.contains("SelectedTemplate") ? nbt.getString("SelectedTemplate") : null;
    }

    private boolean hasPermission(ServerPlayerEntity player) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) return false;
        return SecondDawnRP.GM_PERMISSION_SERVICE.canTriggerEvents(player, profile);
    }
}