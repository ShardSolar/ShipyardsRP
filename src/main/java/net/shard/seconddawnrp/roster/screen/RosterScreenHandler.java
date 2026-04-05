package net.shard.seconddawnrp.roster.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.roster.data.RosterEntry;
import net.shard.seconddawnrp.roster.data.RosterOpenData;
import net.shard.seconddawnrp.roster.data.ServiceRecordEntryDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RosterScreenHandler extends ScreenHandler {

    private List<RosterEntry> members;
    private String divisionName;
    private int viewerAuthority;
    private int selectedIndex = -1;
    private String feedbackMessage = "";
    private long feedbackExpiry = 0;
    private Map<String, List<ServiceRecordEntryDto>> serviceRecords;

    public RosterScreenHandler(int syncId, PlayerInventory inventory, RosterOpenData data) {
        super(ModScreenHandlers.ROSTER_SCREEN, syncId);
        this.members         = new ArrayList<>(data.members());
        this.divisionName    = data.divisionName();
        this.viewerAuthority = data.viewerAuthority();
        this.serviceRecords  = data.serviceRecords();
        if (!members.isEmpty()) selectedIndex = 0;
    }

    // ── Data access ───────────────────────────────────────────────────────────

    public List<RosterEntry> getMembers()    { return Collections.unmodifiableList(members); }
    public String getDivisionName()          { return divisionName; }
    public int    getViewerAuthority()       { return viewerAuthority; }
    public int    getSelectedIndex()         { return selectedIndex; }

    public void setSelectedIndex(int i) {
        if (i >= 0 && i < members.size()) selectedIndex = i;
    }

    public RosterEntry getSelected() {
        if (selectedIndex < 0 || selectedIndex >= members.size()) return null;
        return members.get(selectedIndex);
    }

    public List<ServiceRecordEntryDto> getServiceRecords(String playerUuidStr) {
        if (serviceRecords == null) return List.of();
        return serviceRecords.getOrDefault(playerUuidStr, List.of());
    }

    public void applyRefresh(RosterOpenData data, String feedback) {
        this.members         = new ArrayList<>(data.members());
        this.divisionName    = data.divisionName();
        this.viewerAuthority = data.viewerAuthority();
        this.serviceRecords  = data.serviceRecords();
        if (selectedIndex >= members.size()) {
            selectedIndex = members.isEmpty() ? -1 : 0;
        }
        if (feedback != null && !feedback.isBlank()) {
            this.feedbackMessage = feedback;
            this.feedbackExpiry  = System.currentTimeMillis() + 4000;
        }
    }

    public String getFeedbackMessage() {
        if (System.currentTimeMillis() > feedbackExpiry) return "";
        return feedbackMessage;
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    public boolean canManageRanks()   { return viewerAuthority >= 7; }
    public boolean canManageMembers() { return viewerAuthority >= 6; }
    public boolean canCommend()       { return viewerAuthority >= 8; }
    public boolean isAdmin()          { return viewerAuthority >= 99; }

    // ── Boilerplate ───────────────────────────────────────────────────────────

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}