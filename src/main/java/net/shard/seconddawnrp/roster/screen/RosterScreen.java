package net.shard.seconddawnrp.roster.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.roster.data.RosterActionC2SPacket;
import net.shard.seconddawnrp.roster.data.RosterEntry;
import net.shard.seconddawnrp.roster.data.ServiceRecordEntryDto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RosterScreen extends HandledScreen<RosterScreenHandler> {

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int GUI_W  = 420;
    private static final int GUI_H  = 230;

    private static final int LIST_X = 10;
    private static final int LIST_Y = 30;
    private static final int LIST_W = 140;
    private static final int LIST_H = 180;
    private static final int ROW_H  = 28;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private static final int RIGHT_X = 158;
    private static final int RIGHT_Y = 30;
    private static final int RIGHT_W = 252;
    private static final int RIGHT_H = 180;

    // Tab strip within right panel
    private static final int TAB_Y   = RIGHT_Y;
    private static final int TAB_H   = 14;
    private static final int TAB_W   = 60;

    // Content area below tabs
    private static final int CONTENT_Y = RIGHT_Y + TAB_H + 2;
    private static final int CONTENT_H = RIGHT_H - TAB_H - 2;

    // Action buttons below right panel
    private static final int BTN_X       = 158;
    private static final int BTN_START_Y = 218;
    private static final int BTN_W       = 110;
    private static final int BTN_H       = 13;
    private static final int BTN_GAP     = 15;
    private static final int BTN_COL2_X  = 278;

    private static final int FEEDBACK_Y = 220;

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private enum Tab { PROFILE, COMMENDATIONS, DEMERITS, RECORD }
    private Tab activeTab = Tab.PROFILE;

    private static final String[] TAB_LABELS = {"PROFILE", "COMMENDS", "DEMERITS", "RECORD"};

    // ── State ─────────────────────────────────────────────────────────────────

    private int listScroll    = 0;
    private int contentScroll = 0;

    private boolean awaitingGraduateInput = false;
    private boolean awaitingCommendInput  = false;
    private String inputBuffer  = "";
    private int commendPoints   = 25;

    private static final int COMMEND_MAX = 100;
    private static final int COMMEND_STEP = 5;

    // ── Colors ────────────────────────────────────────────────────────────────

    private static final int COL_HEADER  = 0xFF8FD7E8;
    private static final int COL_TEXT    = 0xFFF2E7D5;
    private static final int COL_DIM     = 0xFFAAAAAA;
    private static final int COL_GOLD    = 0xFFFFB24A;
    private static final int COL_GREEN   = 0xFF38FF9A;
    private static final int COL_RED     = 0xFFFF6060;
    private static final int COL_ONLINE  = 0xFF44FF88;
    private static final int COL_OFFLINE = 0xFF666666;
    private static final int COL_TAB_ACT = 0xFF1A3050;
    private static final int COL_TAB_IN  = 0xFF0A1828;
    private static final int COL_BG      = 0xE0101820;
    private static final int COL_BORDER  = 0xFF2A3C55;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());

    public RosterScreen(RosterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
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
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int ox = this.x, oy = this.y;

        // Main background
        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, COL_BG);

        // Title bar
        ctx.fill(ox, oy, ox + GUI_W, oy + 22, 0xFF1A2840);
        String title = "ROSTER — " + handler.getDivisionName();
        ctx.drawText(textRenderer, title,
                ox + (GUI_W - textRenderer.getWidth(title)) / 2,
                oy + 7, COL_HEADER, false);

        ctx.drawBorder(ox, oy, GUI_W, GUI_H, COL_BORDER);
        ctx.fill(ox + 4, oy + 22, ox + GUI_W - 4, oy + 23, COL_BORDER);

        // Panel labels
        drawCentered(ctx, "CREW", ox + LIST_X, oy + LIST_Y - 11, LIST_W, COL_HEADER);
        ctx.drawBorder(ox + LIST_X, oy + LIST_Y, LIST_W, LIST_H, COL_BORDER);

        drawMemberList(ctx, ox, oy, mouseX, mouseY);
        drawTabStrip(ctx, ox, oy, mouseX, mouseY);
        drawRightPanel(ctx, ox, oy, mouseX, mouseY);
        drawActionButtons(ctx, ox, oy, mouseX, mouseY);
        drawInputOverlay(ctx, ox, oy);
        drawFeedback(ctx, ox, oy);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {}

    // ── Member list ───────────────────────────────────────────────────────────

    private void drawMemberList(DrawContext ctx, int ox, int oy, int mx, int my) {
        List<RosterEntry> members = handler.getMembers();
        if (members.isEmpty()) {
            ctx.drawText(textRenderer, "No members.",
                    ox + LIST_X + 4, oy + LIST_Y + 4, COL_DIM, false);
            return;
        }

        clampListScroll(members.size());

        ctx.enableScissor(ox + LIST_X + 1, oy + LIST_Y + 1,
                ox + LIST_X + LIST_W - 1, oy + LIST_Y + LIST_H - 1);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i + listScroll;
            if (di >= members.size()) break;
            RosterEntry e = members.get(di);
            int rowX = ox + LIST_X + 2;
            int rowY = oy + LIST_Y + (i * ROW_H) + 1;
            boolean sel = di == handler.getSelectedIndex();

            ctx.fill(rowX, rowY, rowX + LIST_W - 4, rowY + ROW_H - 2,
                    sel ? 0x2A4488FF : 0x0A000000);

            int dot = e.isOnline() ? COL_ONLINE : COL_OFFLINE;
            ctx.fill(rowX + 2, rowY + 5, rowX + 5, rowY + 8, dot);

            ctx.drawText(textRenderer, trim(e.characterName(), 14),
                    rowX + 8, rowY + 2, e.isOnline() ? COL_TEXT : COL_DIM, false);
            ctx.drawText(textRenderer, "@" + trim(e.minecraftName(), 12),
                    rowX + 8, rowY + 11, 0xFF7FA0B8, false);
            ctx.drawText(textRenderer, rankAbbrev(e.rankId()),
                    rowX + 8, rowY + 20, rankColor(e.rankId()), false);
            ctx.drawText(textRenderer,
                    e.divisionName().substring(0, Math.min(3, e.divisionName().length())),
                    rowX + LIST_W - 26, rowY + 11, COL_DIM, false);
        }

        ctx.disableScissor();
        drawScrollArrows(ctx, ox + LIST_X, oy + LIST_Y, LIST_W, LIST_H,
                listScroll, members.size(), VISIBLE_ROWS);
    }

    // ── Tab strip ─────────────────────────────────────────────────────────────

    private void drawTabStrip(DrawContext ctx, int ox, int oy, int mx, int my) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tx = ox + RIGHT_X + i * (TAB_W + 2);
            int ty = oy + TAB_Y;
            boolean active = tabs[i] == activeTab;
            boolean hov = inBounds(mx, my, tx, ty, TAB_W, TAB_H);
            ctx.fill(tx, ty, tx + TAB_W, ty + TAB_H,
                    active ? COL_TAB_ACT : (hov ? 0xFF142030 : COL_TAB_IN));
            ctx.drawBorder(tx, ty, TAB_W, TAB_H,
                    active ? COL_HEADER : COL_BORDER);
            int lw = textRenderer.getWidth(TAB_LABELS[i]);
            ctx.drawText(textRenderer, TAB_LABELS[i],
                    tx + (TAB_W - lw) / 2, ty + 3,
                    active ? COL_HEADER : COL_DIM, false);
        }
    }

    // ── Right panel content ───────────────────────────────────────────────────

    private void drawRightPanel(DrawContext ctx, int ox, int oy, int mx, int my) {
        RosterEntry e = handler.getSelected();

        // Content area border
        int cx = ox + RIGHT_X;
        int cy = oy + CONTENT_Y;
        ctx.drawBorder(cx - 2, cy - 2, RIGHT_W + 4, CONTENT_H + 4, COL_BORDER);

        if (e == null) {
            ctx.drawText(textRenderer, "Select a crew member.",
                    cx + 4, cy + 4, COL_DIM, false);
            return;
        }

        switch (activeTab) {
            case PROFILE       -> drawProfileTab(ctx, cx, cy, e);
            case COMMENDATIONS -> drawHistoryTab(ctx, cx, cy, mx, my, e, true, false);
            case DEMERITS      -> drawHistoryTab(ctx, cx, cy, mx, my, e, false, true);
            case RECORD        -> drawHistoryTab(ctx, cx, cy, mx, my, e, false, false);
        }
    }

    private void drawProfileTab(DrawContext ctx, int cx, int cy, RosterEntry e) {
        List<String[]> lines = buildProfileLines(e);
        int visible = CONTENT_H / 11;
        clampContentScroll(lines.size(), visible);

        ctx.enableScissor(cx - 1, cy - 1, cx + RIGHT_W + 1, cy + CONTENT_H + 1);
        int ty = cy + 3;
        for (int i = 0; i < visible; i++) {
            int di = i + contentScroll;
            if (di >= lines.size()) break;
            String[] row = lines.get(di);
            int lw = textRenderer.getWidth(row[0]);
            ctx.drawText(textRenderer, row[0], cx + 4, ty, COL_DIM, false);
            ctx.drawText(textRenderer, row[1], cx + 4 + lw + 2, ty,
                    Integer.parseInt(row[2], 16), false);
            ty += 11;
        }
        ctx.disableScissor();
        drawScrollArrows(ctx, cx - 2, cy - 2, RIGHT_W + 4, CONTENT_H + 4,
                contentScroll, lines.size(), visible);
    }

    private void drawHistoryTab(DrawContext ctx, int cx, int cy, int mx, int my,
                                RosterEntry e, boolean commendOnly, boolean demeritOnly) {
        List<ServiceRecordEntryDto> all = handler.getServiceRecords(e.playerUuidStr());
        List<ServiceRecordEntryDto> filtered = new ArrayList<>();
        for (ServiceRecordEntryDto dto : all) {
            if (commendOnly && dto.pointsDelta() <= 0) continue;
            if (demeritOnly && dto.pointsDelta() >= 0) continue;
            if (!commendOnly && !demeritOnly && dto.pointsDelta() != 0
                    && (dto.type().equals("COMMENDATION") || dto.type().equals("DEMERIT"))) continue;
            filtered.add(dto);
        }
        // For RECORD tab, show everything except pure commend/demerit entries
        if (!commendOnly && !demeritOnly) {
            filtered.clear();
            for (ServiceRecordEntryDto dto : all) {
                String t = dto.type();
                if (!t.equals("COMMENDATION") && !t.equals("DEMERIT")) filtered.add(dto);
            }
        }

        int rowH = 22;
        int visible = CONTENT_H / rowH;
        int maxScroll = Math.max(0, filtered.size() - visible);
        if (contentScroll > maxScroll) contentScroll = maxScroll;

        if (filtered.isEmpty()) {
            ctx.drawText(textRenderer, "§7No entries yet.", cx + 4, cy + 4, COL_DIM, false);
            return;
        }

        ctx.enableScissor(cx - 1, cy - 1, cx + RIGHT_W + 1, cy + CONTENT_H + 1);
        int ty = cy + 3;
        for (int i = 0; i < visible; i++) {
            int di = i + contentScroll;
            if (di >= filtered.size()) break;
            ServiceRecordEntryDto dto = filtered.get(di);

            // Points badge
            String pts = dto.pointsLabel();
            if (!pts.isEmpty()) {
                int ptsColor = dto.pointsDelta() > 0 ? COL_GOLD : COL_RED;
                ctx.drawText(textRenderer, pts, cx + 4, ty, ptsColor, false);
            }

            // Type label
            int labelX = pts.isEmpty() ? cx + 4 : cx + 4 + textRenderer.getWidth(pts) + 4;
            ctx.drawText(textRenderer, dto.typeLabel(), labelX, ty, dto.typeColor(), false);

            // Date (right-aligned)
            String date = DATE_FMT.format(Instant.ofEpochMilli(dto.timestamp()));
            ctx.drawText(textRenderer, date,
                    cx + RIGHT_W - textRenderer.getWidth(date) - 4, ty, COL_DIM, false);

            // Reason + actor (second line)
            String detail = dto.reason().isBlank()
                    ? "— " + dto.actorName()
                    : trim(dto.reason(), 32) + " — " + dto.actorName();
            ctx.drawText(textRenderer, "§8" + detail, cx + 8, ty + 10, COL_DIM, false);

            ty += rowH;
        }
        ctx.disableScissor();
        drawScrollArrows(ctx, cx - 2, cy - 2, RIGHT_W + 4, CONTENT_H + 4,
                contentScroll, filtered.size(), visible);
    }

    // ── Profile lines ─────────────────────────────────────────────────────────

    private List<String[]> buildProfileLines(RosterEntry e) {
        List<String[]> lines = new ArrayList<>();
        String online = e.isOnline() ? " ●" : " ○";
        lines.add(row("Name:", e.characterName() + online,
                e.isOnline() ? "38FF9A" : "AAAAAA"));
        lines.add(row("User:", e.minecraftName(), "888888"));
        lines.add(row("Rank:", e.rankDisplayName() + (e.mustang() ? " ⋆" : ""),
                rankColorHex(e.rankId())));
        lines.add(row("Division:", e.divisionName(), "8FD7E8"));
        lines.add(row("Path:", e.progressionPath(), "FFB24A"));
        if (!e.shipPosition().equals("NONE"))
            lines.add(row("Position:", e.shipPosition().replace("_", " "), "FFD700"));
        lines.add(row("Rank Pts:", String.valueOf(e.rankPoints()), "F2E7D5"));
        lines.add(row("Service:", String.valueOf(e.serviceRecord()), "F2E7D5"));
        if (e.pointsToNextRank() > 0)
            lines.add(row("To Next:", e.pointsToNextRank() + " pts", "AAAAAA"));
        if (!e.certifications().isEmpty())
            lines.add(row("Certs:", String.join(", ", e.certifications()), "8FD7E8"));
        if (e.mustang())
            lines.add(row("", "★ Mustang", "FFB24A"));
        return lines;
    }

    private String[] row(String label, String value, String colorHex) {
        return new String[]{label, value, colorHex};
    }

    // ── Action buttons ────────────────────────────────────────────────────────

    private void drawActionButtons(DrawContext ctx, int ox, int oy, int mx, int my) {
        RosterEntry sel = handler.getSelected();
        if (sel == null) return;

        boolean isCadet = isCadetRank(sel.rankId());
        int y = oy + BTN_START_Y;

        if (handler.canManageRanks() && !isCadet) {
            drawBtn(ctx, ox + BTN_X, y, BTN_W, BTN_H, "▲ PROMOTE", mx, my, 0x1A22FF44);
            drawBtn(ctx, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H, "▼ DEMOTE", mx, my, 0x1AFF4422);
        }
        if (handler.isAdmin() || handler.canManageRanks()) {
            if (!isCadet) {
                int cadetY = y + (handler.canManageRanks() ? BTN_GAP * 2 : 0);
                drawBtn(ctx, ox + BTN_X, cadetY + BTN_GAP, BTN_W, BTN_H,
                        "ENROL CADET", mx, my, 0x1A4488FF);
            } else {
                drawBtn(ctx, ox + BTN_X, y, BTN_W, BTN_H, "CADET PROMOTE", mx, my, 0x1A4488FF);
                drawBtn(ctx, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H, "PROPOSE GRAD", mx, my, 0x1A44FFCC);
                drawBtn(ctx, ox + BTN_X, y + BTN_GAP * 2, BTN_W, BTN_H, "APPROVE GRAD", mx, my, 0x1A88FF44);
            }
        }
        if (handler.canManageMembers()) {
            drawBtn(ctx, ox + BTN_COL2_X, y, BTN_W, BTN_H, "TRANSFER DIV", mx, my, 0x1AFFAA22);
            drawBtn(ctx, ox + BTN_COL2_X, y + BTN_GAP, BTN_W, BTN_H, "DISMISS", mx, my, 0x1AFF2222);
        }
        if (handler.canCommend()) {
            drawBtn(ctx, ox + BTN_COL2_X, y + BTN_GAP * 2, BTN_W, BTN_H,
                    "COMMEND / DEMERIT", mx, my, 0x1AFFDD00);
        }
    }

    private void drawBtn(DrawContext ctx, int bx, int by, int bw, int bh,
                         String label, int mx, int my, int bg) {
        boolean hov = inBounds(mx, my, bx, by, bw, bh);
        ctx.fill(bx, by, bx + bw, by + bh, hov ? (bg | 0x3F000000) : bg);
        ctx.drawBorder(bx, by, bw, bh, COL_BORDER);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label, bx + (bw - lw) / 2, by + (bh - 7) / 2 + 1,
                COL_TEXT, false);
    }

    // ── Input overlay ─────────────────────────────────────────────────────────

    private void drawInputOverlay(DrawContext ctx, int ox, int oy) {
        if (!awaitingGraduateInput && !awaitingCommendInput) return;
        int px = ox + BTN_X - 4, py = oy + BTN_START_Y - 20;
        int pw = BTN_COL2_X - BTN_X + BTN_W + 8, ph = 60;
        ctx.fill(px, py, px + pw, py + ph, 0xCC101820);
        ctx.drawBorder(px, py, pw, ph, 0xFF8FD7E8);

        if (awaitingGraduateInput) {
            ctx.drawText(textRenderer, "Starting rank ID:", ox + BTN_X, py + 4, COL_HEADER, false);
            ctx.drawText(textRenderer, "> " + inputBuffer + "_", ox + BTN_X, py + 16, COL_GREEN, false);
            ctx.drawText(textRenderer, "Enter = confirm   Esc = cancel", ox + BTN_X, py + 28, COL_DIM, false);
        } else {
            ParsedCommend p = parseCommend(inputBuffer, commendPoints);
            String mode = p.points() >= 0 ? "COMMEND" : "DEMERIT";
            int mc = p.points() >= 0 ? COL_GOLD : COL_RED;
            ctx.drawText(textRenderer, mode + " (" + p.points() + " pts):", ox + BTN_X, py + 4, mc, false);
            ctx.drawText(textRenderer, "> " + inputBuffer + "_", ox + BTN_X, py + 16, mc, false);
            ctx.drawText(textRenderer, "Enter = confirm   Esc = cancel", ox + BTN_X, py + 28, COL_DIM, false);
            ctx.drawText(textRenderer, "Tip: start with +15 or -10 to set points",
                    ox + BTN_X, py + 40, COL_DIM, false);
        }
    }

    private void drawFeedback(DrawContext ctx, int ox, int oy) {
        String msg = handler.getFeedbackMessage();
        if (msg.isBlank()) return;
        int fw = textRenderer.getWidth(msg);
        ctx.fill(ox + (GUI_W - fw - 8) / 2, oy + FEEDBACK_Y - 2,
                ox + (GUI_W + fw + 8) / 2, oy + FEEDBACK_Y + 10, 0xCC1A2840);
        ctx.drawText(textRenderer, msg, ox + (GUI_W - fw) / 2, oy + FEEDBACK_Y, COL_GREEN, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ox = this.x, oy = this.y;

        if (awaitingGraduateInput || awaitingCommendInput)
            return super.mouseClicked(mx, my, button);

        // Tab clicks
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tx = ox + RIGHT_X + i * (TAB_W + 2);
            int ty = oy + TAB_Y;
            if (inBounds(mx, my, tx, ty, TAB_W, TAB_H)) {
                activeTab = tabs[i];
                contentScroll = 0;
                return true;
            }
        }

        // Member list clicks
        List<RosterEntry> members = handler.getMembers();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i + listScroll;
            if (di >= members.size()) break;
            int rowX = ox + LIST_X + 2;
            int rowY = oy + LIST_Y + (i * ROW_H) + 1;
            if (inBounds(mx, my, rowX, rowY, LIST_W - 4, ROW_H - 2)) {
                handler.setSelectedIndex(di);
                contentScroll = 0;
                return true;
            }
        }

        // Action buttons
        RosterEntry sel = handler.getSelected();
        if (sel != null) handleBtnClicks(mx, my, ox, oy, sel);

        return super.mouseClicked(mx, my, button);
    }

    private void handleBtnClicks(double mx, double my, int ox, int oy, RosterEntry sel) {
        boolean isCadet = isCadetRank(sel.rankId());
        int y = oy + BTN_START_Y;

        if (handler.canManageRanks() && !isCadet) {
            if (inBounds(mx, my, ox + BTN_X, y, BTN_W, BTN_H)) {
                send("PROMOTE", sel, "", 0); return;
            }
            if (inBounds(mx, my, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H)) {
                send("DEMOTE", sel, "", 0); return;
            }
        }
        if (handler.isAdmin() || handler.canManageRanks()) {
            if (!isCadet) {
                int cy = y + (handler.canManageRanks() ? BTN_GAP * 3 : 0);
                if (inBounds(mx, my, ox + BTN_X, cy, BTN_W, BTN_H)) {
                    send("CADET_ENROL", sel, "", 0); return;
                }
            } else {
                if (inBounds(mx, my, ox + BTN_X, y, BTN_W, BTN_H)) {
                    send("CADET_PROMOTE", sel, "", 0); return;
                }
                if (inBounds(mx, my, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H)) {
                    awaitingGraduateInput = true; inputBuffer = "ensign"; return;
                }
                if (inBounds(mx, my, ox + BTN_X, y + BTN_GAP * 2, BTN_W, BTN_H)) {
                    send("CADET_APPROVE", sel, "", 0); return;
                }
            }
        }
        if (handler.canManageMembers()) {
            if (inBounds(mx, my, ox + BTN_COL2_X, y, BTN_W, BTN_H)) {
                send("TRANSFER", sel, "UNASSIGNED", 0); return;
            }
            if (inBounds(mx, my, ox + BTN_COL2_X, y + BTN_GAP, BTN_W, BTN_H)) {
                send("DISMISS", sel, "", 0); return;
            }
        }
        if (handler.canCommend()) {
            if (inBounds(mx, my, ox + BTN_COL2_X, y + BTN_GAP * 2, BTN_W, BTN_H)) {
                awaitingCommendInput = true; inputBuffer = ""; commendPoints = 25;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int ox = this.x, oy = this.y;
        int delta = vAmt > 0 ? -1 : 1;

        if (inBounds(mx, my, ox + LIST_X, oy + LIST_Y, LIST_W, LIST_H)) {
            listScroll = clamp(listScroll + delta, 0,
                    Math.max(0, handler.getMembers().size() - VISIBLE_ROWS));
            return true;
        }
        if (inBounds(mx, my, ox + RIGHT_X, oy + CONTENT_Y, RIGHT_W, CONTENT_H)) {
            contentScroll = clamp(contentScroll + delta, 0,
                    Math.max(0, getContentLineCount() - getContentVisible()));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    private int getContentLineCount() {
        RosterEntry e = handler.getSelected();
        if (e == null) return 0;
        return switch (activeTab) {
            case PROFILE -> buildProfileLines(e).size();
            case COMMENDATIONS -> (int) handler.getServiceRecords(e.playerUuidStr())
                    .stream().filter(d -> d.pointsDelta() > 0).count();
            case DEMERITS -> (int) handler.getServiceRecords(e.playerUuidStr())
                    .stream().filter(d -> d.pointsDelta() < 0).count();
            case RECORD -> (int) handler.getServiceRecords(e.playerUuidStr())
                    .stream().filter(d -> !d.type().equals("COMMENDATION")
                            && !d.type().equals("DEMERIT")).count();
        };
    }

    private int getContentVisible() {
        return activeTab == Tab.PROFILE ? CONTENT_H / 11 : CONTENT_H / 22;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (awaitingGraduateInput || awaitingCommendInput) {
            if (key == 256) { // Esc
                awaitingGraduateInput = awaitingCommendInput = false;
                inputBuffer = "";
                return true;
            }
            if (key == 257 || key == 335) { // Enter
                RosterEntry sel = handler.getSelected();
                if (sel != null) {
                    if (awaitingGraduateInput) {
                        send("CADET_GRADUATE", sel, inputBuffer.trim(), 0);
                    } else {
                        ParsedCommend p = parseCommend(inputBuffer, commendPoints);
                        send("COMMEND", sel, p.reason(), p.points());
                    }
                }
                awaitingGraduateInput = awaitingCommendInput = false;
                inputBuffer = "";
                return true;
            }
            if (key == 259 && !inputBuffer.isEmpty()) {
                inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
                return true;
            }
            if (awaitingCommendInput) {
                if (key == 93 || key == 334) { commendPoints = adjPts(commendPoints + COMMEND_STEP); return true; }
                if (key == 45 || key == 333) { commendPoints = adjPts(commendPoints - COMMEND_STEP); return true; }
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (awaitingGraduateInput || awaitingCommendInput) {
            if (chr >= 32 && inputBuffer.length() < 96) inputBuffer += chr;
            return true;
        }
        return super.charTyped(chr, mods);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void send(String action, RosterEntry target, String strArg, int intArg) {
        ClientPlayNetworking.send(new RosterActionC2SPacket(
                action, target.playerUuidStr(), strArg, intArg));
    }

    private ParsedCommend parseCommend(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return new ParsedCommend(normPts(fallback), "");
        String t = raw.trim();
        int idx = 0;
        if (t.charAt(0) == '+' || t.charAt(0) == '-') {
            idx++;
            while (idx < t.length() && Character.isDigit(t.charAt(idx))) idx++;
            if (idx > 1) {
                try {
                    int pts = Integer.parseInt(t.substring(0, idx));
                    return new ParsedCommend(clampPts(pts), t.substring(idx).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return new ParsedCommend(normPts(fallback), t);
    }

    private int clampPts(int v) {
        if (v > COMMEND_MAX) return COMMEND_MAX;
        if (v < -COMMEND_MAX) return -COMMEND_MAX;
        if (v == 0) return COMMEND_STEP;
        return v;
    }

    private int normPts(int v) {
        int c = clamp(v, -COMMEND_MAX, COMMEND_MAX);
        return c == 0 ? COMMEND_STEP : c;
    }

    private int adjPts(int v) {
        if (v > COMMEND_MAX) return COMMEND_MAX;
        if (v < -COMMEND_MAX) return -COMMEND_MAX;
        if (v == 0) return -COMMEND_STEP;
        return v;
    }

    private record ParsedCommend(int points, String reason) {}

    private void drawScrollArrows(DrawContext ctx, int px, int py, int pw, int ph,
                                  int scroll, int total, int visible) {
        if (total <= visible) return;
        int ax = px + pw - 7;
        if (scroll > 0) ctx.drawText(textRenderer, "^", ax, py + 2, COL_DIM, false);
        if (scroll + visible < total) ctx.drawText(textRenderer, "v", ax, py + ph - 10, COL_DIM, false);
    }

    private void drawCentered(DrawContext ctx, String text, int panelX, int y, int panelW, int color) {
        ctx.drawText(textRenderer, text, panelX + (panelW - textRenderer.getWidth(text)) / 2, y, color, false);
    }

    private void clampListScroll(int total) {
        listScroll = clamp(listScroll, 0, Math.max(0, total - VISIBLE_ROWS));
    }

    private void clampContentScroll(int total, int visible) {
        contentScroll = clamp(contentScroll, 0, Math.max(0, total - visible));
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 2)) + "..";
    }

    private String rankAbbrev(String r) {
        return switch (r == null ? "" : r) {
            case "JUNIOR_CREWMAN"       -> "JCR";
            case "CREWMAN"              -> "CR";
            case "SENIOR_CREWMAN"       -> "SCR";
            case "PETTY_OFFICER"        -> "PO";
            case "SENIOR_PETTY_OFFICER" -> "SPO";
            case "CHIEF_PETTY_OFFICER"  -> "CPO";
            case "CADET_1"              -> "C1";
            case "CADET_2"              -> "C2";
            case "CADET_3"              -> "C3";
            case "CADET_4"              -> "C4";
            case "ENSIGN"               -> "ENS";
            case "LIEUTENANT_JG"        -> "LTJG";
            case "LIEUTENANT"           -> "LT";
            case "LIEUTENANT_COMMANDER" -> "LCDR";
            case "COMMANDER"            -> "CDR";
            case "CAPTAIN"              -> "CAPT";
            default -> "??";
        };
    }

    private int rankColor(String r) { return Integer.parseInt(rankColorHex(r), 16); }
    private String rankColorHex(String r) {
        if (r == null) return "AAAAAA";
        if (r.startsWith("CADET"))  return "88AAFF";
        if (r.equals("CAPTAIN"))    return "FFD700";
        if (r.equals("COMMANDER"))  return "FFB24A";
        if (r.startsWith("LIEUTENANT") || r.equals("ENSIGN")) return "38FF9A";
        return "F2E7D5";
    }
    private boolean isCadetRank(String r) { return r != null && r.startsWith("CADET"); }
}