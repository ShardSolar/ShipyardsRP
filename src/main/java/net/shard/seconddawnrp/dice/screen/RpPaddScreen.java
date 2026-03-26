package net.shard.seconddawnrp.dice.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.dice.network.OpenRpPaddS2CPacket;
import net.shard.seconddawnrp.dice.network.SignRpPaddC2SPacket;
import net.shard.seconddawnrp.dice.network.ToggleRpRecordingC2SPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * RP PADD screen — code-drawn, matching the Engineering PAD palette.
 *
 * Layout (all Y values relative to oy):
 *   y+2  .. y+15  — title bar
 *   y+18 .. y+38  — status row (dot + text + buttons)
 *   y+40 .. y+41  — divider
 *   y+43 .. y+54  — "Session Log" label
 *   y+54 .. y+187 — log rows (ROWS_VIS × ROW_H)
 *   y+187.. y+200 — footer
 */
public class RpPaddScreen extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xFF03091A;
    private static final int COL_BG2       = 0xFF07101F;
    private static final int COL_HEADER_BG = 0xFF050B1A;
    private static final int COL_BORDER    = 0xFFB96408;
    private static final int COL_ACCENT    = 0xFFD7820A;
    private static final int COL_DIM       = 0xFF502C04;
    private static final int COL_DARK_PNL  = 0xFF02050F;
    private static final int COL_ROW_DIV   = 0xFF0A1226;
    private static final int COL_TEXT_GOLD = 0xFFD4AA44;
    private static final int COL_TEXT_GRAY = 0xFF888888;
    private static final int COL_TEXT_WHITE= 0xFFFFFFFF;
    private static final int COL_GREEN     = 0xFF2D8214;
    private static final int COL_GREEN_BRT = 0xFF44CC22;
    private static final int COL_RED       = 0xFFA01C12;
    private static final int COL_RED_BRT   = 0xFFCC3300;
    private static final int COL_AMBER     = 0xFFB97308;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int W        = 260;
    private static final int H        = 200;
    private static final int PAD      = 6;
    private static final int ROW_H    = 11;
    private static final int ROWS_VIS = 9;

    // Y offsets (relative to oy) — single source of truth
    private static final int TITLE_Y  = 2;
    private static final int STATUS_Y = 18;   // status row top
    private static final int STATUS_H = 20;   // status row height
    private static final int LOG_LABEL_Y = STATUS_Y + STATUS_H + 4;
    private static final int LOG_Y    = LOG_LABEL_Y + 11;
    private static final int FOOTER_Y = H - 13;

    // Buttons inside status row
    // Toggle button: right side, full height of status row minus 2px padding
    private static final int BTN_TOGGLE_W = 62;
    private static final int BTN_SIGN_W   = 50;
    private static final int BTN_H        = 16;
    private static final int BTN_TOP_PAD  = 2; // padding from top of status row

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isRecording;
    private int entryCount;
    private final boolean isSigned;
    private final List<String> entries;
    private int scrollOffset = 0;
    private int ox, oy;

    // Hover state — recomputed each render from current ox/oy
    private boolean hoverToggle;
    private boolean hoverSign;
    private int hoveredRow = -1;

    private final long openedAtMs = System.currentTimeMillis();

    public RpPaddScreen(OpenRpPaddS2CPacket packet) {
        super(Text.literal("RP PADD"));
        this.isRecording = packet.isRecording();
        this.entryCount  = packet.entryCount();
        this.isSigned    = packet.isSigned();
        this.entries     = new ArrayList<>(packet.recentEntries());
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) {}

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width  - W) / 2;
        oy = (height - H) / 2;
        computeHover(mx, my);
        drawPanel(ctx, mx, my);
        super.render(ctx, mx, my, delta);
    }

    // ── Hover — computed from actual draw positions ────────────────────────────

    private void computeHover(int mx, int my) {
        int x = ox, y = oy;

        // Toggle button bounds (mirrors drawPanel exactly)
        int toggleX = x + W - PAD - BTN_TOGGLE_W;
        int toggleY = y + STATUS_Y + BTN_TOP_PAD;
        hoverToggle = !isSigned
                && mx >= toggleX && mx <= toggleX + BTN_TOGGLE_W
                && my >= toggleY && my <= toggleY + BTN_H;

        // Sign button — only when not recording and not already signed and has entries
        int signX = x + W - PAD - BTN_TOGGLE_W - 4 - BTN_SIGN_W;
        int signY = toggleY;
        hoverSign = !isSigned && !isRecording && entryCount > 0
                && mx >= signX && mx <= signX + BTN_SIGN_W
                && my >= signY && my <= signY + BTN_H;

        // Log row hover
        hoveredRow = -1;
        int logTop = y + LOG_Y;
        if (!entries.isEmpty() && mx >= x + PAD && mx <= x + W - PAD) {
            int rel = my - logTop;
            if (rel >= 0 && rel < ROWS_VIS * ROW_H) {
                int row = scrollOffset + rel / ROW_H;
                if (row < entries.size()) hoveredRow = row;
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;

        // Outer panel + borders
        fill(ctx, x, y, x+W, y+H, COL_BG);
        hborder(ctx, x, y,     x+W, COL_BORDER);
        hborder(ctx, x, y+H-1, x+W, COL_BORDER);
        vborder(ctx, x,     y, y+H, COL_BORDER);
        vborder(ctx, x+W-1, y, y+H, COL_BORDER);
        hborder(ctx, x+1, y+1,   x+W-1, COL_DIM);
        hborder(ctx, x+1, y+H-2, x+W-1, COL_DIM);
        vborder(ctx, x+1,   y+1, y+H-1, COL_DIM);
        vborder(ctx, x+W-2, y+1, y+H-1, COL_DIM);

        // Title bar
        fill(ctx, x+2, y+TITLE_Y, x+W-2, y+TITLE_Y+13, COL_HEADER_BG);
        hborder(ctx, x+2, y+TITLE_Y+13, x+W-2, COL_BORDER);
        int[] dotCols = {0xFFAA2323, 0xFFA08210, COL_ACCENT};
        for (int i = 0; i < 3; i++) fill(ctx, x+PAD+i*9, y+TITLE_Y+3, x+PAD+i*9+5, y+TITLE_Y+8, dotCols[i]);
        ctx.drawText(textRenderer, Text.literal("RP PADD").formatted(Formatting.GOLD),
                x+36, y+TITLE_Y+2, COL_TEXT_WHITE, false);
        if (isSigned) ctx.drawText(textRenderer, Text.literal("[SIGNED]"),
                x+W-60, y+TITLE_Y+2, COL_ACCENT, false);

        // Status row background
        int sY = y + STATUS_Y;
        fill(ctx, x+2, sY, x+W-2, sY+STATUS_H, COL_BG2);
        hborder(ctx, x+2, sY,           x+W-2, COL_DIM);
        hborder(ctx, x+2, sY+STATUS_H-1, x+W-2, COL_DIM);

        // Status dot — pulses when recording
        boolean pulse = ((System.currentTimeMillis() - openedAtMs) / 600) % 2 == 0;
        int dotCol = isRecording ? (pulse ? COL_RED_BRT : COL_RED) : COL_GREEN;
        fill(ctx, x+PAD+1, sY+7, x+PAD+7, sY+13, dotCol);

        // Status text
        String statusText = isRecording ? "RECORDING" : (isSigned ? "SIGNED" : "IDLE");
        int statusCol = isRecording ? COL_RED_BRT : (isSigned ? COL_AMBER : COL_GREEN_BRT);
        ctx.drawText(textRenderer, Text.literal(statusText), x+PAD+10, sY+6, statusCol, false);
        ctx.drawText(textRenderer, Text.literal(entryCount + " entries"),
                x+PAD+85, sY+6, COL_TEXT_GRAY, false);

        // Toggle button (Start / Stop)
        if (!isSigned) {
            int toggleX = x + W - PAD - BTN_TOGGLE_W;
            int toggleY = sY + BTN_TOP_PAD;
            drawButton(ctx, toggleX, toggleY, BTN_TOGGLE_W, BTN_H,
                    isRecording ? "■ Stop" : "● Start",
                    isRecording ? (hoverToggle ? COL_RED_BRT : COL_RED)
                            : (hoverToggle ? COL_GREEN_BRT : COL_GREEN),
                    hoverToggle);

            // Sign button — only when idle and has entries
            if (!isRecording && entryCount > 0) {
                int signX = toggleX - 4 - BTN_SIGN_W;
                drawButton(ctx, signX, toggleY, BTN_SIGN_W, BTN_H,
                        "✎ Sign",
                        hoverSign ? COL_ACCENT : COL_AMBER,
                        hoverSign);
            }
        }

        // Divider
        hborder(ctx, x+2, y + STATUS_Y + STATUS_H + 2, x+W-2, COL_BORDER);

        // Log label
        ctx.drawText(textRenderer, Text.literal("Session Log").formatted(Formatting.GOLD),
                x+PAD, y+LOG_LABEL_Y, COL_TEXT_GOLD, false);

        // Log entries
        int logTop = y + LOG_Y;
        if (entries.isEmpty()) {
            ctx.drawText(textRenderer,
                    Text.literal(isRecording
                                    ? "Recording... use /rp and /roll"
                                    : "No entries yet.")
                            .formatted(Formatting.DARK_GRAY),
                    x+PAD, logTop+2, COL_TEXT_GRAY, false);
        } else {
            int end = Math.min(scrollOffset + ROWS_VIS, entries.size());
            for (int i = scrollOffset; i < end; i++) {
                String entry = entries.get(i);
                int ry = logTop + (i - scrollOffset) * ROW_H;

                if (i > scrollOffset) hborder(ctx, x+PAD, ry, x+W-PAD, COL_ROW_DIV);

                // Highlight hovered row
                if (hoveredRow == i) fill(ctx, x+PAD, ry, x+W-PAD, ry+ROW_H, 0x22FFFFFF);

                int textCol = entry.contains("| ROLL |") ? COL_ACCENT
                        : entry.contains("| RP |") ? COL_TEXT_WHITE : COL_TEXT_GRAY;

                // Truncate to fit panel width
                String display = entry;
                int maxW = W - PAD * 2 - 8;
                while (display.length() > 1 && textRenderer.getWidth(display) > maxW) {
                    display = display.substring(0, display.length() - 1);
                }
                if (!display.equals(entry)) display += "…";

                ctx.drawText(textRenderer, Text.literal(display), x+PAD+2, ry+1, textCol, false);
            }

            // Scroll bar
            if (entries.size() > ROWS_VIS) {
                int trackH = ROWS_VIS * ROW_H;
                int trackX = x + W - PAD - 1;
                vborder(ctx, trackX, logTop, logTop + trackH, COL_DIM);
                int thumbH = Math.max(4, trackH * ROWS_VIS / entries.size());
                int thumbY = logTop + (trackH - thumbH) * scrollOffset
                        / Math.max(1, entries.size() - ROWS_VIS);
                fill(ctx, trackX, thumbY, trackX + 1, thumbY + thumbH, COL_ACCENT);
            }
        }

        // Tooltip for hovered row — full text
        if (hoveredRow >= 0 && hoveredRow < entries.size()) {
            String full = entries.get(hoveredRow);
            // Break into chunks of ~40 chars for tooltip width
            List<Text> lines = new ArrayList<>();
            while (full.length() > 40) {
                int cut = full.lastIndexOf(' ', 40);
                if (cut < 10) cut = 40;
                lines.add(Text.literal(full.substring(0, cut)).formatted(Formatting.WHITE));
                full = full.substring(cut).stripLeading();
            }
            if (!full.isBlank()) lines.add(Text.literal(full).formatted(Formatting.WHITE));
            ctx.drawTooltip(textRenderer, lines, mx, my);
        }

        // Footer
        fill(ctx, x+2, y+FOOTER_Y, x+W-2, y+H-2, COL_HEADER_BG);
        hborder(ctx, x+2, y+FOOTER_Y, x+W-2, COL_BORDER);
        String hint = isSigned ? "Submit at a Submission Box"
                : isRecording ? "/rp [action]  ·  /roll"
                : "Click Start to record";
        ctx.drawText(textRenderer, Text.literal(hint).formatted(Formatting.DARK_GRAY),
                x+PAD, y+FOOTER_Y+3, COL_TEXT_GRAY, false);
    }

    // ── Button helper ─────────────────────────────────────────────────────────

    private void drawButton(DrawContext ctx, int bx, int by, int bw, int bh,
                            String label, int borderCol, boolean hovered) {
        fill(ctx, bx, by, bx+bw, by+bh, hovered ? COL_BG2 : COL_DARK_PNL);
        hborder(ctx, bx, by,      bx+bw, borderCol);
        hborder(ctx, bx, by+bh-1, bx+bw, borderCol);
        vborder(ctx, bx,      by, by+bh, borderCol);
        vborder(ctx, bx+bw-1, by, by+bh, borderCol);
        int textCol = hovered ? COL_TEXT_WHITE : borderCol;
        ctx.drawText(textRenderer, Text.literal(label),
                bx + (bw - textRenderer.getWidth(label)) / 2,
                by + (bh - 8) / 2,
                textCol, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (hoverToggle) {
            boolean nowRecording = !isRecording;
            ClientPlayNetworking.send(new ToggleRpRecordingC2SPacket(nowRecording));
            isRecording = nowRecording;
            if (!nowRecording) { entries.clear(); entryCount = 0; }
            return true;
        }

        if (hoverSign) {
            ClientPlayNetworking.send(new SignRpPaddC2SPacket());
            this.close();
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, entries.size() - ROWS_VIS);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - v));
        return true;
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1 > x0 && y1 > y0) ctx.fill(x0, y0, x1, y1, col);
    }
    private void hborder(DrawContext ctx, int x0, int y, int x1, int col) { ctx.fill(x0, y, x1, y+1, col); }
    private void vborder(DrawContext ctx, int x, int y0, int y1, int col) { ctx.fill(x, y0, x+1, y1, col); }
}