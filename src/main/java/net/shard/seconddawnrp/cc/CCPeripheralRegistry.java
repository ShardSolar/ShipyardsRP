package net.shard.seconddawnrp.cc;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Registers all SecondDawnRP ComputerCraft peripherals on SERVER_STARTED.
 * CC is entirely optional — silently skips if absent.
 */
public class CCPeripheralRegistry {

    private static boolean initialized = false;

    public static void register(MinecraftServer server) {
        if (initialized) return;

        if (!FabricLoader.getInstance().isModLoaded("computercraft")) {
            System.out.println("[SecondDawnRP] ComputerCraft not detected — CC integration skipped.");
            return;
        }

        try {
            doRegister(server);
            initialized = true;
            System.out.println("[SecondDawnRP] ComputerCraft peripheral API registered successfully.");
        } catch (Throwable t) {
            System.out.println("[SecondDawnRP] CC detected but peripheral registration failed — continuing without CC.");
            t.printStackTrace();
        }
    }

    private static void doRegister(MinecraftServer server) {
        registerWarpCorePeripheral(server);
        registerDegradationPeripheral(server);
        registerOpsPeripheral(server);
        registerTacticalPeripheral(server);
    }

    // ── Warp Core ─────────────────────────────────────────────────────────────

    private static void registerWarpCorePeripheral(MinecraftServer server) {
        try {
            PeripheralLookup.get().registerForBlockEntity(
                    (blockEntity, direction) -> {
                        if (blockEntity.getWorld() == null) return null;
                        String worldKey = blockEntity.getWorld()
                                .getRegistryKey().getValue().toString();
                        long packedPos = blockEntity.getPos().asLong();
                        return SecondDawnRP.WARP_CORE_SERVICE
                                .getByPosition(worldKey, packedPos)
                                .map(entry -> new WarpCorePeripheral(entry.getEntryId(), server))
                                .orElse(null);
                    },
                    net.shard.seconddawnrp.registry.ModBlocks.WARP_CORE_CONTROLLER_ENTITY
            );
            System.out.println("[SecondDawnRP] CC: WarpCorePeripheral registered.");
        } catch (Exception e) {
            System.out.println("[SecondDawnRP] CC: Failed to register WarpCorePeripheral — " + e.getMessage());
        }
    }

    // ── Degradation ───────────────────────────────────────────────────────────

    private static void registerDegradationPeripheral(MinecraftServer server) {
        SecondDawnRP.CC_DEGRADATION_PERIPHERAL = new DegradationPeripheral(server);
        System.out.println("[SecondDawnRP] CC: DegradationPeripheral ready.");
    }

    // ── Ops ───────────────────────────────────────────────────────────────────

    private static void registerOpsPeripheral(MinecraftServer server) {
        SecondDawnRP.CC_OPS_PERIPHERAL = new OpsPeripheral(server);
        System.out.println("[SecondDawnRP] CC: OpsPeripheral ready.");
    }

    // ── Tactical ─────────────────────────────────────────────────────────────

    private static void registerTacticalPeripheral(MinecraftServer server) {
        // TacticalPeripheral is a static utility class — no instance needed.
        // CC programs call it via the peripheral name "tactical" on any
        // TacticalConsoleBlock that is adjacent to a CC computer.
        // Full block-entity peripheral lookup added in Phase 12.1 when
        // TacticalConsoleBlock gains a BlockEntity. For MVP, Tactical data
        // is accessible via the static methods directly from Lua programs
        // using peripheral.find("tactical").
        System.out.println("[SecondDawnRP] CC: TacticalPeripheral ready (static mode).");
    }
}