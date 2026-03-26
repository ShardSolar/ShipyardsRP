package net.shard.seconddawnrp.dice.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.shard.seconddawnrp.dice.data.SessionLogEntry;
import net.shard.seconddawnrp.dice.network.OpenRpPaddS2CPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * The RP PADD item.
 *
 * Uses DataComponentTypes.CUSTOM_DATA (1.21.1 replacement for legacy NBT tag API).
 * getOrCreateNbt() / setNbt() / getNbt() / setCustomName() are all removed in 1.21.1.
 */
public class RpPaddItem extends Item {

    public static final String NBT_ROOT      = "RpPadd";
    public static final String NBT_SIGNED    = "signed";
    public static final String NBT_LOG       = "sessionLog";
    public static final String NBT_COUNT     = "entryCount";
    public static final String NBT_SUBMITTER = "submitterName";

    public RpPaddItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.success(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player))
            return TypedActionResult.pass(user.getStackInHand(hand));

        ItemStack stack = player.getStackInHand(hand);
        NbtCompound root = getRoot(stack);
        boolean signed = root.getBoolean(NBT_SIGNED);

        boolean recording = SecondDawnRP.RP_PADD_SERVICE != null
                && SecondDawnRP.RP_PADD_SERVICE.hasActiveSession(player.getUuid());
        java.util.List<String> recent = new java.util.ArrayList<>();
        int entryCount = 0;

        if (recording) {
            var sessionOpt = SecondDawnRP.RP_PADD_SERVICE.getSession(player.getUuid());
            if (sessionOpt.isPresent()) {
                var log = sessionOpt.get().getLog();
                entryCount = log.size();
                int start = Math.max(0, log.size() - 20);
                for (int i = start; i < log.size(); i++) {
                    recent.add(serializeEntry(log.get(i)));
                }
            }
        } else if (root.contains(NBT_LOG)) {
            var list = root.getList(NBT_LOG, net.minecraft.nbt.NbtElement.STRING_TYPE);
            entryCount = list.size();
            int start = Math.max(0, list.size() - 20);
            for (int i = start; i < list.size(); i++) recent.add(list.getString(i));
        }

        ServerPlayNetworking.send(player,
                new OpenRpPaddS2CPacket(recording, entryCount, signed, recent));

        return TypedActionResult.success(stack);
    }

    // ── Sign ──────────────────────────────────────────────────────────────────

    public static boolean sign(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof RpPaddItem)) continue;
            NbtCompound root = getRoot(stack);
            if (root.getBoolean(NBT_SIGNED) || !root.contains(NBT_LOG)) continue;

            root.putBoolean(NBT_SIGNED, true);
            root.putString(NBT_SUBMITTER, player.getName().getString());
            setRoot(stack, root);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("RP PADD [Signed — " + player.getName().getString() + "]")
                            .formatted(Formatting.GOLD));
            return true;
        }
        return false;
    }

    // ── Write session log ─────────────────────────────────────────────────────

    public static boolean writeSessionLog(ServerPlayerEntity player, List<SessionLogEntry> log) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof RpPaddItem)) continue;
            NbtCompound root = getRoot(stack);
            if (root.getBoolean(NBT_SIGNED)) continue;

            NbtList logList = new NbtList();
            for (SessionLogEntry entry : log) logList.add(NbtString.of(serializeEntry(entry)));
            root.put(NBT_LOG, logList);
            root.putInt(NBT_COUNT, log.size());
            setRoot(stack, root);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("RP PADD [" + log.size() + " entries — unsigned]")
                            .formatted(Formatting.AQUA));
            return true;
        }
        return false;
    }

    // ── Read helpers ──────────────────────────────────────────────────────────

    public static List<String> readLog(ItemStack stack) {
        List<String> entries = new ArrayList<>();
        NbtCompound root = getRoot(stack);
        if (!root.contains(NBT_LOG)) return entries;
        NbtList list = root.getList(NBT_LOG, NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) entries.add(list.getString(i));
        return entries;
    }

    public static boolean isSigned(ItemStack stack) {
        return getRoot(stack).getBoolean(NBT_SIGNED);
    }

    public static boolean hasLog(ItemStack stack) {
        return getRoot(stack).contains(NBT_LOG);
    }

    // ── Component API helpers (1.21.1) ────────────────────────────────────────

    private static NbtCompound getRoot(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return new NbtCompound();
        NbtCompound top = component.copyNbt();
        return top.contains(NBT_ROOT, NbtElement.COMPOUND_TYPE)
                ? top.getCompound(NBT_ROOT) : new NbtCompound();
    }

    private static void setRoot(ItemStack stack, NbtCompound root) {
        NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound top = existing != null ? existing.copyNbt() : new NbtCompound();
        top.put(NBT_ROOT, root);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(top));
    }

    // ── Display ───────────────────────────────────────────────────────────────

    private static void showLog(ServerPlayerEntity player, NbtCompound root) {
        NbtList list = root.getList(NBT_LOG, NbtElement.STRING_TYPE);
        player.sendMessage(Text.literal("── RP PADD Log ──").formatted(Formatting.GOLD), false);
        if (list.isEmpty()) {
            player.sendMessage(Text.literal("  (empty)").formatted(Formatting.GRAY), false);
        } else {
            for (int i = 0; i < list.size(); i++) {
                player.sendMessage(Text.literal("  " + list.getString(i))
                        .formatted(Formatting.WHITE), false);
            }
        }
    }

    private static void showStatus(ServerPlayerEntity player) {
        boolean recording = SecondDawnRP.RP_PADD_SERVICE != null
                && SecondDawnRP.RP_PADD_SERVICE.hasActiveSession(player.getUuid());
        if (recording) {
            int count = SecondDawnRP.RP_PADD_SERVICE.getSession(player.getUuid())
                    .map(s -> s.getLog().size()).orElse(0);
            player.sendMessage(Text.literal("[RP PADD] Recording — " + count
                            + " entries. Use /rp record stop to finalize.")
                    .formatted(Formatting.GREEN), false);
        } else {
            player.sendMessage(Text.literal(
                            "[RP PADD] Not recording. Use /rp record start to begin.")
                    .formatted(Formatting.GRAY), false);
        }
    }

    private static String serializeEntry(SessionLogEntry entry) {
        return entry.formatOffset() + " | " + entry.getCharacterName()
                + " | " + entry.getType().name() + " | " + entry.getContent();
    }
}