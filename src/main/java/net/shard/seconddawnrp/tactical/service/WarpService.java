package net.shard.seconddawnrp.tactical.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipClassDefinition;
import net.shard.seconddawnrp.tactical.data.ShipState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages warp capability, rolling power average, and warp speed state.
 * Warp reversion: 30-second window before drop-out when power falls below threshold.
 */
public class WarpService {

    private static final int ROLLING_WINDOW = 20; // samples
    private static final long REVERSION_WINDOW_MS = 30_000L;

    // Rolling power average per ship
    private final Map<String, LinkedList<Integer>> powerHistory = new ConcurrentHashMap<>();
    // Reversion sequence start per ship (null = not in reversion)
    private final Map<String, Long> reversionStart = new ConcurrentHashMap<>();

    /**
     * Called every encounter tick (5 seconds).
     * Updates rolling average, warpCapable, handles reversion.
     */
    public void tick(EncounterState encounter, MinecraftServer server) {
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            updateRollingAverage(ship);
            checkWarpState(encounter, ship, server);
        }
    }

    private void updateRollingAverage(ShipState ship) {
        LinkedList<Integer> history = powerHistory.computeIfAbsent(
                ship.getShipId(), k -> new LinkedList<>());
        history.addLast(ship.getPowerOutput());
        while (history.size() > ROLLING_WINDOW) history.removeFirst();
    }

    private int getRollingAverage(String shipId) {
        LinkedList<Integer> history = powerHistory.get(shipId);
        if (history == null || history.isEmpty()) return 0;
        return (int) history.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private void checkWarpState(EncounterState encounter, ShipState ship, MinecraftServer server) {
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null) return;

        int rollingAvg = getRollingAverage(ship.getShipId());
        int currentWarp = ship.getWarpSpeed();

        if (currentWarp > 0) {
            // Check if we can sustain current warp
            int required = def.getRequiredPowerForWarp(currentWarp);
            if (rollingAvg < required) {
                // Start or continue reversion sequence
                Long start = reversionStart.get(ship.getShipId());
                if (start == null) {
                    reversionStart.put(ship.getShipId(), System.currentTimeMillis());
                    notifyEngineering(server,
                            "[WARP] Power insufficient for Warp " + currentWarp
                                    + " — 30 seconds to reversion. Engineering: emergency boost required.");
                    encounter.log("[WARP] Reversion sequence started on " + ship.getRegistryName());
                } else if (System.currentTimeMillis() - start > REVERSION_WINDOW_MS) {
                    // Reversion — drop warp
                    int newWarp = currentWarp - 1;
                    ship.setWarpSpeed(Math.max(0, newWarp));
                    reversionStart.remove(ship.getShipId());
                    if (newWarp <= 0) {
                        ship.setWarpCapable(false);
                        notifyAll(server, "[WARP] " + ship.getRegistryName()
                                + " dropped to sublight — insufficient warp core output.", Formatting.YELLOW);
                    } else {
                        notifyAll(server, "[WARP] " + ship.getRegistryName()
                                + " reverted to Warp " + newWarp + ".", Formatting.YELLOW);
                    }
                    encounter.log("[WARP] Reverted to Warp " + newWarp);
                }
            } else {
                // Power is sufficient — cancel any pending reversion
                reversionStart.remove(ship.getShipId());
                ship.setWarpCapable(true);
            }
        } else {
            // Not at warp — check if we can engage
            boolean capable = rollingAvg >= def.getRequiredPowerForWarp(1);
            ship.setWarpCapable(capable);
        }
    }

    /**
     * Attempt to engage warp at the given factor.
     * Returns success message or failure reason.
     */
    public String engageWarp(ShipState ship, int warpFactor) {
        if (ship.isDestroyed()) return "Ship is destroyed.";
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null) return "No ship class definition.";

        int rollingAvg = getRollingAverage(ship.getShipId());
        int required   = def.getRequiredPowerForWarp(warpFactor);

        if (required == Integer.MAX_VALUE)
            return "Warp " + warpFactor + " not supported by " + def.getDisplayName() + ".";
        if (rollingAvg < required)
            return String.format("Insufficient power. Need %d E/tick average, have %d. Engineering: increase output.",
                    required, rollingAvg);

        ship.setWarpSpeed(warpFactor);
        ship.setWarpCapable(true);
        reversionStart.remove(ship.getShipId());
        return ship.getRegistryName() + " engaged Warp " + warpFactor + ". Engaging...";
    }

    public String dropToSublight(ShipState ship) {
        ship.setWarpSpeed(0);
        reversionStart.remove(ship.getShipId());
        return ship.getRegistryName() + " dropped to sublight.";
    }

    private void notifyEngineering(MinecraftServer server, String message) {
        if (server == null) return;
        // In full build, filter by division — for now broadcast
        notifyAll(server, message, Formatting.RED);
    }

    private void notifyAll(MinecraftServer server, String message, Formatting color) {
        if (server == null) return;
        Text text = Text.literal(message).formatted(color);
        server.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(text, false));
    }
}