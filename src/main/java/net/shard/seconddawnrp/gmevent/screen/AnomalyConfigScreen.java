package net.shard.seconddawnrp.gmevent.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.gmevent.data.AnomalyType;
import net.shard.seconddawnrp.gmevent.network.SaveAnomalyConfigC2SPacket;

/**
 * Anomaly Marker configuration screen.
 * Purple theme (matching anomaly tool visibility colour).
 * Opened when a GM right-clicks a registered anomaly marker block.
 */
public class AnomalyConfigScreen extends Screen {

    private static final int W   = 260;
    private static final int H   = 210;
    private static final int PAD = 8;

    // Palette — purple theme
    private static final int COL_BG        = 0xFF05010F;
    private static final int COL_HEADER_BG = 0xFF0A0220;
    private static final int COL_BORDER    = 0xFF7B2FBE;
    private static final int COL_ACCENT    = 0xFF9B4FDE;
    private static final int COL_DIM       = 0xFF3B1060;
    private static final int COL_DARK_PNL  = 0xFF020008;
    private static final int COL_TEXT      = 0xFFE8D0FF;

    private static final int COL_ENERGY      = 0xFF2288DD;
    private static final int COL_BIO         = 0xFF22AA44;
    private static final int COL_GRAV        = 0xFF9944CC;
    private static final int COL_UNKNOWN     = 0xFFCC2222;

    private final String entryId;
    private AnomalyType selectedType;
    private boolean active;

    private TextFieldWidget nameField;
    private TextFieldWidget descField;
    private int ox, oy;

    public AnomalyConfigScreen(String entryId, String name, String description,
                               String anomalyType, boolean active) {
        super(Text.literal("Anomaly Marker Config"));
        this.entryId      = entryId;
        this.selectedType = parseType(anomalyType);
        this.active       = active;
    }

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;

        int fieldW = W - PAD * 2;
        int fieldX = ox + PAD;

        // Name field
        nameField = new TextFieldWidget(textRenderer, fieldX, oy + 44, fieldW, 14,
                Text.literal("Name"));
        nameField.setMaxLength(64);
        nameField.setText(title.getString().equals("Anomaly Marker Config") ? "" : "");
        addDrawableChild(nameField);

        // Description field
        descField = new TextFieldWidget(textRenderer, fieldX, oy + 90, fieldW, 14,
                Text.literal("Description"));
        descField.setMaxLength(128);
        addDrawableChild(descField);

        // Type buttons
        int typeBtnW = (fieldW - 6) / 4;
        AnomalyType[] types = AnomalyType.values();
        for (int i = 0; i < types.length; i++) {
            final AnomalyType type = types[i];
            addDrawableChild(ButtonWidget.builder(
                            Text.literal(type.getDisplayName().split(" ")[0]),
                            b -> { selectedType = type; rebuildButtons(); })
                    .dimensions(fieldX + i * (typeBtnW + 2), oy + 126, typeBtnW, 14)
                    .build());
        }

        // Activate / Deactivate toggle
        addDrawableChild(ButtonWidget.builder(
                        Text.literal(active ? "Deactivate" : "Activate")
                                .formatted(active ? Formatting.YELLOW : Formatting.GREEN),
                        b -> { active = !active; rebuildButtons(); })
                .dimensions(ox + PAD, oy + H - 26, 80, 14).build());

        // Save
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Save").formatted(Formatting.GREEN),
                        b -> save())
                .dimensions(ox + W / 2 - 40, oy + H - 26, 80, 14).build());

        // Cancel
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Cancel"),
                        b -> close())
                .dimensions(ox + W - PAD - 60, oy + H - 26, 60, 14).build());
    }

    private void rebuildButtons() {
        clearChildren();
        init();
    }

    private void save() {
        ClientPlayNetworking.send(new SaveAnomalyConfigC2SPacket(
                entryId,
                nameField.getText().trim().isEmpty() ? "Anomaly" : nameField.getText().trim(),
                descField.getText().trim(),
                selectedType.name(),
                active));
        close();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        drawPanel(ctx, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;
        fill(ctx, x, y, x+W, y+H, COL_BG);
        hline(ctx, x, y, x+W, COL_BORDER);
        hline(ctx, x, y+H-1, x+W, COL_BORDER);
        vline(ctx, x, y, y+H, COL_BORDER);
        vline(ctx, x+W-1, y, y+H, COL_BORDER);

        // Header
        fill(ctx, x+2, y+2, x+W-2, y+17, COL_HEADER_BG);
        hline(ctx, x+2, y+17, x+W-2, COL_BORDER);
        fill(ctx, x+PAD, y+6, x+PAD+4, y+12, typeColour(selectedType));
        ctx.drawText(textRenderer,
                Text.literal("Anomaly Marker — " + selectedType.getDisplayName())
                        .formatted(Formatting.WHITE),
                x+PAD+8, y+5, COL_ACCENT, false);
        ctx.drawText(textRenderer,
                Text.literal(entryId).formatted(Formatting.DARK_GRAY),
                x+W-PAD-textRenderer.getWidth(entryId), y+5, 0xFF332244, false);

        // Name label
        ctx.drawText(textRenderer, Text.literal("Name").formatted(Formatting.GOLD),
                x+PAD, y+33, COL_ACCENT, false);

        // Description label
        ctx.drawText(textRenderer, Text.literal("Description").formatted(Formatting.GOLD),
                x+PAD, y+79, COL_ACCENT, false);

        // Type label
        hline(ctx, x+2, y+110, x+W-2, COL_DIM);
        ctx.drawText(textRenderer, Text.literal("Type").formatted(Formatting.GOLD),
                x+PAD, y+114, COL_ACCENT, false);

        // Status indicator
        hline(ctx, x+2, y+148, x+W-2, COL_DIM);
        int statusCol = active ? 0xFF22AA44 : 0xFF886622;
        String statusStr = active ? "● ACTIVE" : "○ INACTIVE";
        ctx.drawText(textRenderer, Text.literal(statusStr), x+PAD, y+154, statusCol, false);

        // Footer divider
        hline(ctx, x+2, y+H-32, x+W-2, COL_BORDER);
        fill(ctx, x+2, y+H-32, x+W-2, y+H-2, COL_HEADER_BG);
    }

    private static int typeColour(AnomalyType t) {
        return switch (t) {
            case ENERGY      -> COL_ENERGY;
            case BIOLOGICAL  -> COL_BIO;
            case GRAVITATIONAL -> COL_GRAV;
            case UNKNOWN     -> COL_UNKNOWN;
        };
    }

    private static AnomalyType parseType(String s) {
        try { return AnomalyType.valueOf(s); }
        catch (Exception e) { return AnomalyType.UNKNOWN; }
    }

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1 > x0 && y1 > y0) ctx.fill(x0, y0, x1, y1, col);
    }
    private void hline(DrawContext ctx, int x0, int y, int x1, int col) { ctx.fill(x0, y, x1, y+1, col); }
    private void vline(DrawContext ctx, int x, int y0, int y1, int col) { ctx.fill(x, y0, x+1, y1, col); }
}