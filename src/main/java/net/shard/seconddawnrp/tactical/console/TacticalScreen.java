package net.shard.seconddawnrp.tactical.console;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.*;

import java.util.List;

/**
 * Four-panel Tactical Console GUI.
 *
 * ┌────────────────────────┬────────────────────────┐
 * │   NAVIGATION (top-L)   │   WEAPONS (top-R)      │
 * │   Tactical map grid    │   Hardpoints, fire btn  │
 * ├────────────────────────┼────────────────────────┤
 * │   SHIELDS (bot-L)      │   STATUS (bot-R)        │
 * │   4-facing bars+sliders│   Hull, power, log      │
 * └────────────────────────┴────────────────────────┘
 */
public class TacticalScreen extends HandledScreen<TacticalScreenHandler> {

    private static final int GUI_W = 420;
    private static final int GUI_H = 260;

    // Panel bounds (relative to ox, oy)
    private static final int HALF_W = GUI_W / 2;
    private static final int HALF_H = (GUI_H - 22) / 2; // minus title bar
    private static final int PANEL_Y = 22;

    // Colors
    private static final int COL_BG        = 0xE0080E18;
    private static final int COL_TITLE     = 0xFF1A2840;
    private static final int COL_BORDER    = 0xFF1A4A6A;
    private static final int COL_HEADER    = 0xFF00AAFF;
    private static final int COL_FRIENDLY  = 0xFF3399FF;
    private static final int COL_HOSTILE   = 0xFFFF3333;
    private static final int COL_UNKNOWN   = 0xFFFFAA00;
    private static final int COL_TEXT      = 0xFFCCEEFF;
    private static final int COL_DIM       = 0xFF667799;
    private static final int COL_GREEN     = 0xFF33FF88;
    private static final int COL_RED       = 0xFFFF4444;
    private static final int COL_YELLOW    = 0xFFFFBB33;
    private static final int COL_BTN       = 0xFF0A2040;
    private static final int COL_BTN_HOV   = 0xFF1A3A60;
    private static final int COL_SHIELD_OK = 0xFF2266FF;
    private static final int COL_SHIELD_LOW= 0xFFFF6622;
    private static final int COL_HULL_OK   = 0xFF22CC44;
    private static final int COL_HULL_CRIT = 0xFFCC2222;

    // Map display — 40x40 unit grid mapped to pixels
    private static final float MAP_SCALE = 0.15f;
    private static final int MAP_OFFSET  = 40; // center of map panel

    // Active tab for mobile-friendly alternate layout (not used in 4-panel)
    private enum Panel { NAVIGATION, WEAPONS, SHIELDS, STATUS }

    public TacticalScreen(TacticalScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth  = GUI_W;
        this.backgroundHeight = GUI_H;
        this.playerInventoryTitleY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 0;
        this.titleY = 0;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        int ox = this.x, oy = this.y;

        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, COL_BG);
        ctx.fill(ox, oy, ox + GUI_W, oy + PANEL_Y, COL_TITLE);
        ctx.drawBorder(ox, oy, GUI_W, GUI_H, COL_BORDER);

        // Title
        String encounterInfo = handler.isStandby()
                ? "TACTICAL CONSOLE — STANDBY"
                : "TACTICAL CONSOLE — " + handler.getEncounterId()
                + " [" + handler.getEncounterStatus() + "]";
        ctx.drawText(textRenderer, encounterInfo,
                ox + (GUI_W - textRenderer.getWidth(encounterInfo)) / 2,
                oy + 7, COL_HEADER, false);

        // Cross dividers
        ctx.fill(ox + HALF_W, oy + PANEL_Y, ox + HALF_W + 1, oy + GUI_H, COL_BORDER);
        ctx.fill(ox, oy + PANEL_Y + HALF_H, ox + GUI_W, oy + PANEL_Y + HALF_H + 1, COL_BORDER);

        // Panel headers
        drawPanelHeader(ctx, ox, oy + PANEL_Y, HALF_W, "NAVIGATION", mx, my);
        drawPanelHeader(ctx, ox + HALF_W, oy + PANEL_Y, HALF_W, "WEAPONS", mx, my);
        drawPanelHeader(ctx, ox, oy + PANEL_Y + HALF_H, HALF_W, "SHIELDS", mx, my);
        drawPanelHeader(ctx, ox + HALF_W, oy + PANEL_Y + HALF_H, HALF_W, "STATUS", mx, my);

        // Draw all four panels
        drawNavigationPanel(ctx, ox + 4, oy + PANEL_Y + 12, HALF_W - 8, HALF_H - 16, mx, my);
        drawWeaponsPanel(ctx, ox + HALF_W + 4, oy + PANEL_Y + 12, HALF_W - 8, HALF_H - 16, mx, my);
        drawShieldsPanel(ctx, ox + 4, oy + PANEL_Y + HALF_H + 12, HALF_W - 8, HALF_H - 16, mx, my);
        drawStatusPanel(ctx, ox + HALF_W + 4, oy + PANEL_Y + HALF_H + 12, HALF_W - 8, HALF_H - 16, mx, my);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mx, int my) {}

    // ── Navigation Panel — Tactical Map ───────────────────────────────────────

    private void drawNavigationPanel(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        if (handler.isStandby()) {
            ctx.drawText(textRenderer, "§7No active encounter.", x + 4, y + 4, COL_DIM, false);
            ctx.drawText(textRenderer, "§8Awaiting GM encounter initiation.", x + 4, y + 14, COL_DIM, false);
            return;
        }

        // Map center
        int mapCX = x + w / 2;
        int mapCY = y + h / 2;

        // Range rings
        for (int ring : new int[]{30, 60, 90}) {
            drawCircleApprox(ctx, mapCX, mapCY, (int)(ring * MAP_SCALE), COL_BORDER);
        }

        // Draw each ship
        for (ShipSnapshot ship : handler.getShips()) {
            if (ship.destroyed()) continue;
            int sx = mapCX + (int)(ship.posX() * MAP_SCALE);
            int sz = mapCY + (int)(ship.posZ() * MAP_SCALE);
            int color = "FRIENDLY".equals(ship.faction()) ? COL_FRIENDLY : COL_HOSTILE;

            // Ship icon (3x3 dot)
            ctx.fill(sx - 2, sz - 2, sx + 2, sz + 2, color);

            // Heading indicator (short line)
            double rad = Math.toRadians(ship.heading());
            int hx = sx + (int)(Math.cos(rad) * 6);
            int hz = sz + (int)(Math.sin(rad) * 6);
            ctx.fill(sx, sz, hx, hz, color);

            // Combat ID label
            ctx.drawText(textRenderer, ship.combatId(), sx + 3, sz - 4, color, false);

            // Shield ring (4 colored dots around ship)
            drawShieldRing(ctx, sx, sz, ship);
        }

        // Helm controls if player has FRIENDLY ship
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps != null) {
            // Show current heading/speed at bottom of nav panel
            ctx.drawText(textRenderer,
                    "§bHDG: §f" + (int)ps.heading() + "°  §bSPD: §f" + String.format("%.1f", ps.speed()),
                    x + 4, y + h - 10, COL_TEXT, false);
        }
    }

    private void drawShieldRing(DrawContext ctx, int sx, int sz, ShipSnapshot ship) {
        int maxS = Math.max(1, ship.shieldFore() + ship.shieldAft() + ship.shieldPort() + ship.shieldStarboard());
        drawShieldDot(ctx, sx, sz - 5, ship.shieldFore(),   maxS / 4);  // FORE  = top
        drawShieldDot(ctx, sx, sz + 5, ship.shieldAft(),    maxS / 4);  // AFT   = bottom
        drawShieldDot(ctx, sx - 5, sz, ship.shieldPort(),   maxS / 4);  // PORT  = left
        drawShieldDot(ctx, sx + 5, sz, ship.shieldStarboard(), maxS / 4); // STBD = right
    }

    private void drawShieldDot(DrawContext ctx, int x, int y, int current, int max) {
        float pct = max <= 0 ? 0 : (float) current / max;
        int color = pct > 0.5f ? COL_SHIELD_OK
                : pct > 0.2f ? COL_YELLOW
                : pct > 0    ? COL_SHIELD_LOW
                : COL_RED;
        ctx.fill(x - 1, y - 1, x + 1, y + 1, color);
    }

    private void drawCircleApprox(DrawContext ctx, int cx, int cy, int r, int color) {
        // Approximate circle with 16 segments of dots
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16;
            int px = cx + (int)(Math.cos(angle) * r);
            int py = cy + (int)(Math.sin(angle) * r);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    // ── Weapons Panel ─────────────────────────────────────────────────────────

    private void drawWeaponsPanel(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        if (handler.isStandby()) return;

        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null) {
            ctx.drawText(textRenderer, "§7No friendly ship.", x + 4, y + 4, COL_DIM, false);
            return;
        }

        // Target selection
        ctx.drawText(textRenderer, "§bTARGETS:", x + 4, y, COL_HEADER, false);
        int rowY = y + 10;
        for (ShipSnapshot hostile : handler.getHostileShips()) {
            if (hostile.destroyed()) continue;
            boolean selected = hostile.shipId().equals(handler.getSelectedTargetId());
            boolean hov = inBounds(mx, my, x + 2, rowY - 1, w - 4, 10);
            if (selected) ctx.fill(x + 2, rowY - 1, x + w - 2, rowY + 9, 0x44FF3333);
            else if (hov) ctx.fill(x + 2, rowY - 1, x + w - 2, rowY + 9, 0x22FFFFFF);
            ctx.drawText(textRenderer,
                    (selected ? "§c► " : "§7○ ") + "§f" + hostile.combatId()
                            + " §8| Hull: " + hostile.hullIntegrity() + "/" + hostile.hullMax(),
                    x + 4, rowY, COL_TEXT, false);
            rowY += 12;
        }

        // Torpedo count
        rowY += 4;
        ctx.drawText(textRenderer, "§bTORPEDOES: §f" + ps.torpedoCount(), x + 4, rowY, COL_TEXT, false);
        rowY += 12;

        // Fire buttons
        boolean canFire = handler.getSelectedTargetId() != null;
        int btnY = y + h - 28;

        drawTacticalButton(ctx, x + 4, btnY, (w - 12) / 2, 13,
                "§c⚡ PHASERS", canFire, mx, my);
        drawTacticalButton(ctx, x + (w - 12) / 2 + 8, btnY, (w - 12) / 2, 13,
                "§e⦿ TORPEDO", canFire && ps.torpedoCount() > 0, mx, my);

        // Evasive maneuver button
        drawTacticalButton(ctx, x + 4, btnY + 16, w - 8, 11,
                "§b⟳ EVASIVE MANEUVER", true, mx, my);
    }

    // ── Shields Panel ─────────────────────────────────────────────────────────

    private void drawShieldsPanel(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        if (handler.isStandby()) return;

        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null) return;

        ctx.drawText(textRenderer, "§bSHIELD FACINGS:", x + 4, y, COL_HEADER, false);

        int barW = w - 60;
        int barH = 8;
        int labelW = 38;

        drawShieldBar(ctx, x + 4, y + 14, barW, barH, labelW,
                "FORE", ps.shieldFore(), ps.shieldFore() + ps.shieldAft() + ps.shieldPort() + ps.shieldStarboard());
        drawShieldBar(ctx, x + 4, y + 28, barW, barH, labelW,
                "AFT", ps.shieldAft(), ps.shieldFore() + ps.shieldAft() + ps.shieldPort() + ps.shieldStarboard());
        drawShieldBar(ctx, x + 4, y + 42, barW, barH, labelW,
                "PORT", ps.shieldPort(), ps.shieldFore() + ps.shieldAft() + ps.shieldPort() + ps.shieldStarboard());
        drawShieldBar(ctx, x + 4, y + 56, barW, barH, labelW,
                "STBD", ps.shieldStarboard(), ps.shieldFore() + ps.shieldAft() + ps.shieldPort() + ps.shieldStarboard());

        // Power info
        ctx.drawText(textRenderer,
                "§8Shield Power: " + ps.shieldsPower() + "/" + ps.powerBudget(),
                x + 4, y + 72, COL_DIM, false);

        // Redistribute button
        drawTacticalButton(ctx, x + 4, y + h - 14, w - 8, 12,
                "§b= BALANCE SHIELDS", true, mx, my);
    }

    private void drawShieldBar(DrawContext ctx, int x, int y, int barW, int barH,
                               int labelW, String label, int current, int max) {
        float pct = max <= 0 ? 0 : Math.min(1f, (float) current / (max / 4f));
        int color = pct > 0.5f ? COL_SHIELD_OK : pct > 0.2f ? COL_YELLOW : COL_RED;

        ctx.drawText(textRenderer, label, x, y, COL_DIM, false);
        int bx = x + labelW;
        ctx.fill(bx, y, bx + barW, y + barH, 0xFF111820);
        ctx.fill(bx, y, bx + (int)(barW * pct), y + barH, color);
        ctx.drawBorder(bx, y, barW, barH, COL_BORDER);
        ctx.drawText(textRenderer, current + "", bx + barW + 3, y, COL_TEXT, false);
    }

    // ── Status Panel ──────────────────────────────────────────────────────────

    private void drawStatusPanel(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        if (handler.isStandby()) {
            ctx.drawText(textRenderer, "§7TACTICAL ONLINE", x + 4, y + 4, COL_DIM, false);
            ctx.drawText(textRenderer, "§8Awaiting encounter.", x + 4, y + 14, COL_DIM, false);
            return;
        }

        ShipSnapshot ps = handler.getPlayerShip();
        int ty = y;

        if (ps != null) {
            // Hull integrity bar
            ctx.drawText(textRenderer, "§bHULL:", x + 4, ty, COL_HEADER, false);
            ty += 10;
            float hullPct = (float) ps.hullIntegrity() / Math.max(1, ps.hullMax());
            int hullColor = hullPct > 0.75f ? COL_HULL_OK
                    : hullPct > 0.5f ? COL_GREEN
                    : hullPct > 0.25f ? COL_YELLOW : COL_HULL_CRIT;
            ctx.fill(x + 4, ty, x + 4 + (w - 8), ty + 8, 0xFF111820);
            ctx.fill(x + 4, ty, x + 4 + (int)((w - 8) * hullPct), ty + 8, hullColor);
            ctx.drawBorder(x + 4, ty, w - 8, 8, COL_BORDER);
            ctx.drawText(textRenderer,
                    ps.hullIntegrity() + "/" + ps.hullMax() + " [" + ps.hullState() + "]",
                    x + 4, ty + 10, hullColor, false);
            ty += 22;

            // Power summary
            ctx.drawText(textRenderer, "§bPOWER:", x + 4, ty, COL_HEADER, false);
            ty += 10;
            ctx.drawText(textRenderer, "§8Budget: §f" + ps.powerBudget()
                            + "  §8Wpn: §c" + ps.weaponsPower()
                            + "  §8Shld: §b" + ps.shieldsPower(),
                    x + 4, ty, COL_TEXT, false);
            ty += 10;
            ctx.drawText(textRenderer, "§8Eng: §a" + ps.enginesPower()
                            + "  §8Sens: §e" + ps.sensorsPower()
                            + "  §8Warp: §f" + ps.warpSpeed(),
                    x + 4, ty, COL_TEXT, false);
            ty += 14;
        }

        // Combat log
        ctx.drawText(textRenderer, "§bENCOUNTER LOG:", x + 4, ty, COL_HEADER, false);
        ty += 10;
        List<String> log = handler.getCombatLog();
        int logVisible = (y + h - ty) / 8;
        int logStart = Math.max(0, log.size() - logVisible);
        for (int i = logStart; i < log.size() && ty < y + h - 4; i++) {
            String entry = log.get(i);
            // Strip timestamp prefix for display
            int bracket = entry.indexOf(']');
            String display = bracket >= 0 ? entry.substring(bracket + 2) : entry;
            if (display.length() > 38) display = display.substring(0, 36) + "..";
            ctx.drawText(textRenderer, "§8" + display, x + 4, ty, COL_DIM, false);
            ty += 8;
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || handler.isStandby()) return super.mouseClicked(mx, my, button);

        int ox = this.x, oy = this.y;
        int wpx = ox + HALF_W + 4;
        int wpy = oy + PANEL_Y + 12;
        int wpw = HALF_W - 8;
        int wph = HALF_H - 16;

        ShipSnapshot ps = handler.getPlayerShip();

        // Target selection (weapons panel)
        int rowY = wpy + 10;
        for (ShipSnapshot hostile : handler.getHostileShips()) {
            if (hostile.destroyed()) continue;
            if (inBounds(mx, my, wpx + 2, rowY - 1, wpw - 4, 10)) {
                handler.setSelectedTargetId(hostile.shipId());
                return true;
            }
            rowY += 12;
        }

        // Fire phasers
        if (ps != null) {
            int btnY = wpy + wph - 28;
            int halfBtnW = (wpw - 12) / 2;
            if (inBounds(mx, my, wpx + 4, btnY, halfBtnW, 13)
                    && handler.getSelectedTargetId() != null) {
                sendWeaponFire("PHASER");
                return true;
            }
            // Fire torpedo
            if (inBounds(mx, my, wpx + halfBtnW + 8, btnY, halfBtnW, 13)
                    && handler.getSelectedTargetId() != null && ps.torpedoCount() > 0) {
                sendWeaponFire("TORPEDO");
                return true;
            }
            // Evasive maneuver
            if (inBounds(mx, my, wpx + 4, btnY + 16, wpw - 8, 11)) {
                sendEvasive();
                return true;
            }

            // Balance shields
            int shpx = ox + 4;
            int shpy = oy + PANEL_Y + HALF_H + 12;
            int shph = HALF_H - 16;
            if (inBounds(mx, my, shpx + 4, shpy + shph - 14, HALF_W - 12, 12)) {
                sendBalanceShields();
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    // ── Packet sends ──────────────────────────────────────────────────────────

    private void sendWeaponFire(String type) {
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null || handler.getSelectedTargetId() == null) return;
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.WeaponFirePayload(
                handler.getEncounterId(), ps.shipId(),
                handler.getSelectedTargetId(), type));
    }

    private void sendEvasive() {
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null) return;
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.HelmInputPayload(
                handler.getEncounterId(), ps.shipId(), 0, 0, true));
    }

    private void sendBalanceShields() {
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null) return;
        // Equal distribution — 25% each
        int quarter = ps.powerBudget() / 4;
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.ShieldDistributePayload(
                handler.getEncounterId(), ps.shipId(),
                quarter, quarter, quarter, quarter));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawPanelHeader(DrawContext ctx, int x, int y, int w, String label, int mx, int my) {
        ctx.fill(x, y, x + w, y + 12, 0xFF0A1828);
        ctx.drawText(textRenderer, label,
                x + (w - textRenderer.getWidth(label)) / 2,
                y + 2, COL_HEADER, false);
    }

    private void drawTacticalButton(DrawContext ctx, int bx, int by, int bw, int bh,
                                    String label, boolean enabled, int mx, int my) {
        boolean hov = enabled && inBounds(mx, my, bx, by, bw, bh);
        int bg = !enabled ? 0xFF0A1020 : hov ? COL_BTN_HOV : COL_BTN;
        ctx.fill(bx, by, bx + bw, by + bh, bg);
        ctx.drawBorder(bx, by, bw, bh, enabled ? COL_BORDER : 0xFF222233);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label, bx + (bw - lw) / 2, by + (bh - 7) / 2 + 1,
                enabled ? 0xFFFFFFFF : COL_DIM, false);
    }

    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override public boolean shouldPause() { return false; }
}