package net.shard.seconddawnrp.gmevent.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.network.DespawnAllC2SPacket;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket.TemplateEntry;

import java.util.List;

public class SpawnItemScreen extends HandledScreen<SpawnItemScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/spawn_item_gui.png");

    private static final int TEX_W = 512, TEX_H = 256;
    private static final int GUI_W = 380, GUI_H = 190;

    private static final int LIST_X = 14, LIST_Y = 40, LIST_W = 180, LIST_H = 100, ROW_H = 20;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private static final int DET_X = 204, DET_Y = 40, DET_W = 162, DET_H = 100;

    private static final int COORDS_X = 14, COORDS_Y = GUI_H - 49,
            COORDS_W = GUI_W - 28, COORDS_H = 13;

    // Only DESPAWN button — FIRE moved to keybinding G
    private static final int DESP_X = GUI_W/2 - 60, DESP_Y = GUI_H - 34,
            DESP_W = 120, DESP_H = 18;

    private static final int C_ACCENT  = 0xFFb42828;
    private static final int C_ACCENT2 = 0xFFdc5028;
    private static final int C_TEXT    = 0xFFe8d8d8;
    private static final int C_DIM     = 0xFF784040;

    private int listScrollOffset = 0;

    public SpawnItemScreen(SpawnItemScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth  = GUI_W;
        this.backgroundHeight = GUI_H;
        this.playerInventoryTitleY = 10000;
    }

    @Override protected void init() { super.init(); titleX = 0; titleY = 0; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        context.drawTexture(TEXTURE, x, y, 0, 0, GUI_W, GUI_H, TEX_W, TEX_H);
        drawHeader(context, x, y);
        drawTemplateList(context, x, y, mouseX, mouseY);
        drawDetailPanel(context, x, y);
        drawCoordsBar(context, x, y);
        drawActionBar(context, x, y, mouseX, mouseY);
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {}

    private void drawHeader(DrawContext context, int x, int y) {
        int lw = textRenderer.getWidth("TEMPLATES");
        context.drawText(textRenderer, "TEMPLATES",
                x+LIST_X+(LIST_W-lw)/2, y+29, C_ACCENT, false);
        int dw = textRenderer.getWidth("SELECTED");
        context.drawText(textRenderer, "SELECTED",
                x+DET_X+(DET_W-dw)/2, y+29, C_ACCENT, false);
    }

    private void drawTemplateList(DrawContext context, int x, int y, int mx, int my) {
        List<TemplateEntry> templates = handler.getTemplates();
        clampScroll(templates.size());

        if (templates.isEmpty()) {
            context.drawText(textRenderer, "No templates loaded",
                    x+LIST_X+4, y+LIST_Y+4, C_DIM, false);
            return;
        }

        context.enableScissor(x+LIST_X, y+LIST_Y, x+LIST_X+LIST_W, y+LIST_Y+LIST_H);
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i+listScrollOffset;
            if (di >= templates.size()) break;
            int rx = x+LIST_X, ry = y+LIST_Y+(i*ROW_H);
            boolean sel = di == handler.getSelectedIndex();
            boolean hov = inside(mx, my, rx+2, ry+1, LIST_W-4, ROW_H-2);
            context.fill(rx+2, ry+1, rx+LIST_W-2, ry+ROW_H-1,
                    sel ? 0x30FF2020 : hov ? 0x18FF2020 : 0x08000000);
            if (sel) context.fill(rx+2, ry+1, rx+4, ry+ROW_H-1, C_ACCENT2);
            TemplateEntry t = templates.get(di);
            context.drawText(textRenderer, trim(t.displayName(), 16),
                    rx+8, ry+3, sel ? C_TEXT : 0xFF886060, false);
            context.drawText(textRenderer, trim(t.mobTypeId(), 18),
                    rx+8, ry+11, C_DIM, false);
        }
        context.disableScissor();
        drawScrollArrows(context, x+LIST_X, y+LIST_Y, LIST_W, LIST_H,
                listScrollOffset, templates.size(), VISIBLE_ROWS);
    }

    private void drawDetailPanel(DrawContext context, int x, int y) {
        TemplateEntry sel = handler.getSelectedTemplate();
        int tx = x+DET_X+4, ty = y+DET_Y+2;

        context.enableScissor(x+DET_X, y+DET_Y, x+DET_X+DET_W, y+DET_Y+DET_H);

        if (sel == null) {
            context.drawText(textRenderer, "Select a template", tx, ty, C_DIM, false);
            context.drawText(textRenderer, "then press G to spawn", tx, ty+12, C_DIM, false);
            context.disableScissor();
            return;
        }

        context.drawText(textRenderer, trim(sel.displayName(), 18), tx, ty, C_TEXT, false); ty += 12;
        drawDetailLine(context, tx, ty, "Mob",    sel.mobTypeId()); ty += 11;
        drawDetailLine(context, tx, ty, "HP",     String.valueOf((int)sel.maxHealth())); ty += 11;
        drawDetailLine(context, tx, ty, "Armor",  String.valueOf((int)sel.armor())); ty += 11;
        drawDetailLine(context, tx, ty, "Count",  sel.totalSpawnCount()+" / max "+sel.maxActiveAtOnce()); ty += 11;
        drawDetailLine(context, tx, ty, "Radius", sel.spawnRadiusBlocks()+"b"); ty += 11;
        drawDetailLine(context, tx, ty, "Behav",  sel.spawnBehaviour()); ty += 11;

        // Spawn hint
        context.drawText(textRenderer, "Press G to spawn here", tx, ty+4, C_ACCENT, false);

        context.disableScissor();
    }

    private void drawDetailLine(DrawContext context, int tx, int ty, String label, String value) {
        context.drawText(textRenderer, label+":", tx, ty, 0xFFff6060, false);
        int lw = textRenderer.getWidth(label+":");
        context.drawText(textRenderer, trim(value, 16), tx+lw+4, ty, C_TEXT, false);
    }

    private void drawCoordsBar(DrawContext context, int x, int y) {
        String coords = "TARGET " + handler.getTargetX() + ", "
                + handler.getTargetY() + ", " + handler.getTargetZ();
        int cw = textRenderer.getWidth(coords);
        context.drawText(textRenderer, coords,
                x+COORDS_X+(COORDS_W-cw)/2, y+COORDS_Y+3, C_ACCENT, false);
    }

    private void drawActionBar(DrawContext context, int x, int y, int mx, int my) {
        // DESPAWN ALL — centered, with hover highlight
        boolean hovDesp = inside(mx, my, x+DESP_X, y+DESP_Y, DESP_W, DESP_H);
        context.fill(x+DESP_X, y+DESP_Y, x+DESP_X+DESP_W, y+DESP_Y+DESP_H,
                hovDesp ? 0x40FF2020 : 0x18FF2020);
        int dlw = textRenderer.getWidth("DESPAWN ALL");
        context.drawText(textRenderer, "DESPAWN ALL",
                x+DESP_X+(DESP_W-dlw)/2, y+DESP_Y+(DESP_H-8)/2+1,
                hovDesp ? C_ACCENT : C_DIM, false);

        // Keybind hint
        String hint = "G = Spawn  |  H = Despawn All";
        int hw = textRenderer.getWidth(hint);
        context.drawText(textRenderer, hint,
                x+(GUI_W-hw)/2, y+GUI_H-10, C_DIM, false);
    }

    private void drawScrollArrows(DrawContext context, int px, int py, int pw, int ph,
                                  int offset, int total, int visible) {
        if (total <= visible) return;
        int ax = px+pw-8;
        if (offset > 0) context.drawText(textRenderer, "^", ax, py+2, C_DIM, false);
        if (offset+visible < total) context.drawText(textRenderer, "v", ax, py+ph-10, C_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = this.x, y = this.y;

        List<TemplateEntry> templates = handler.getTemplates();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i+listScrollOffset;
            if (di >= templates.size()) break;
            int rx = x+LIST_X, ry = y+LIST_Y+(i*ROW_H);
            if (inside(mx, my, rx+2, ry+1, LIST_W-4, ROW_H-2)) {
                handler.setSelectedIndex(di);
                // Write selected template to NBT so keybinding can read it
                writeSelectedTemplateToItem(templates.get(di).id());
                return true;
            }
        }

        if (inside(mx, my, x+DESP_X, y+DESP_Y, DESP_W, DESP_H)) {
            ClientPlayNetworking.send(new DespawnAllC2SPacket());
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int x = this.x, y = this.y;
        if (inside(mx, my, x+LIST_X, y+LIST_Y, LIST_W, LIST_H)) {
            int delta = va > 0 ? -1 : 1;
            listScrollOffset = Math.max(0, Math.min(listScrollOffset+delta,
                    Math.max(0, handler.getTemplates().size()-VISIBLE_ROWS)));
            return true;
        }
        return super.mouseScrolled(mx, my, ha, va);
    }

    private void writeSelectedTemplateToItem(String templateId) {
        // Write to held item NBT via a lightweight packet so the keybinding
        // can read the selected template next time G is pressed
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return;
        var stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof net.shard.seconddawnrp.gmevent.item.SpawnItemTool)) return;
        var nbt = new net.minecraft.nbt.NbtCompound();
        var existing = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (existing != null) nbt = existing.copyNbt();
        nbt.putString("SelectedTemplate", templateId);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt));
    }

    private String trim(String t, int max) {
        if (t==null) return "";
        return t.length()<=max ? t : t.substring(0,Math.max(0,max-3))+"...";
    }
    private void clampScroll(int total) {
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, Math.max(0, total-VISIBLE_ROWS)));
    }
    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx>=x && mx<=x+w && my>=y && my<=y+h;
    }
}