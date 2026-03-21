package net.shard.seconddawnrp.gmevent.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.EncounterTemplate;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket;
import net.shard.seconddawnrp.gmevent.screen.SpawnConfigScreenHandlerFactory;
import net.shard.seconddawnrp.gmevent.screen.SpawnConfigScreenOpenData;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;

public class SpawnBlockConfigTool extends Item {

    public SpawnBlockConfigTool(Settings settings) {
        super(settings);
    }

    // ── Right-click air — cycle through templates ─────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (!(player instanceof ServerPlayerEntity serverPlayer))
            return TypedActionResult.pass(stack);
        if (!hasPermission(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("[GM] No permission."), false);
            return TypedActionResult.fail(stack);
        }

        List<EncounterTemplate> templates = SecondDawnRP.GM_EVENT_SERVICE.getTemplates();
        if (templates.isEmpty()) {
            serverPlayer.sendMessage(Text.literal("[GM] No templates defined."), false);
            return TypedActionResult.pass(stack);
        }

        String current = readSelectedTemplate(stack);
        int currentIndex = -1;
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).getId().equals(current)) { currentIndex = i; break; }
        }

        int nextIndex = (currentIndex + 1) % templates.size();
        String nextId = templates.get(nextIndex).getId();
        writeSelectedTemplate(stack, nextId);

        serverPlayer.sendMessage(Text.literal(
                "[GM] Template selected: " + templates.get(nextIndex).getDisplayName()
                        + " [" + nextId + "]"), false);
        return TypedActionResult.success(stack);
    }

    // ── Right-click block — register + open GUI ───────────────────────────────
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockPos pos = context.getBlockPos();

        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer))
            return ActionResult.SUCCESS;
        if (!hasPermission(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("[GM] No permission."), false);
            return ActionResult.FAIL;
        }
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.FAIL;

        // Sneak + right-click = remove spawn block
        if (player.isSneaking()) {
            boolean removed = SecondDawnRP.GM_EVENT_SERVICE
                    .removeSpawnBlock(serverWorld, pos);
            serverPlayer.sendMessage(Text.literal(
                    removed ? "[GM] Spawn block removed at " + pos.toShortString()
                            : "[GM] No spawn block here."), false);
            return ActionResult.SUCCESS;
        }

        // Auto-register the block with the currently selected template
        String selectedTemplate = readSelectedTemplate(context.getStack());
        var existing = SecondDawnRP.GM_EVENT_SERVICE
                .findSpawnBlock(serverWorld, pos).orElse(null);

        if (selectedTemplate != null && !selectedTemplate.isBlank()) {
            SecondDawnRP.GM_EVENT_SERVICE.registerSpawnBlock(
                    serverWorld, pos, selectedTemplate,
                    existing != null ? existing.getLinkedTaskId() : null);
            serverPlayer.sendMessage(Text.literal(
                    "[GM] Block registered with template: " + selectedTemplate
                            + " at " + pos.toShortString()), false);
        } else if (existing == null) {
            serverPlayer.sendMessage(Text.literal(
                    "[GM] No template selected. Right-click air to cycle templates first."), false);
            return ActionResult.FAIL;
        }

        // Open config GUI
        var templateEntries = SecondDawnRP.GM_EVENT_SERVICE.getTemplates().stream()
                .map(t -> new GmToolRefreshS2CPacket.TemplateEntry(
                        t.getId(), t.getDisplayName(), t.getMobTypeId(),
                        t.getMaxHealth(), t.getArmor(), t.getTotalSpawnCount(),
                        t.getMaxActiveAtOnce(), t.getSpawnRadiusBlocks(),
                        t.getSpawnIntervalTicks(), t.getSpawnBehaviour().name(),
                        t.getStatusEffects()))
                .toList();

        // Re-fetch after potential registration
        var entry = SecondDawnRP.GM_EVENT_SERVICE
                .findSpawnBlock(serverWorld, pos).orElse(null);

        var data = new SpawnConfigScreenOpenData(
                templateEntries,
                pos.getX(), pos.getY(), pos.getZ(),
                world.getRegistryKey().getValue().toString(),
                entry != null ? entry.getTemplateId() : selectedTemplate,
                entry != null ? entry.getLinkedTaskId() : null);

        serverPlayer.openHandledScreen(new SpawnConfigScreenHandlerFactory(data));
        return ActionResult.SUCCESS;
    }

    // ── NBT helpers ───────────────────────────────────────────────────────────
    private String readSelectedTemplate(ItemStack stack) {
        var component = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        var nbt = component.copyNbt();
        return nbt.contains("SelectedTemplate") ? nbt.getString("SelectedTemplate") : null;
    }

    private void writeSelectedTemplate(ItemStack stack, String templateId) {
        var nbt = new net.minecraft.nbt.NbtCompound();
        var existing = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (existing != null) nbt = existing.copyNbt();
        nbt.putString("SelectedTemplate", templateId);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt));
    }

    private boolean hasPermission(ServerPlayerEntity player) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) return false;
        return SecondDawnRP.GM_PERMISSION_SERVICE.canConfigureSpawnBlocks(player, profile);
    }
}