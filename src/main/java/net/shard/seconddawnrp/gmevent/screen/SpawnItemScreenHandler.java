package net.shard.seconddawnrp.gmevent.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket;
import net.shard.seconddawnrp.registry.ModScreenHandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpawnItemScreenHandler extends ScreenHandler {

    private final List<GmToolRefreshS2CPacket.TemplateEntry> templates;
    private int selectedIndex;
    private int targetX, targetY, targetZ;
    private final String worldKey;

    public SpawnItemScreenHandler(int syncId, PlayerInventory playerInventory,
                                  SpawnItemScreenOpenData data) {
        super(ModScreenHandlers.SPAWN_ITEM_SCREEN, syncId);
        this.templates = new ArrayList<>(data.templates());
        this.targetX   = data.targetX();
        this.targetY   = data.targetY();
        this.targetZ   = data.targetZ();
        this.worldKey  = data.worldKey();
        this.selectedIndex = 0;

        if (data.currentTemplateId() != null) {
            for (int i = 0; i < templates.size(); i++) {
                if (templates.get(i).id().equals(data.currentTemplateId())) {
                    selectedIndex = i; break;
                }
            }
        }
    }

    public List<GmToolRefreshS2CPacket.TemplateEntry> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public GmToolRefreshS2CPacket.TemplateEntry getSelectedTemplate() {
        if (templates.isEmpty() || selectedIndex >= templates.size()) return null;
        return templates.get(selectedIndex);
    }

    public int getSelectedIndex()       { return selectedIndex; }
    public void setSelectedIndex(int i) {
        if (i >= 0 && i < templates.size()) selectedIndex = i;
    }

    public int getTargetX()    { return targetX; }
    public int getTargetY()    { return targetY; }
    public int getTargetZ()    { return targetZ; }
    public String getWorldKey(){ return worldKey; }

    public void replaceTemplates(List<GmToolRefreshS2CPacket.TemplateEntry> newTemplates) {
        String currentId = getSelectedTemplate() != null ? getSelectedTemplate().id() : null;
        templates.clear();
        templates.addAll(newTemplates);
        if (currentId != null) {
            for (int i = 0; i < templates.size(); i++) {
                if (templates.get(i).id().equals(currentId)) { selectedIndex = i; return; }
            }
        }
        selectedIndex = 0;
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}