package net.shard.seconddawnrp.warpcore.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.warpcore.network.WarpCoreActionC2SPacket;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;

/**
 * Warp Core Monitor screen. Dark navy + amber theme.
 * Physical buttons for Startup, Shutdown, and Reset.
 */
public class WarpCoreMonitorScreen extends Screen {

    private static final int W   = 260;
    private static final int H   = 220;
    private static final int PAD = 6;

    private static final int COL_BG        = 0xFF03091A;
    private static final int COL_HEADER_BG = 0xFF050B1A;
    private static final int COL_BORDER    = 0xFFB96408;
    private static final int COL_ACCENT    = 0xFFD7820A;
    private static final int COL_DIM       = 0xFF502C04;
    private static final int COL_DIM2      = 0xFF1E0F02;
    private static final int COL_DARK_PNL  = 0xFF02050F;
    private static final int COL_ONLINE    = 0xFF2D8214;
    private static final int COL_UNSTABLE  = 0xFFB97308;
    private static final int COL_CRITICAL  = 0xFFC84608;

    private WarpCoreStatusS2CPacket data;
    private String entryId;
    private int ox, oy;

    public WarpCoreMonitorScreen(WarpCoreStatusS2CPacket data, String entryId) {
        super(Text.literal("Warp Core Monitor"));
        this.data    = data;
        this.entryId = entryId;
    }

    public void updateData(WarpCoreStatusS2CPacket data) { this.data = data; }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;

        int btnY  = oy + H - 26;
        int gap   = 4;
        int totalGap = gap * 3;
        int btnW  = (W - PAD * 2 - totalGap) / 4;
        int x0    = ox + PAD;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Startup").formatted(Formatting.GREEN),
                        b -> sendAction(WarpCoreActionC2SPacket.Action.STARTUP))
                .dimensions(x0, btnY, btnW, 14).build());

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Shutdown").formatted(Formatting.YELLOW),
                        b -> sendAction(WarpCoreActionC2SPacket.Action.SHUTDOWN))
                .dimensions(x0 + (btnW + gap), btnY, btnW, 14).build());

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Reset").formatted(Formatting.RED),
                        b -> sendAction(WarpCoreActionC2SPacket.Action.RESET))
                .dimensions(x0 + (btnW + gap) * 2, btnY, btnW, 14).build());

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Close"),
                        b -> close())
                .dimensions(x0 + (btnW + gap) * 3, btnY, btnW, 14).build());
    }

    private void sendAction(WarpCoreActionC2SPacket.Action action) {
        if (entryId == null) return;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new WarpCoreActionC2SPacket(entryId, action));
        close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        renderBackground(ctx, mx, my, delta);
        drawPanel(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void drawPanel(DrawContext ctx) {
        int x = ox, y = oy;

        fill(ctx, x, y, x+W, y+H, COL_BG);
        hline(ctx, x, y,     x+W, COL_BORDER);
        hline(ctx, x, y+H-1, x+W, COL_BORDER);
        vline(ctx, x,     y, y+H, COL_BORDER);
        vline(ctx, x+W-1, y, y+H, COL_BORDER);
        hline(ctx, x+1, y+1,     x+W-1, COL_DIM);
        hline(ctx, x+1, y+H-2,   x+W-1, COL_DIM);

        int cy = y + 2;

        // Title bar
        fill(ctx, x+2, cy, x+W-2, cy+13, COL_HEADER_BG);
        hline(ctx, x+2, cy+13, x+W-2, COL_BORDER);
        int[] dots = {0xFFAA2323, 0xFFA08210, COL_ACCENT};
        for (int i = 0; i < 3; i++) fill(ctx, x+PAD+i*9, cy+4, x+PAD+i*9+5, cy+9, dots[i]);
        ctx.drawText(textRenderer, Text.literal("Warp Core Monitor").formatted(Formatting.GOLD),
                x+32, cy+3, 0xFFFFFFFF, false);
        if (entryId != null)
            ctx.drawText(textRenderer, Text.literal(entryId).formatted(Formatting.DARK_GRAY),
                    x+W-textRenderer.getWidth(entryId)-PAD, cy+3, 0xFF444433, false);
        cy += 15;

        if (data == null || !data.registered()) {
            ctx.drawText(textRenderer,
                    Text.literal("No warp core registered.").formatted(Formatting.DARK_GRAY),
                    x+PAD, cy+8, 0xFF666666, false);
            drawFooterDivider(ctx, x, y);
            return;
        }

        // State banner
        int stateBord = stateCol(data.state());
        int stateBg = dimOf(stateBord);
        fill(ctx, x+2, cy, x+W-2, cy+14, stateBg);
        hline(ctx, x+2, cy,    x+W-2, stateBord);
        hline(ctx, x+2, cy+13, x+W-2, stateBord);
        fill(ctx, x+PAD, cy+4, x+PAD+4, cy+10, stateBord);
        ctx.drawText(textRenderer, Text.literal(data.state()).formatted(Formatting.WHITE),
                x+PAD+8, cy+3, stateBord, false);
        cy += 15;

        // Power output
        hline(ctx, x+2, cy, x+W-2, COL_DIM);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal("Power output").formatted(Formatting.GOLD), x+PAD, cy, 0xFF886622, false);
        cy += 11;
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.powerOutput(), 100, COL_ACCENT, data.powerOutput() + "%");
        cy += 13;

        // Fuel / energy
        hline(ctx, x+2, cy, x+W-2, COL_DIM2);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal(data.fuelLabel()).formatted(Formatting.GOLD), x+PAD, cy, 0xFF886622, false);
        cy += 11;
        int fuelCol = data.fuelPercent() > 40 ? COL_ONLINE : data.fuelPercent() > 15 ? COL_UNSTABLE : COL_CRITICAL;
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.fuelPercent(), 100, fuelCol,
                data.fuelRods() + " / " + data.maxFuelRods());
        cy += 13;

        // Stability
        hline(ctx, x+2, cy, x+W-2, COL_DIM2);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal("Stability").formatted(Formatting.GOLD), x+PAD, cy, 0xFF886622, false);
        cy += 11;
        int stabCol = data.stability() > 60 ? COL_ONLINE : data.stability() > 30 ? COL_UNSTABLE : COL_CRITICAL;
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.stability(), 100, stabCol, data.stability() + "%");
        cy += 13;

        // Resonance coil
        hline(ctx, x+2, cy, x+W-2, COL_DIM2);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal("Resonance coil").formatted(Formatting.GOLD), x+PAD, cy, 0xFF886622, false);
        cy += 11;
        if (data.coilHealth() < 0) {
            ctx.drawText(textRenderer,
                    Text.literal("Not linked — /warpcore linkcoil <id>").formatted(Formatting.DARK_GRAY),
                    x+PAD, cy+1, 0xFF555544, false);
        } else {
            int coilCol = data.coilHealth() > 60 ? COL_ONLINE : data.coilHealth() > 35 ? COL_UNSTABLE : COL_CRITICAL;
            drawBar(ctx, x+PAD, cy, W-PAD*2-40, 8, data.coilHealth(), 100, coilCol,
                    data.coilHealth() + "/100");
            ctx.drawText(textRenderer,
                    Text.literal("×" + data.coilCount()).formatted(Formatting.DARK_GRAY),
                    x+W-PAD-textRenderer.getWidth("×" + data.coilCount()), cy, 0xFF444433, false);
        }
        cy += 13;

        drawFooterDivider(ctx, x, y);
    }

    private void drawBar(DrawContext ctx, int x, int y, int w, int h,
                         int value, int max, int fillCol, String label) {
        fill(ctx, x, y, x+w, y+h, COL_DARK_PNL);
        int fw = max > 0 ? Math.max(0, w * value / max) : 0;
        if (fw > 0) fill(ctx, x+1, y+1, x+fw, y+h-1, fillCol);
        // Draw label to the right, but clamp so it stays inside the panel
        int labelX = Math.min(x + w + 4, ox + W - PAD - textRenderer.getWidth(label));
        ctx.drawText(textRenderer, Text.literal(label), labelX, y, 0xFFCCCCCC, false);
    }

    private void drawFooterDivider(DrawContext ctx, int x, int y) {
        hline(ctx, x+2, y+H-32, x+W-2, COL_BORDER);
        fill(ctx, x+2, y+H-32, x+W-2, y+H-2, COL_HEADER_BG);
    }

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1>x0 && y1>y0) ctx.fill(x0, y0, x1, y1, col);
    }
    private void hline(DrawContext ctx, int x0, int y, int x1, int col) { ctx.fill(x0, y, x1, y+1, col); }
    private void vline(DrawContext ctx, int x, int y0, int y1, int col) { ctx.fill(x, y0, x+1, y1, col); }

    private static int stateCol(String s) {
        return switch (s) {
            case "ONLINE"   -> 0xFF2D8214;
            case "STARTING" -> 0xFF1A6A7A;
            case "UNSTABLE" -> 0xFFB97308;
            case "CRITICAL" -> 0xFFC84608;
            case "FAILED"   -> 0xFF8A1010;
            default         -> 0xFF444441;
        };
    }

    private static int dimOf(int col) {
        int r = ((col >> 16) & 0xFF) / 6;
        int g = ((col >> 8)  & 0xFF) / 6;
        int b = ( col        & 0xFF) / 6;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}