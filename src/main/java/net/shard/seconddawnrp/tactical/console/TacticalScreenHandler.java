package net.shard.seconddawnrp.tactical.console;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.ShipSnapshot;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.EncounterUpdatePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen handler for the Tactical Console.
 * Holds the current encounter snapshot received from the server.
 * Updated by the client-side handler when EncounterUpdatePayload arrives.
 */
public class TacticalScreenHandler extends ScreenHandler {

    private String encounterId   = "";
    private String encounterStatus = "STANDBY";
    private List<ShipSnapshot> ships = new ArrayList<>();
    private List<String> combatLog   = new ArrayList<>();

    // UI state — which ship is selected for targeting
    private String selectedShipId = null;
    private String selectedTargetId = null;

    // Current player's ship (derived from encounter — first FRIENDLY ship for MVP)
    private ShipSnapshot playerShip = null;

    // Tick counter for last update (for staleness display)
    private long lastUpdateMs = 0;

    // Client-side constructor
    public TacticalScreenHandler(int syncId, PlayerInventory inventory) {
        super(ModScreenHandlers.TACTICAL_SCREEN, syncId);
    }

    /**
     * Called by TacticalClientHandler when an EncounterUpdatePayload arrives.
     */
    public void applyUpdate(EncounterUpdatePayload payload) {
        this.encounterId     = payload.encounterId();
        this.encounterStatus = payload.status();
        this.ships           = new ArrayList<>(payload.ships());
        this.combatLog       = new ArrayList<>(payload.recentLog());
        this.lastUpdateMs    = System.currentTimeMillis();

        // Refresh player ship reference
        this.playerShip = ships.stream()
                .filter(s -> "FRIENDLY".equals(s.faction()) && !s.destroyed())
                .findFirst()
                .orElse(null);

        // Auto-select own ship if none selected
        if (selectedShipId == null && playerShip != null) {
            selectedShipId = playerShip.shipId();
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getEncounterId()     { return encounterId; }
    public String getEncounterStatus() { return encounterStatus; }
    public List<ShipSnapshot> getShips() { return ships; }
    public List<String> getCombatLog() { return combatLog; }
    public ShipSnapshot getPlayerShip() { return playerShip; }
    public String getSelectedShipId()  { return selectedShipId; }
    public String getSelectedTargetId() { return selectedTargetId; }
    public long getLastUpdateMs()      { return lastUpdateMs; }
    public boolean isStandby()         { return "STANDBY".equals(encounterStatus) || ships.isEmpty(); }

    public void setSelectedShipId(String id)   { this.selectedShipId = id; }
    public void setSelectedTargetId(String id) { this.selectedTargetId = id; }

    public List<ShipSnapshot> getFriendlyShips() {
        return ships.stream().filter(s -> "FRIENDLY".equals(s.faction())).toList();
    }

    public List<ShipSnapshot> getHostileShips() {
        return ships.stream().filter(s -> "HOSTILE".equals(s.faction())).toList();
    }

    public ShipSnapshot getShip(String shipId) {
        return ships.stream().filter(s -> s.shipId().equals(shipId)).findFirst().orElse(null);
    }

    // ── Boilerplate ───────────────────────────────────────────────────────────

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}