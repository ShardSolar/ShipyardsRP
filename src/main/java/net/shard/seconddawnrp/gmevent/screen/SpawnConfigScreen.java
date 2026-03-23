package net.shard.seconddawnrp.gmevent.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.GmSkill;
import net.shard.seconddawnrp.gmevent.data.SpawnBehaviour;
import net.shard.seconddawnrp.gmevent.network.*;
import net.shard.seconddawnrp.gmevent.screen.SpawnConfigScreenHandler.ConfigTab;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket.TemplateEntry;
import net.shard.seconddawnrp.division.Division;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SpawnConfigScreen extends HandledScreen<SpawnConfigScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/spawn_block_config_gui.png");

    private static final int TEX_W = 512, TEX_H = 256;
    private static final int GUI_W = 420, GUI_H = 210;

    // Tabs
    private static final int CONFIG_TAB_X = 20,  CONFIG_TAB_Y = 25, CONFIG_TAB_W = 100, CONFIG_TAB_H = 18;
    private static final int POOL_TAB_X   = 130, POOL_TAB_Y   = 25, POOL_TAB_W   = 100, POOL_TAB_H   = 18;

    // List panel
    private static final int LIST_X = 14, LIST_Y = 60, LIST_W = 180, LIST_H = 126, ROW_H = 22;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    // Config panel
    private static final int CFG_X = 204, CFG_Y = 46, CFG_W = 202;
    private static final int FIELD_H = 13;
    private static final int FIELD_GAP = 14;

    // Field Y positions — absolute from CFG_Y
    private static final int FY_NAME    = CFG_Y + 2;
    private static final int FY_MOB     = CFG_Y + 16;
    private static final int FY_HEALTH  = CFG_Y + 30;
    private static final int FY_ARMOR   = CFG_Y + 44;
    private static final int FY_COUNT   = CFG_Y + 58;
    private static final int FY_MAX_ACT = CFG_Y + 72;
    private static final int FY_BEHAV   = CFG_Y + 86;
    private static final int FY_EFFECTS = CFG_Y + 100;
    private static final int FY_TASK    = CFG_Y + 114;

    // Buttons — pushed below field area
    private static final int ACT_BTN_X = GUI_W - 130, ACT_BTN_Y = GUI_H - 30,
            ACT_BTN_W = 116, ACT_BTN_H = 16;
    private static final int SAVE_BTN_X = 14, SAVE_BTN_Y = GUI_H - 30,
            SAVE_BTN_W = 116, SAVE_BTN_H = 16;

    // Colours
    private static final int C_ACCENT  = 0xFFb42828;
    private static final int C_ACCENT2 = 0xFFdc5028;
    private static final int C_GOLD    = 0xFFa07820;
    private static final int C_TEXT    = 0xFFe8d8d8;
    private static final int C_DIM     = 0xFF784040;
    private static final int C_LABEL   = 0xFFff6060;
    private static final int C_HOVER   = 0x30FF2020;

    // All vanilla hostile/neutral mobs
    private static final List<String> MOB_LIST = List.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
            "minecraft:cave_spider", "minecraft:creeper", "minecraft:enderman",
            "minecraft:blaze", "minecraft:wither_skeleton", "minecraft:witch",
            "minecraft:zombie_villager", "minecraft:husk", "minecraft:stray",
            "minecraft:drowned", "minecraft:phantom", "minecraft:pillager",
            "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker",
            "minecraft:slime", "minecraft:magma_cube", "minecraft:ghast",
            "minecraft:hoglin", "minecraft:piglin", "minecraft:piglin_brute",
            "minecraft:zoglin", "minecraft:zombified_piglin", "minecraft:warden",
            "minecraft:endermite", "minecraft:silverfish", "minecraft:vex",
            "minecraft:bee", "minecraft:wolf", "minecraft:polar_bear",
            "minecraft:llama", "minecraft:panda", "minecraft:villager",
            "minecraft:iron_golem", "minecraft:snow_golem"
    );

    private static final List<String> VANILLA_EFFECTS = List.of(
            "minecraft:slowness:1", "minecraft:slowness:2",
            "minecraft:weakness:1", "minecraft:weakness:2",
            "minecraft:poison:1",   "minecraft:poison:2",
            "minecraft:wither:1",   "minecraft:wither:2",
            "minecraft:strength:1", "minecraft:strength:2",
            "minecraft:speed:1",    "minecraft:speed:2",
            "minecraft:resistance:1","minecraft:resistance:2",
            "minecraft:fire_resistance:1",
            "minecraft:glowing:1"
    );

    // Dropdown state
    private enum Dropdown { NONE, MOB, BEHAVIOUR, EFFECTS }
    private Dropdown openDropdown = Dropdown.NONE;
    private int dropdownScrollOffset = 0;
    private static final int DD_VISIBLE = 6;
    private static final int DD_ROW_H   = 11;

    // Combined effects list (vanilla + skills)
    private final List<String> allEffects = new ArrayList<>();

    // Focused text field
    private enum TextField { NONE, NAME, HEALTH, ARMOR, TASK, POOL_NAME, POOL_DESC }
    private TextField focusedField = TextField.NONE;

    // String buffers for numeric fields
    private String healthBuffer = "";
    private String armorBuffer  = "";

    private int listScrollOffset = 0;
    private int mouseX = 0, mouseY = 0;

    public SpawnConfigScreen(SpawnConfigScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth  = GUI_W;
        this.backgroundHeight = GUI_H;
        this.playerInventoryTitleY = 10000;
        buildEffectsList();
    }

    private void buildEffectsList() {
        allEffects.clear();
        allEffects.addAll(VANILLA_EFFECTS);
        for (GmSkill skill : GmSkill.values()) {
            allEffects.add(skill.toStorageKey());
        }
    }

    @Override protected void init() {
        super.init();
        titleX = 0; titleY = 0;
        // Sync buffers from handler
        healthBuffer = String.valueOf((int) handler.getEditMaxHealth());
        armorBuffer  = String.valueOf((int) handler.getEditArmor());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX; this.mouseY = mouseY;
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        // Draw dropdowns on top of everything
        drawOpenDropdown(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        context.drawTexture(TEXTURE, x, y, 0, 0, GUI_W, GUI_H, TEX_W, TEX_H);
        drawTabs(context, x, y);
        drawTemplateList(context, x, y, mouseX, mouseY);
        if (handler.getSelectedTab() == ConfigTab.CONFIG) drawConfigPanel(context, x, y, mouseX, mouseY);
        else drawPoolPanel(context, x, y);
        drawButtons(context, x, y, mouseX, mouseY);
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {}

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private void drawTabs(DrawContext context, int x, int y) {
        boolean configActive = handler.getSelectedTab() == ConfigTab.CONFIG;
        drawTabLabel(context, "CONFIG", x+CONFIG_TAB_X, y+CONFIG_TAB_Y,
                CONFIG_TAB_W, CONFIG_TAB_H, configActive);
        drawTabLabel(context, "POOL", x+POOL_TAB_X, y+POOL_TAB_Y,
                POOL_TAB_W, POOL_TAB_H, !configActive);
    }

    private void drawTabLabel(DrawContext context, String text, int bx, int by,
                              int bw, int bh, boolean active) {
        if (active) context.fill(bx, by, bx+bw, by+bh, 0x20FF2020);
        int tw = textRenderer.getWidth(text);
        context.drawText(textRenderer, text, bx+(bw-tw)/2, by+(bh-8)/2+1,
                active ? C_ACCENT : C_DIM, false);
    }

    // ── Template list ─────────────────────────────────────────────────────────
    private void drawTemplateList(DrawContext context, int x, int y, int mx, int my) {
        List<TemplateEntry> templates = handler.getTemplates();
        clampScroll(templates.size());

        context.enableScissor(x+LIST_X, y+LIST_Y, x+LIST_X+LIST_W, y+LIST_Y+LIST_H);
        if (templates.isEmpty()) {
            context.drawText(textRenderer, "No templates", x+LIST_X+4, y+LIST_Y+4, C_DIM, false);
        } else {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int di = i + listScrollOffset;
                if (di >= templates.size()) break;
                int rx = x+LIST_X, ry = y+LIST_Y+(i*ROW_H);
                boolean sel = di == handler.getSelectedTemplateIndex();
                boolean hov = inside(mx, my, rx+2, ry+1, LIST_W-4, ROW_H-2);
                context.fill(rx+2, ry+1, rx+LIST_W-2, ry+ROW_H-1,
                        sel ? 0x30FF2020 : hov ? 0x18FF2020 : 0x08000000);
                if (sel) context.fill(rx+2, ry+1, rx+4, ry+ROW_H-1, C_ACCENT2);
                TemplateEntry t = templates.get(di);
                context.drawText(textRenderer, trim(t.displayName(), 18),
                        rx+8, ry+4, sel ? C_TEXT : C_DIM, false);
                context.drawText(textRenderer, trim(t.mobTypeId(), 20),
                        rx+8, ry+13, 0xFF886666, false);
            }
        }
        context.disableScissor();
        drawScrollArrows(context, x+LIST_X, y+LIST_Y, LIST_W, LIST_H,
                listScrollOffset, templates.size(), VISIBLE_ROWS);
    }

    // ── Config panel ──────────────────────────────────────────────────────────
    private void drawConfigPanel(DrawContext context, int x, int y, int mx, int my) {
        int tx = x+CFG_X+4;

        drawTextField(context, tx, y+FY_NAME,   CFG_W-8, "NAME",
                withCursor(handler.getEditDisplayName(), focusedField == TextField.NAME),
                focusedField == TextField.NAME);

        // Mob — dropdown trigger
        drawDropdownField(context, tx, y+FY_MOB, CFG_W-8, "MOB",
                trim(handler.getEditMobTypeId(), 22),
                openDropdown == Dropdown.MOB, mx, my);

        // Health — typed numeric
        drawTextField(context, tx, y+FY_HEALTH, CFG_W-8, "HP",
                withCursor(healthBuffer, focusedField == TextField.HEALTH),
                focusedField == TextField.HEALTH);

        // Armor — typed numeric
        drawTextField(context, tx, y+FY_ARMOR,  CFG_W-8, "ARMOR",
                withCursor(armorBuffer, focusedField == TextField.ARMOR),
                focusedField == TextField.ARMOR);

        // Count — click +/-
        drawClickField(context, tx, y+FY_COUNT,   CFG_W-8, "COUNT",
                String.valueOf(handler.getEditTotalCount()), mx, my, y+FY_COUNT);
        drawClickField(context, tx, y+FY_MAX_ACT, CFG_W-8, "MAX ACT",
                String.valueOf(handler.getEditMaxActive()), mx, my, y+FY_MAX_ACT);

        // Behaviour — dropdown
        drawDropdownField(context, tx, y+FY_BEHAV, CFG_W-8, "BEHAV",
                handler.getEditBehaviour().name(),
                openDropdown == Dropdown.BEHAVIOUR, mx, my);

        // Effects — dropdown (multi-select, shows count)
        int effectCount = handler.getEditEffects().size();
        String effectLabel = effectCount == 0 ? "none" : effectCount + " selected";
        drawDropdownField(context, tx, y+FY_EFFECTS, CFG_W-8, "EFFECTS",
                effectLabel, openDropdown == Dropdown.EFFECTS, mx, my);

        // Task link — text field
        drawTextField(context, tx, y+FY_TASK, CFG_W-8, "TASK",
                withCursor(handler.getEditLinkedTask(), focusedField == TextField.TASK),
                focusedField == TextField.TASK);

        // Click hints for count fields
        context.drawText(textRenderer, "L=+ R=-",
                tx+CFG_W-50, y+FY_COUNT+3, 0xFF664444, false);
        context.drawText(textRenderer, "L=+ R=-",
                tx+CFG_W-50, y+FY_MAX_ACT+3, 0xFF664444, false);
    }

    private void drawTextField(DrawContext context, int tx, int ty, int w,
                               String label, String value, boolean focused) {
        context.fill(tx, ty, tx+w, ty+FIELD_H, focused ? 0x20FF2020 : 0x08000000);
        context.drawText(textRenderer, label, tx+2, ty+3, C_LABEL, false);
        int lw = textRenderer.getWidth(label);
        context.drawText(textRenderer, trim(value, 20), tx+lw+6, ty+3, C_TEXT, false);
    }

    private void drawDropdownField(DrawContext context, int tx, int ty, int w,
                                   String label, String value, boolean open,
                                   int mx, int my) {
        boolean hov = inside(mx, my, tx, ty, w, FIELD_H);
        context.fill(tx, ty, tx+w, ty+FIELD_H, open ? 0x30FF2020 : hov ? 0x18FF2020 : 0x08000000);
        context.drawText(textRenderer, label, tx+2, ty+3, C_LABEL, false);
        int lw = textRenderer.getWidth(label);
        context.drawText(textRenderer, trim(value, 18), tx+lw+6, ty+3, C_TEXT, false);
        // Arrow indicator
        context.drawText(textRenderer, open ? "^" : "v",
                tx+w-8, ty+3, C_DIM, false);
    }

    private void drawClickField(DrawContext context, int tx, int ty, int w,
                                String label, String value, int mx, int my, int fieldY) {
        boolean hov = inside(mx, my, tx, ty, w, FIELD_H);
        context.fill(tx, ty, tx+w, ty+FIELD_H, hov ? 0x18FF2020 : 0x08000000);
        context.drawText(textRenderer, label, tx+2, ty+3, C_LABEL, false);
        int lw = textRenderer.getWidth(label);
        context.drawText(textRenderer, value, tx+lw+6, ty+3, C_TEXT, false);
    }

    // ── Open dropdown overlay ─────────────────────────────────────────────────
    private void drawOpenDropdown(DrawContext context, int mx, int my) {
        if (openDropdown == Dropdown.NONE) return;
        int x = this.x, y = this.y;

        List<String> items;
        int anchorY;
        switch (openDropdown) {
            case MOB       -> { items = MOB_LIST;    anchorY = y+FY_MOB; }
            case BEHAVIOUR -> { items = getBehaviourList(); anchorY = y+FY_BEHAV; }
            case EFFECTS   -> { items = allEffects;  anchorY = y+FY_EFFECTS; }
            default -> { return; }
        }

        int ddX = x+CFG_X+4;
        int ddY = anchorY + FIELD_H + 1;
        int ddW = CFG_W - 8;
        int ddH = DD_VISIBLE * DD_ROW_H + 2;

        // If dropdown would go off screen, flip up
        if (ddY + ddH > y + GUI_H - 20) ddY = anchorY - ddH - 1;

        // Background
        context.fill(ddX, ddY, ddX+ddW, ddY+ddH, 0xFF1a0808);
        context.fill(ddX, ddY, ddX+ddW, ddY+1, C_ACCENT);
        context.fill(ddX, ddY+ddH-1, ddX+ddW, ddY+ddH, C_ACCENT);
        context.fill(ddX, ddY, ddX+1, ddY+ddH, C_ACCENT);
        context.fill(ddX+ddW-1, ddY, ddX+ddW, ddY+ddH, C_ACCENT);

        clampDropdownScroll(items.size());

        context.enableScissor(ddX+1, ddY+1, ddX+ddW-1, ddY+ddH-1);
        for (int i = 0; i < DD_VISIBLE; i++) {
            int di = i + dropdownScrollOffset;
            if (di >= items.size()) break;
            int ry = ddY + 1 + i * DD_ROW_H;
            String item = items.get(di);
            boolean hov = inside(mx, my, ddX+1, ry, ddW-2, DD_ROW_H);
            boolean sel = isEffectSelected(item);

            context.fill(ddX+1, ry, ddX+ddW-1, ry+DD_ROW_H,
                    hov ? 0x30FF2020 : sel ? 0x18FF4040 : 0x00000000);
            if (sel) context.fill(ddX+1, ry, ddX+3, ry+DD_ROW_H, C_ACCENT2);

            String display = formatEffectLabel(item);
            context.drawText(textRenderer, trim(display, 24),
                    ddX+5, ry+2, sel ? C_ACCENT2 : C_TEXT, false);
        }
        context.disableScissor();

        // Scroll arrows
        if (dropdownScrollOffset > 0)
            context.drawText(textRenderer, "^", ddX+ddW-8, ddY+2, C_DIM, false);
        if (dropdownScrollOffset + DD_VISIBLE < items.size())
            context.drawText(textRenderer, "v", ddX+ddW-8, ddY+ddH-10, C_DIM, false);
    }

    // ── Pool tab ──────────────────────────────────────────────────────────────
    private void drawPoolPanel(DrawContext context, int x, int y) {
        int tx = x+CFG_X+4, ty = y+CFG_Y+4;
        context.drawText(textRenderer, "Push encounter to task pool", tx, ty, C_DIM, false);
        ty += 14;
        drawTextField(context, tx, ty,    CFG_W-8, "NAME",
                withCursor(handler.getPoolTaskName(), focusedField == TextField.POOL_NAME),
                focusedField == TextField.POOL_NAME);
        ty += FIELD_GAP;
        drawTextField(context, tx, ty,    CFG_W-8, "DESC",
                withCursor(handler.getPoolTaskDesc(), focusedField == TextField.POOL_DESC),
                focusedField == TextField.POOL_DESC);
        ty += FIELD_GAP;
        // Division — click to cycle
        context.fill(tx, ty, tx+CFG_W-8, ty+FIELD_H, 0x08000000);
        context.drawText(textRenderer, "DIV", tx+2, ty+3, C_LABEL, false);
        context.drawText(textRenderer, handler.getPoolDivision(), tx+30, ty+3, C_TEXT, false);
    }

    // ── Buttons ───────────────────────────────────────────────────────────────
    private void drawButtons(DrawContext context, int x, int y, int mx, int my) {
        // ACTIVATE
        boolean hovAct = inside(mx, my, x+ACT_BTN_X, y+ACT_BTN_Y, ACT_BTN_W, ACT_BTN_H);
        context.fill(x+ACT_BTN_X, y+ACT_BTN_Y, x+ACT_BTN_X+ACT_BTN_W, y+ACT_BTN_Y+ACT_BTN_H,
                hovAct ? 0x40FF2020 : 0x20FF2020);
        drawCentered(context, "ACTIVATE SPAWN",
                x+ACT_BTN_X, y+ACT_BTN_Y, ACT_BTN_W, ACT_BTN_H, C_ACCENT);

        // SAVE TEMPLATE
        boolean canSave = !handler.getEditDisplayName().isBlank()
                && !handler.getEditMobTypeId().isBlank();
        boolean hovSave = inside(mx, my, x+SAVE_BTN_X, y+SAVE_BTN_Y, SAVE_BTN_W, SAVE_BTN_H);
        context.fill(x+SAVE_BTN_X, y+SAVE_BTN_Y, x+SAVE_BTN_X+SAVE_BTN_W, y+SAVE_BTN_Y+SAVE_BTN_H,
                canSave ? (hovSave ? 0x40FF8020 : 0x20FF8020) : 0x08000000);
        drawCentered(context, "SAVE TEMPLATE",
                x+SAVE_BTN_X, y+SAVE_BTN_Y, SAVE_BTN_W, SAVE_BTN_H,
                canSave ? C_ACCENT2 : C_DIM);

        // Block coords
        context.drawText(textRenderer,
                "BLOCK " + handler.getBlockX() + "," + handler.getBlockY() + ","
                        + handler.getBlockZ(),
                x+14, y+GUI_H-10, C_DIM, false);
    }

    private void drawCentered(DrawContext context, String text,
                              int bx, int by, int bw, int bh, int color) {
        int tw = textRenderer.getWidth(text);
        context.drawText(textRenderer, text, bx+(bw-tw)/2, by+(bh-8)/2+1, color, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = this.x, y = this.y;

        // Close dropdown if clicking outside
        if (openDropdown != Dropdown.NONE) {
            int ddX = x+CFG_X+4;
            int anchorY = switch (openDropdown) {
                case MOB       -> y+FY_MOB;
                case BEHAVIOUR -> y+FY_BEHAV;
                case EFFECTS   -> y+FY_EFFECTS;
                default        -> 0;
            };
            int ddY = anchorY + FIELD_H + 1;
            int ddH = DD_VISIBLE * DD_ROW_H + 2;
            if (ddY + ddH > y + GUI_H - 20) ddY = anchorY - ddH - 1;

            List<String> items = switch (openDropdown) {
                case MOB       -> MOB_LIST;
                case BEHAVIOUR -> getBehaviourList();
                case EFFECTS   -> allEffects;
                default        -> List.of();
            };

            if (inside(mx, my, ddX+1, ddY+1, CFG_W-10, DD_VISIBLE * DD_ROW_H)) {
                // Click inside dropdown
                int relY = (int)(my - ddY - 1);
                int idx = relY / DD_ROW_H + dropdownScrollOffset;
                if (idx >= 0 && idx < items.size()) {
                    handleDropdownSelect(items.get(idx));
                }
                return true;
            } else {
                openDropdown = Dropdown.NONE;
                return true;
            }
        }

        // Tabs
        if (inside(mx, my, x+CONFIG_TAB_X, y+CONFIG_TAB_Y, CONFIG_TAB_W, CONFIG_TAB_H)) {
            handler.setSelectedTab(ConfigTab.CONFIG); focusedField = TextField.NONE; return true;
        }
        if (inside(mx, my, x+POOL_TAB_X, y+POOL_TAB_Y, POOL_TAB_W, POOL_TAB_H)) {
            handler.setSelectedTab(ConfigTab.POOL); focusedField = TextField.NONE; return true;
        }

        // Template list
        List<TemplateEntry> templates = handler.getTemplates();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i+listScrollOffset;
            if (di >= templates.size()) break;
            if (inside(mx, my, x+LIST_X+2, y+LIST_Y+(i*ROW_H)+1, LIST_W-4, ROW_H-2)) {
                handler.setSelectedTemplateIndex(di);
                focusedField = TextField.NONE;
                syncBuffersFromHandler();
                return true;
            }
        }

        if (handler.getSelectedTab() == ConfigTab.CONFIG) {
            int tx = x+CFG_X+4;

            // Text fields
            if (inside(mx, my, tx, y+FY_NAME,   CFG_W-8, FIELD_H)) { focusedField = TextField.NAME;   return true; }
            if (inside(mx, my, tx, y+FY_HEALTH,  CFG_W-8, FIELD_H)) { focusedField = TextField.HEALTH; return true; }
            if (inside(mx, my, tx, y+FY_ARMOR,   CFG_W-8, FIELD_H)) { focusedField = TextField.ARMOR;  return true; }
            if (inside(mx, my, tx, y+FY_TASK,    CFG_W-8, FIELD_H)) { focusedField = TextField.TASK;   return true; }

            // Click +/- fields
            if (inside(mx, my, tx, y+FY_COUNT,   CFG_W-8, FIELD_H)) {
                if (button == 0) handler.setEditTotalCount(handler.getEditTotalCount()+1);
                else handler.setEditTotalCount(handler.getEditTotalCount()-1);
                return true;
            }
            if (inside(mx, my, tx, y+FY_MAX_ACT, CFG_W-8, FIELD_H)) {
                if (button == 0) handler.setEditMaxActive(handler.getEditMaxActive()+1);
                else handler.setEditMaxActive(handler.getEditMaxActive()-1);
                return true;
            }

            // Dropdowns — toggle
            if (inside(mx, my, tx, y+FY_MOB,     CFG_W-8, FIELD_H)) {
                openDropdown = openDropdown == Dropdown.MOB ? Dropdown.NONE : Dropdown.MOB;
                dropdownScrollOffset = 0; focusedField = TextField.NONE; return true;
            }
            if (inside(mx, my, tx, y+FY_BEHAV,   CFG_W-8, FIELD_H)) {
                openDropdown = openDropdown == Dropdown.BEHAVIOUR ? Dropdown.NONE : Dropdown.BEHAVIOUR;
                dropdownScrollOffset = 0; focusedField = TextField.NONE; return true;
            }
            if (inside(mx, my, tx, y+FY_EFFECTS, CFG_W-8, FIELD_H)) {
                openDropdown = openDropdown == Dropdown.EFFECTS ? Dropdown.NONE : Dropdown.EFFECTS;
                dropdownScrollOffset = 0; focusedField = TextField.NONE; return true;
            }

            // Buttons
            if (inside(mx, my, x+ACT_BTN_X, y+ACT_BTN_Y, ACT_BTN_W, ACT_BTN_H)) {
                commitBuffers(); sendActivate(); return true;
            }
            if (inside(mx, my, x+SAVE_BTN_X, y+SAVE_BTN_Y, SAVE_BTN_W, SAVE_BTN_H)) {
                commitBuffers(); sendSaveTemplate(); return true;
            }
        }

        if (handler.getSelectedTab() == ConfigTab.POOL) {
            int tx = x+CFG_X+4;
            int ty = y+CFG_Y+18;
            if (inside(mx, my, tx, ty,          CFG_W-8, FIELD_H)) { focusedField = TextField.POOL_NAME; return true; }
            if (inside(mx, my, tx, ty+FIELD_GAP, CFG_W-8, FIELD_H)) { focusedField = TextField.POOL_DESC; return true; }
            if (inside(mx, my, tx, ty+FIELD_GAP*2, CFG_W-8, FIELD_H)) { cycleDivision(); return true; }
            if (inside(mx, my, x+SAVE_BTN_X, y+SAVE_BTN_Y, SAVE_BTN_W, SAVE_BTN_H)) {
                sendPushToPool(); return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int x = this.x, y = this.y;
        int delta = va > 0 ? -1 : 1;

        if (openDropdown != Dropdown.NONE) {
            List<String> items = switch (openDropdown) {
                case MOB       -> MOB_LIST;
                case BEHAVIOUR -> getBehaviourList();
                case EFFECTS   -> allEffects;
                default        -> List.of();
            };
            dropdownScrollOffset = Math.max(0, Math.min(
                    dropdownScrollOffset + delta,
                    Math.max(0, items.size() - DD_VISIBLE)));
            return true;
        }

        if (inside(mx, my, x+LIST_X, y+LIST_Y, LIST_W, LIST_H)) {
            listScrollOffset = Math.max(0, Math.min(
                    listScrollOffset + delta,
                    Math.max(0, handler.getTemplates().size() - VISIBLE_ROWS)));
            return true;
        }
        return super.mouseScrolled(mx, my, ha, va);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr < 32 || chr == 127) return false;
        switch (focusedField) {
            case NAME -> { if (handler.getEditDisplayName().length() < 48)
                handler.setEditDisplayName(handler.getEditDisplayName()+chr); return true; }
            case HEALTH -> { if (isNumericChar(chr) && healthBuffer.length() < 6)
                healthBuffer += chr; return true; }
            case ARMOR  -> { if (isNumericChar(chr) && armorBuffer.length()  < 6)
                armorBuffer  += chr; return true; }
            case TASK -> { if (handler.getEditLinkedTask().length() < 48)
                handler.setEditLinkedTask(handler.getEditLinkedTask()+chr); return true; }
            case POOL_NAME -> { if (handler.getPoolTaskName().length() < 48)
                handler.setPoolTaskName(handler.getPoolTaskName()+chr); return true; }
            case POOL_DESC -> { if (handler.getPoolTaskDesc().length() < 96)
                handler.setPoolTaskDesc(handler.getPoolTaskDesc()+chr); return true; }
            default -> {}
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (openDropdown != Dropdown.NONE) { openDropdown = Dropdown.NONE; return true; }
            if (focusedField != TextField.NONE) { commitBuffers(); focusedField = TextField.NONE; return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focusedField != TextField.NONE) {
            switch (focusedField) {
                case NAME      -> handler.setEditDisplayName(backspace(handler.getEditDisplayName()));
                case HEALTH    -> healthBuffer = backspace(healthBuffer);
                case ARMOR     -> armorBuffer  = backspace(armorBuffer);
                case TASK      -> handler.setEditLinkedTask(backspace(handler.getEditLinkedTask()));
                case POOL_NAME -> handler.setPoolTaskName(backspace(handler.getPoolTaskName()));
                case POOL_DESC -> handler.setPoolTaskDesc(backspace(handler.getPoolTaskDesc()));
                default -> {}
            }
            return true;
        }
        if (focusedField != TextField.NONE && keyCode != GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Dropdown helpers ──────────────────────────────────────────────────────
    private void handleDropdownSelect(String item) {
        switch (openDropdown) {
            case MOB       -> { handler.setEditMobTypeId(item); openDropdown = Dropdown.NONE; }
            case BEHAVIOUR -> {
                try { ((SpawnConfigScreenHandler) handler).setEditBehaviour(SpawnBehaviour.valueOf(item)); }                catch (Exception ignored) {}
                openDropdown = Dropdown.NONE;
            }
            case EFFECTS   -> {
                // Toggle selection
                List<String> effects = new ArrayList<>(handler.getEditEffects());
                if (effects.contains(item)) effects.remove(item);
                else effects.add(item);
                handler.setEditEffects(effects);
                // Keep dropdown open for multi-select
            }
            default -> {}
        }
    }

    private boolean isEffectSelected(String item) {
        if (openDropdown == Dropdown.EFFECTS) {
            return handler.getEditEffects().contains(item);
        }
        return false;
    }

    private String formatEffectLabel(String item) {
        if (item.startsWith("skill:")) {
            GmSkill skill = GmSkill.fromStorageKey(item);
            return skill != null ? "[SKILL] " + skill.getDisplayName() : item;
        }
        // vanilla effect — prettify
        String[] parts = item.split(":");
        String name = parts.length >= 2 ? parts[1] : item;
        String amp  = parts.length >= 3 ? " Amp " + parts[2] : "";
        return name.replace("_", " ") + amp;
    }

    private List<String> getBehaviourList() {
        List<String> list = new ArrayList<>();
        for (SpawnBehaviour b : SpawnBehaviour.values()) list.add(b.name());
        return list;
    }

    // ── Send packets ──────────────────────────────────────────────────────────
    private void sendSaveTemplate() {
        if (handler.getEditDisplayName().isBlank() || handler.getEditMobTypeId().isBlank()) return;
        ClientPlayNetworking.send(new SaveTemplateC2SPacket(
                handler.getEditDisplayName().trim()
                        .toLowerCase().replaceAll("[^a-z0-9]","_")
                        .replaceAll("_+","_").replaceAll("^_|_$",""),
                handler.getEditDisplayName(),
                handler.getEditMobTypeId(),
                handler.getEditMaxHealth(),
                handler.getEditArmor(),
                handler.getEditTotalCount(),
                handler.getEditMaxActive(),
                handler.getEditRadius(),
                handler.getEditInterval(),
                handler.getEditBehaviour().name(),
                handler.getEditEffects()
        ));
    }

    private void sendActivate() {
        String taskId = h().getEditLinkedTask().isBlank() ? null : h().getEditLinkedTask();
        // Send the currently selected template ID from the GUI, not the registered one
        TemplateEntry sel = h().getSelectedTemplate();
        String templateId = sel != null ? sel.id() : null;
        if (templateId == null) return;

        // First save the current edits as a template update
        sendSaveTemplate();

        // Then activate using the selected template
        ClientPlayNetworking.send(new ActivateSpawnBlockC2SPacket(
                h().getBlockX(), h().getBlockY(), h().getBlockZ(),
                h().getWorldKey(), taskId, templateId));
        this.close();
    }

    private void sendPushToPool() {
        TemplateEntry sel = handler.getSelectedTemplate();
        if (sel == null || handler.getPoolTaskName().isBlank()) return;
        ClientPlayNetworking.send(new PushToPoolC2SPacket(
                sel.id(), handler.getPoolTaskName(),
                handler.getPoolTaskDesc(), handler.getPoolDivision()));
    }

    // ── Buffer helpers ────────────────────────────────────────────────────────
    private void commitBuffers() {
        try { handler.setEditMaxHealth(Double.parseDouble(healthBuffer)); }
        catch (Exception e) { healthBuffer = String.valueOf((int)handler.getEditMaxHealth()); }
        try { handler.setEditArmor(Double.parseDouble(armorBuffer)); }
        catch (Exception e) { armorBuffer = String.valueOf((int)handler.getEditArmor()); }
    }

    private void syncBuffersFromHandler() {
        healthBuffer = String.valueOf((int)handler.getEditMaxHealth());
        armorBuffer  = String.valueOf((int)handler.getEditArmor());
    }

    private void cycleDivision() {
        Division[] vals = Division.values();
        for (int i = 0; i < vals.length; i++) {
            if (vals[i].name().equals(handler.getPoolDivision())) {
                handler.setPoolDivision(vals[(i+1) % vals.length].name()); return;
            }
        }
        handler.setPoolDivision(vals[0].name());
    }

    private boolean isNumericChar(char c) { return c >= '0' && c <= '9' || c == '.'; }
    private String withCursor(String v, boolean f) { return f ? v+"_" : v; }
    private String backspace(String v) { return v==null||v.isEmpty()?"":v.substring(0,v.length()-1); }
    private String trim(String t, int max) {
        if (t==null) return "";
        return t.length()<=max ? t : t.substring(0,Math.max(0,max-3))+"...";
    }
    private void clampScroll(int total) {
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, Math.max(0, total-VISIBLE_ROWS)));
    }
    private void clampDropdownScroll(int total) {
        dropdownScrollOffset = Math.max(0, Math.min(dropdownScrollOffset, Math.max(0, total-DD_VISIBLE)));
    }
    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx>=x && mx<=x+w && my>=y && my<=y+h;
    }

    private void drawScrollArrows(DrawContext context, int px, int py, int pw, int ph,
                                  int offset, int total, int visible) {
        if (total <= visible) return;
        int ax = px + pw - 8;
        if (offset > 0)
            context.drawText(textRenderer, "^", ax, py + 2, C_DIM, false);
        if (offset + visible < total)
            context.drawText(textRenderer, "v", ax, py + ph - 10, C_DIM, false);
    }

    private SpawnConfigScreenHandler h() {
        return (SpawnConfigScreenHandler) handler;
    }

}