package net.shard.seconddawnrp.warpcore.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;

/**
 * Fully code-drawn Warp Core Monitor screen.
 * Opened by right-clicking the Warp Core Controller block or holding
 * the Warp Core Tool and right-clicking in air.
 * Dark navy + amber theme matching the Engineering Pad.
 */
public class WarpCoreMonitorScreen extends Screen {

    private static final int W = 260;
    private static final int H = 200;
    private static final int PAD = 6;

    // Palette — matches EngineeringPadScreen exactly
    private static final int COL_BG        = 0xFF03091A;
    private static final int COL_BG2       = 0xFF07101F;
    private static final int COL_HEADER_BG = 0xFF050B1A;
    private static final int COL_BORDER    = 0xFFB96408;
    private static final int COL_ACCENT    = 0xFFD7820A;
    private static final int COL_DIM       = 0xFF502C04;
    private static final int COL_DIM2      = 0xFF1E0F02;
    private static final int COL_DARK_PNL  = 0xFF02050F;
    private static final int COL_ROW_DIV   = 0xFF0A1226;

    private static final int COL_ONLINE   = 0xFF2D8214;
    private static final int COL_STARTING = 0xFF1A6A7A;
    private static final int COL_UNSTABLE = 0xFFB97308;
    private static final int COL_CRITICAL = 0xFFC84608;
    private static final int COL_FAILED   = 0xFF8A1010;
    private static final int COL_OFFLINE  = 0xFF444441;

    private WarpCoreStatusS2CPacket data;
    private int ox, oy;

    public WarpCoreMonitorScreen(WarpCoreStatusS2CPacket data) {
        super(Text.literal("Warp Core Monitor"));
        this.data = data;
    }

    public void updateData(WarpCoreStatusS2CPacket data) {
        this.data = data;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        this.renderBackground(ctx, mx, my, delta);
        drawPanel(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void drawPanel(DrawContext ctx) {
        int x = ox, y = oy;

        fill(ctx, x, y, x+W, y+H, COL_BG);
        hline(ctx, x, y,    x+W, COL_BORDER);
        hline(ctx, x, y+H-1,x+W, COL_BORDER);
        vline(ctx, x,   y, y+H, COL_BORDER);
        vline(ctx, x+W-1,y, y+H, COL_BORDER);
        hline(ctx, x+1, y+1,    x+W-1, COL_DIM);
        hline(ctx, x+1, y+H-2, x+W-1, COL_DIM);
        vline(ctx, x+1,   y+1, y+H-1, COL_DIM);
        vline(ctx, x+W-2, y+1, y+H-1, COL_DIM);

        int cy = y + 2;

        // ── Title bar ─────────────────────────────────────────────────────────
        fill(ctx, x+2, cy, x+W-2, cy+13, COL_HEADER_BG);
        hline(ctx, x+2, cy+13, x+W-2, COL_BORDER);

        int[] dots = {0xFFAA2323, 0xFFA08210, COL_ACCENT};
        for (int i = 0; i < 3; i++) fill(ctx, x+PAD+i*9, cy+4, x+PAD+i*9+5, cy+9, dots[i]);
        ctx.drawText(textRenderer, Text.literal("Warp Core Monitor").formatted(Formatting.GOLD),
                x+32, cy+3, 0xFFFFFFFF, false);
        cy += 14;

        if (data == null || !data.registered()) {
            ctx.drawText(textRenderer,
                    Text.literal("No warp core registered.").formatted(Formatting.DARK_GRAY),
                    x+PAD, cy+8, 0xFF666666, false);
            drawFooter(ctx, x, y);
            return;
        }

        // ── State banner ──────────────────────────────────────────────────────
        int stateBg = switch (data.state()) {
            case "ONLINE"    -> 0xFF021402;
            case "STARTING"  -> 0xFF011418;
            case "UNSTABLE"  -> 0xFF180E02;
            case "CRITICAL"  -> 0xFF180402;
            case "FAILED"    -> 0xFF140202;
            default          -> 0xFF080810;
        };
        int stateBord = stateCol(data.state());
        fill(ctx, x+2, cy, x+W-2, cy+14, stateBg);
        hline(ctx, x+2, cy,    x+W-2, stateBord);
        hline(ctx, x+2, cy+13, x+W-2, stateBord);
        fill(ctx, x+PAD, cy+4, x+PAD+4, cy+10, stateBord);
        ctx.drawText(textRenderer, Text.literal(data.state()).formatted(Formatting.WHITE),
                x+PAD+8, cy+3, stateBord, false);
        cy += 15;

        // ── Power output bar ──────────────────────────────────────────────────
        hline(ctx, x+2, cy, x+W-2, COL_DIM);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal("Power output").formatted(Formatting.GOLD),
                x+PAD, cy, 0xFF886622, false);
        cy += 11;
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.powerOutput(), 100, COL_ACCENT, data.powerOutput() + "%");
        cy += 12;

        // ── Fuel level bar ────────────────────────────────────────────────────
        hline(ctx, x+2, cy, x+W-2, COL_DIM2);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal(data.fuelLabel()).formatted(Formatting.GOLD),
                x+PAD, cy, 0xFF886622, false);
        cy += 11;
        int fuelCol = data.fuelPercent() > 40 ? COL_ONLINE
                : data.fuelPercent() > 15 ? COL_UNSTABLE : COL_CRITICAL;
        String fuelStr = data.fuelRods() + " / " + data.maxFuelRods();
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.fuelPercent(), 100, fuelCol, fuelStr);
        cy += 12;

        // ── Stability bar ─────────────────────────────────────────────────────
        hline(ctx, x+2, cy, x+W-2, COL_DIM2);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal("Stability").formatted(Formatting.GOLD),
                x+PAD, cy, 0xFF886622, false);
        cy += 11;
        int stabCol = data.stability() > 60 ? COL_ONLINE
                : data.stability() > 30 ? COL_UNSTABLE : COL_CRITICAL;
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.stability(), 100, stabCol, data.stability() + "%");
        cy += 12;

        // ── Resonance coil bar ────────────────────────────────────────────────
        hline(ctx, x+2, cy, x+W-2, COL_DIM2);
        cy += 2;
        ctx.drawText(textRenderer, Text.literal("Resonance coil").formatted(Formatting.GOLD),
                x+PAD, cy, 0xFF886622, false);
        cy += 11;
        int coilCol = data.coilHealth() > 60 ? COL_ONLINE
                : data.coilHealth() > 35 ? COL_UNSTABLE : COL_CRITICAL;
        drawBar(ctx, x+PAD, cy, W-PAD*2, 8, data.coilHealth(), 100, coilCol, data.coilHealth() + "/100");
        cy += 12;

        // ── Commands hint ─────────────────────────────────────────────────────
        hline(ctx, x+2, cy, x+W-2, COL_DIM);
        cy += 3;
        ctx.drawText(textRenderer,
                Text.literal("/warpcore startup  |  shutdown  |  fuel add <n>")
                        .formatted(Formatting.DARK_GRAY),
                x+PAD, cy, 0xFF333320, false);

        drawFooter(ctx, x, y);
    }

    private void drawBar(DrawContext ctx, int x, int y, int w, int h,
                         int value, int max, int fillCol, String label) {
        fill(ctx, x, y, x+w, y+h, COL_DARK_PNL);
        int fw = max > 0 ? Math.max(1, w * value / max) : 0;
        if (fw > 0) fill(ctx, x+1, y+1, x+fw, y+h-1, fillCol);
        int labelX = x + w + 4;
        ctx.drawText(textRenderer, Text.literal(label), labelX, y, 0xFFCCCCCC, false);
    }

    private void drawFooter(DrawContext ctx, int x, int y) {
        int fy = y + H - 13;
        fill(ctx, x+2, fy, x+W-2, y+H-2, COL_HEADER_BG);
        hline(ctx, x+2, fy, x+W-2, COL_BORDER);
        fill(ctx, x+W-18, fy+3, x+W-12, fy+9, COL_ACCENT);
        fill(ctx, x+W-10, fy+3, x+W-4,  fy+9, 0xFF9B1C12);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1 > x0 && y1 > y0) ctx.fill(x0, y0, x1, y1, col);
    }
    private void hline(DrawContext ctx, int x0, int y, int x1, int col) { ctx.fill(x0, y, x1, y+1, col); }
    private void vline(DrawContext ctx, int x, int y0, int y1, int col) { ctx.fill(x, y0, x+1, y1, col); }

    private static int stateCol(String state) {
        return switch (state) {
            case "ONLINE"   -> 0xFF2D8214;
            case "STARTING" -> 0xFF1A6A7A;
            case "UNSTABLE" -> 0xFFB97308;
            case "CRITICAL" -> 0xFFC84608;
            case "FAILED"   -> 0xFF8A1010;
            default         -> 0xFF444441;
        };
    }
}