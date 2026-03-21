package net.shard.seconddawnrp.gmevent.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.gmevent.data.SpawnBehaviour;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket;
import net.shard.seconddawnrp.registry.ModScreenHandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpawnConfigScreenHandler extends ScreenHandler {

    public enum ConfigTab { CONFIG, POOL }

    private final List<GmToolRefreshS2CPacket.TemplateEntry> templates;
    private final int blockX, blockY, blockZ;
    private final String worldKey;

    private int selectedTemplateIndex = 0;
    private ConfigTab selectedTab = ConfigTab.CONFIG;

    // Editable fields — mirror of template but mutable
    private String editId = "";
    private String editDisplayName = "";
    private String editMobTypeId = "";
    private double editMaxHealth = 20.0;
    private double editArmor = 0.0;
    private int editTotalCount = 1;
    private int editMaxActive = 1;
    private int editRadius = 8;
    private int editInterval = 60;
    private SpawnBehaviour editBehaviour = SpawnBehaviour.INSTANT;
    private String editLinkedTask = "";

    // POOL tab fields
    private String poolTaskName = "";
    private String poolTaskDesc = "";
    private String poolDivision = "OPERATIONS";

    public SpawnConfigScreenHandler(int syncId, PlayerInventory playerInventory,
                                    SpawnConfigScreenOpenData data) {
        super(ModScreenHandlers.SPAWN_CONFIG_SCREEN, syncId);
        this.templates = new ArrayList<>(data.templates());
        this.blockX    = data.blockX();
        this.blockY    = data.blockY();
        this.blockZ    = data.blockZ();
        this.worldKey  = data.worldKey();
        this.editLinkedTask = data.linkedTaskId() != null ? data.linkedTaskId() : "";

        // Pre-select current template if set
        if (data.currentTemplateId() != null) {
            for (int i = 0; i < templates.size(); i++) {
                if (templates.get(i).id().equals(data.currentTemplateId())) {
                    selectedTemplateIndex = i;
                    break;
                }
            }
        }
        loadSelectedTemplate();
    }

    public void loadSelectedTemplate() {
        if (templates.isEmpty() || selectedTemplateIndex >= templates.size()) return;
        var t = templates.get(selectedTemplateIndex);
        editId          = t.id();
        editDisplayName = t.displayName();
        editMobTypeId   = t.mobTypeId();
        editMaxHealth   = t.maxHealth();
        editArmor       = t.armor();
        editTotalCount  = t.totalSpawnCount();
        editMaxActive   = t.maxActiveAtOnce();
        editRadius      = t.spawnRadiusBlocks();
        editInterval    = t.spawnIntervalTicks();
        try { editBehaviour = SpawnBehaviour.valueOf(t.spawnBehaviour()); }
        catch (Exception e) { editBehaviour = SpawnBehaviour.INSTANT; }
        editEffects = new ArrayList<>(t.statusEffects());
    }

    private List<String> editEffects = new ArrayList<>();

    public List<String> getEditEffects()          { return editEffects; }
    public void setEditEffects(List<String> e)    { this.editEffects = new ArrayList<>(e); }

    public void replaceTemplates(List<GmToolRefreshS2CPacket.TemplateEntry> newTemplates) {
        String currentId = getSelectedTemplate() != null ? getSelectedTemplate().id() : null;
        templates.clear();
        templates.addAll(newTemplates);
        if (currentId != null) {
            for (int i = 0; i < templates.size(); i++) {
                if (templates.get(i).id().equals(currentId)) { selectedTemplateIndex = i; return; }
            }
        }
        if (!templates.isEmpty()) selectedTemplateIndex = 0;
        loadSelectedTemplate();
    }

    public List<GmToolRefreshS2CPacket.TemplateEntry> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public GmToolRefreshS2CPacket.TemplateEntry getSelectedTemplate() {
        if (templates.isEmpty() || selectedTemplateIndex >= templates.size()) return null;
        return templates.get(selectedTemplateIndex);
    }

    public int getSelectedTemplateIndex()        { return selectedTemplateIndex; }
    public void setSelectedTemplateIndex(int i)  {
        if (i >= 0 && i < templates.size()) { selectedTemplateIndex = i; loadSelectedTemplate(); }
    }

    public ConfigTab getSelectedTab()            { return selectedTab; }
    public void setSelectedTab(ConfigTab tab)    { this.selectedTab = tab; }

    public int getBlockX()   { return blockX; }
    public int getBlockY()   { return blockY; }
    public int getBlockZ()   { return blockZ; }
    public String getWorldKey() { return worldKey; }

    // Edit field accessors
    public String getEditId()              { return editId; }
    public void setEditId(String v)        { editId = v; }
    public String getEditDisplayName()     { return editDisplayName; }
    public void setEditDisplayName(String v){ editDisplayName = v; }
    public String getEditMobTypeId()       { return editMobTypeId; }
    public void setEditMobTypeId(String v) { editMobTypeId = v; }
    public double getEditMaxHealth()       { return editMaxHealth; }
    public void setEditMaxHealth(double v) { editMaxHealth = Math.max(1, v); }
    public double getEditArmor()           { return editArmor; }
    public void setEditArmor(double v)     { editArmor = Math.max(0, v); }
    public int getEditTotalCount()         { return editTotalCount; }
    public void setEditTotalCount(int v)   { editTotalCount = Math.max(1, v); }
    public int getEditMaxActive()          { return editMaxActive; }
    public void setEditMaxActive(int v)    { editMaxActive = Math.max(1, v); }
    public int getEditRadius()             { return editRadius; }
    public void setEditRadius(int v)       { editRadius = Math.max(1, v); }
    public int getEditInterval()           { return editInterval; }
    public void setEditInterval(int v)     { editInterval = Math.max(1, v); }
    public SpawnBehaviour getEditBehaviour(){ return editBehaviour; }
    public void setEditBehaviour(SpawnBehaviour b) { this.editBehaviour = b; }
    public void cycleEditBehaviour()       {
        SpawnBehaviour[] vals = SpawnBehaviour.values();
        editBehaviour = vals[(editBehaviour.ordinal() + 1) % vals.length];
    }

    public String getEditLinkedTask()          { return editLinkedTask; }
    public void setEditLinkedTask(String v)    { editLinkedTask = v; }
    public String getPoolTaskName()            { return poolTaskName; }
    public void setPoolTaskName(String v)      { poolTaskName = v; }
    public String getPoolTaskDesc()            { return poolTaskDesc; }
    public void setPoolTaskDesc(String v)      { poolTaskDesc = v; }
    public String getPoolDivision()            { return poolDivision; }
    public void setPoolDivision(String v)      { poolDivision = v; }

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}