package net.shard.seconddawnrp.roster.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.roster.data.RosterActionC2SPacket;
import net.shard.seconddawnrp.roster.data.RosterEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Roster GUI — two-panel layout.
 *
 * Left panel:  scrollable member list
 * Right panel: selected member detail + action buttons
 *
 * Read-only for all viewers. Action buttons render only for viewers
 * with sufficient authority (checked via handler permission helpers).
 *
 * All actions send RosterActionC2SPacket. The server responds with
 * RosterRefreshS2CPacket which calls handler.applyRefresh() — the screen
 * re-renders automatically on next frame.
 */
public class RosterScreen extends HandledScreen<RosterScreenHandler> {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int GUI_W  = 400;
    private static final int GUI_H  = 220;

    // Left — member list
    private static final int LIST_X = 10;
    private static final int LIST_Y = 30;
    private static final int LIST_W = 140;
    private static final int LIST_H = 170;
    private static final int ROW_H  = 28;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    // Right — detail panel
    private static final int DETAIL_X = 160;
    private static final int DETAIL_Y = 30;
    private static final int DETAIL_W = 230;
    private static final int DETAIL_H = 100;

    // Action button area — below detail panel
    private static final int BTN_X       = 160;
    private static final int BTN_START_Y = 138;
    private static final int BTN_W       = 110;
    private static final int BTN_H       = 13;
    private static final int BTN_GAP     = 15;
    private static final int BTN_COL2_X  = 278;

    // Feedback bar
    private static final int FEEDBACK_Y = 208;

    private static final int COMMEND_MAX_ABS_POINTS = 100;
    private static final int COMMEND_STEP = 5;

    // ── State ─────────────────────────────────────────────────────────────────

    private int listScroll = 0;
    private int detailScroll = 0;

    // Inline input state for graduate rank and commend
    private boolean awaitingGraduateInput = false;
    private boolean awaitingCommendInput  = false;
    private String inputBuffer = "";
    private int commendPoints = 25; // fallback/manual adjustment value

    // Colours
    private static final int COL_HEADER   = 0xFF8FD7E8;
    private static final int COL_TEXT     = 0xFFF2E7D5;
    private static final int COL_DIM      = 0xFFAAAAAA;
    private static final int COL_GOLD     = 0xFFFFB24A;
    private static final int COL_GREEN    = 0xFF38FF9A;
    private static final int COL_RED      = 0xFFFF6060;
    private static final int COL_ONLINE   = 0xFF44FF88;
    private static final int COL_OFFLINE  = 0xFF666666;

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

    // ── Render entry points ───────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int ox = this.x, oy = this.y;

        // Background
        context.fill(ox, oy, ox + GUI_W, oy + GUI_H, 0xE0101820);

        // Title bar
        context.fill(ox, oy, ox + GUI_W, oy + 22, 0xFF1A2840);
        String title = "ROSTER — " + handler.getDivisionName();
        context.drawText(this.textRenderer, title,
                ox + (GUI_W - textRenderer.getWidth(title)) / 2,
                oy + 7, COL_HEADER, false);

        // Panel outlines
        drawOutline(context, ox + LIST_X, oy + LIST_Y, LIST_W, LIST_H, 0xFF2A3C55);
        drawOutline(context, ox + DETAIL_X, oy + DETAIL_Y, DETAIL_W, DETAIL_H, 0xFF2A3C55);

        // Panel headers
        drawCentered(context, "CREW", ox + LIST_X, oy + LIST_Y - 12, LIST_W, COL_HEADER);
        drawCentered(context, "MEMBER DETAILS", ox + DETAIL_X, oy + DETAIL_Y - 12, DETAIL_W, COL_HEADER);

        drawMemberList(context, ox, oy, mouseX, mouseY);
        drawDetailPanel(context, ox, oy);
        drawActionButtons(context, ox, oy, mouseX, mouseY);
        drawInputOverlay(context, ox, oy);
        drawFeedback(context, ox, oy);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {}

    // ── Member list ───────────────────────────────────────────────────────────

    private void drawMemberList(DrawContext context, int ox, int oy, int mouseX, int mouseY) {
        List<RosterEntry> members = handler.getMembers();
        if (members.isEmpty()) {
            context.drawText(this.textRenderer, "No members loaded.",
                    ox + LIST_X + 4, oy + LIST_Y + 4, COL_DIM, false);
            return;
        }

        clampListScroll(members.size());

        context.enableScissor(ox + LIST_X + 1, oy + LIST_Y + 1,
                ox + LIST_X + LIST_W - 1, oy + LIST_Y + LIST_H - 1);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i + listScroll;
            if (di >= members.size()) break;

            RosterEntry e = members.get(di);
            int rowX = ox + LIST_X + 2;
            int rowY = oy + LIST_Y + (i * ROW_H) + 1;

            boolean selected = di == handler.getSelectedIndex();
            context.fill(rowX, rowY, rowX + LIST_W - 4, rowY + ROW_H - 2,
                    selected ? 0x2A4488FF : 0x0A000000);

            // Online indicator dot
            int dotColor = e.isOnline() ? COL_ONLINE : COL_OFFLINE;
            context.fill(rowX + 2, rowY + 5, rowX + 5, rowY + 8, dotColor);

            // Character name (top line)
            String characterName = trim(e.characterName(), 14);
            context.drawText(this.textRenderer, characterName,
                    rowX + 8, rowY + 2, e.isOnline() ? COL_TEXT : COL_DIM, false);

            // Username (second line)
            String username = "@" + trim(e.minecraftName(), 12);
            context.drawText(this.textRenderer, username,
                    rowX + 8, rowY + 11, 0xFF7FA0B8, false);

            // Rank badge
            String rankShort = rankAbbrev(e.rankId());
            int rankColor = rankColor(e.rankId());
            context.drawText(this.textRenderer, rankShort,
                    rowX + 8, rowY + 20, rankColor, false);

            // Division badge (small, right-aligned)
            String divShort = e.divisionName().substring(0, Math.min(3, e.divisionName().length()));
            context.drawText(this.textRenderer, divShort,
                    rowX + LIST_W - 26, rowY + 11, COL_DIM, false);
        }

        context.disableScissor();
        drawScrollIndicators(context, ox + LIST_X, oy + LIST_Y, LIST_W, LIST_H,
                listScroll, members.size(), VISIBLE_ROWS);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void drawDetailPanel(DrawContext context, int ox, int oy) {
        RosterEntry e = handler.getSelected();
        if (e == null) {
            context.drawText(this.textRenderer, "Select a crew member.",
                    ox + DETAIL_X + 4, oy + DETAIL_Y + 4, COL_DIM, false);
            return;
        }

        List<String[]> lines = buildDetailLines(e);
        int visibleLines = DETAIL_H / 11;
        clampDetailScroll(lines.size(), visibleLines);

        context.enableScissor(ox + DETAIL_X + 1, oy + DETAIL_Y + 1,
                ox + DETAIL_X + DETAIL_W - 1, oy + DETAIL_Y + DETAIL_H - 1);

        int textY = oy + DETAIL_Y + 3;
        for (int i = 0; i < visibleLines; i++) {
            int di = i + detailScroll;
            if (di >= lines.size()) break;
            String[] line = lines.get(di);

            int labelW = textRenderer.getWidth(line[0]);
            context.drawText(this.textRenderer, line[0],
                    ox + DETAIL_X + 4, textY, COL_DIM, false);
            context.drawText(this.textRenderer, line[1],
                    ox + DETAIL_X + 4 + labelW + 2, textY,
                    Integer.parseInt(line[2], 16), false);
            textY += 11;
        }

        context.disableScissor();
        drawScrollIndicators(context, ox + DETAIL_X, oy + DETAIL_Y, DETAIL_W, DETAIL_H,
                detailScroll, lines.size(), visibleLines);
    }

    private List<String[]> buildDetailLines(RosterEntry e) {
        List<String[]> lines = new ArrayList<>();
        String onlineMark = e.isOnline() ? " ●" : " ○";

        lines.add(row("Name:", e.characterName() + onlineMark,
                e.isOnline() ? "38FF9A" : "AAAAAA"));
        lines.add(row("User:", e.minecraftName(), "888888"));
        lines.add(row("Rank:", e.rankDisplayName()
                + (e.mustang() ? " ⋆" : ""), rankColorHex(e.rankId())));
        lines.add(row("Division:", e.divisionName(), "8FD7E8"));
        lines.add(row("Path:", e.progressionPath(), "FFB24A"));
        if (!e.shipPosition().equals("NONE")) {
            lines.add(row("Position:", e.shipPosition().replace("_", " "), "FFD700"));
        }
        lines.add(row("Rank Pts:", String.valueOf(e.rankPoints()), "F2E7D5"));
        lines.add(row("Service Rec:", String.valueOf(e.serviceRecord()), "F2E7D5"));
        if (!e.certifications().isEmpty()) {
            lines.add(row("Certs:", String.join(", ", e.certifications()), "8FD7E8"));
        }
        if (e.mustang()) {
            lines.add(row("", "★ Mustang", "FFB24A"));
        }
        return lines;
    }

    private String[] row(String label, String value, String colorHex) {
        return new String[]{label, value, colorHex};
    }

    // ── Action buttons ────────────────────────────────────────────────────────

    private void drawActionButtons(DrawContext context, int ox, int oy,
                                   int mouseX, int mouseY) {
        RosterEntry selected = handler.getSelected();
        if (selected == null) return;

        boolean isCadet = isCadetRank(selected.rankId());
        int y = oy + BTN_START_Y;

        if (handler.canManageRanks()) {
            if (!isCadet) {
                drawButton(context, ox + BTN_X, y, BTN_W, BTN_H,
                        "▲ PROMOTE", mouseX, mouseY, 0x1A22FF44);
                drawButton(context, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H,
                        "▼ DEMOTE", mouseX, mouseY, 0x1AFF4422);
            }
        }

        if (handler.isAdmin() || handler.canManageRanks()) {
            if (!isCadet) {
                int cadetY = y + (handler.canManageRanks() && !isCadet ? BTN_GAP * 2 : 0);
                drawButton(context, ox + BTN_X, cadetY + BTN_GAP, BTN_W, BTN_H,
                        "ENROL CADET", mouseX, mouseY, 0x1A4488FF);
            } else {
                drawButton(context, ox + BTN_X, y, BTN_W, BTN_H,
                        "CADET PROMOTE", mouseX, mouseY, 0x1A4488FF);
                drawButton(context, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H,
                        "PROPOSE GRAD", mouseX, mouseY, 0x1A44FFCC);
                drawButton(context, ox + BTN_X, y + BTN_GAP * 2, BTN_W, BTN_H,
                        "APPROVE GRAD", mouseX, mouseY, 0x1A88FF44);
            }
        }

        if (handler.canManageMembers()) {
            drawButton(context, ox + BTN_COL2_X, y, BTN_W, BTN_H,
                    "TRANSFER DIV", mouseX, mouseY, 0x1AFFAA22);
            drawButton(context, ox + BTN_COL2_X, y + BTN_GAP, BTN_W, BTN_H,
                    "DISMISS", mouseX, mouseY, 0x1AFF2222);
        }

        if (handler.canCommend()) {
            drawButton(context, ox + BTN_COL2_X, y + BTN_GAP * 2, BTN_W, BTN_H,
                    "COMMEND / DEMERIT", mouseX, mouseY, 0x1AFFDD00);
        }
    }

    private void drawButton(DrawContext context, int bx, int by, int bw, int bh,
                            String label, int mouseX, int mouseY, int bgColor) {
        boolean hovered = inside(mouseX, mouseY, bx, by, bw, bh);
        context.fill(bx, by, bx + bw, by + bh,
                hovered ? (bgColor | 0x3F000000) : bgColor);
        context.drawBorder(bx, by, bw, bh, 0xFF2A3C55);
        int lw = textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label,
                bx + (bw - lw) / 2, by + (bh - 7) / 2 + 1, COL_TEXT, false);
    }

    // ── Input overlay ─────────────────────────────────────────────────────────

    private void drawInputOverlay(DrawContext context, int ox, int oy) {
        if (!awaitingGraduateInput && !awaitingCommendInput) return;

        context.fill(ox + BTN_X - 4, oy + BTN_START_Y - 4,
                ox + BTN_COL2_X + BTN_W + 4, oy + BTN_START_Y + BTN_GAP * 4,
                0xCC101820);
        context.drawBorder(ox + BTN_X - 4, oy + BTN_START_Y - 4,
                BTN_COL2_X - BTN_X + BTN_W + 8, BTN_GAP * 4 + 8, 0xFF8FD7E8);

        if (awaitingGraduateInput) {
            context.drawText(this.textRenderer, "Starting rank ID (e.g. ensign):",
                    ox + BTN_X, oy + BTN_START_Y, COL_HEADER, false);
            context.drawText(this.textRenderer, "> " + inputBuffer + "_",
                    ox + BTN_X, oy + BTN_START_Y + 12, COL_GREEN, false);
            context.drawText(this.textRenderer, "Enter to confirm  Esc to cancel",
                    ox + BTN_X, oy + BTN_START_Y + 24, COL_DIM, false);
        } else {
            ParsedCommendInput parsed = parseCommendInput(inputBuffer, commendPoints);

            String mode = parsed.points() >= 0 ? "COMMEND" : "DEMERIT";
            int modeColor = parsed.points() >= 0 ? COL_GOLD : COL_RED;

            context.drawText(this.textRenderer, mode + " reason (pts: " + parsed.points() + "):",
                    ox + BTN_X, oy + BTN_START_Y, modeColor, false);

            context.drawText(this.textRenderer, "> " + inputBuffer + "_",
                    ox + BTN_X, oy + BTN_START_Y + 12, modeColor, false);

            String preview = "Parsed: " + parsed.points() + " pts"
                    + (parsed.reason().isBlank() ? "" : " | Reason: " + parsed.reason());
            context.drawText(this.textRenderer, preview,
                    ox + BTN_X, oy + BTN_START_Y + 24, COL_DIM, false);

            context.drawText(this.textRenderer,
                    "Type +15 or -15 at the start. [ - / + ] still adjusts fallback points.",
                    ox + BTN_X, oy + BTN_START_Y + 36, COL_DIM, false);
        }
    }

    // ── Feedback bar ──────────────────────────────────────────────────────────

    private void drawFeedback(DrawContext context, int ox, int oy) {
        String msg = handler.getFeedbackMessage();
        if (msg.isBlank()) return;
        int fw = textRenderer.getWidth(msg);
        context.fill(ox + (GUI_W - fw - 8) / 2, oy + FEEDBACK_Y - 2,
                ox + (GUI_W + fw + 8) / 2, oy + FEEDBACK_Y + 10, 0xCC1A2840);
        context.drawText(this.textRenderer, msg,
                ox + (GUI_W - fw) / 2, oy + FEEDBACK_Y, COL_GREEN, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ox = this.x, oy = this.y;

        if (awaitingGraduateInput || awaitingCommendInput) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        List<RosterEntry> members = handler.getMembers();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i + listScroll;
            if (di >= members.size()) break;
            int rowX = ox + LIST_X + 2;
            int rowY = oy + LIST_Y + (i * ROW_H) + 1;
            if (inside(mouseX, mouseY, rowX, rowY, LIST_W - 4, ROW_H - 2)) {
                handler.setSelectedIndex(di);
                detailScroll = 0;
                return true;
            }
        }

        RosterEntry selected = handler.getSelected();
        if (selected != null) {
            handleButtonClicks(mouseX, mouseY, ox, oy, selected);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleButtonClicks(double mouseX, double mouseY,
                                    int ox, int oy, RosterEntry selected) {
        boolean isCadet = isCadetRank(selected.rankId());
        int y = oy + BTN_START_Y;

        if (handler.canManageRanks() && !isCadet) {
            if (inside(mouseX, mouseY, ox + BTN_X, y, BTN_W, BTN_H)) {
                sendAction("PROMOTE", selected, "", 0); return;
            }
            if (inside(mouseX, mouseY, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H)) {
                sendAction("DEMOTE", selected, "", 0); return;
            }
        }

        if (handler.isAdmin() || handler.canManageRanks()) {
            if (!isCadet) {
                int cadetY = y + (handler.canManageRanks() ? BTN_GAP * 3 : 0);
                if (inside(mouseX, mouseY, ox + BTN_X, cadetY, BTN_W, BTN_H)) {
                    sendAction("CADET_ENROL", selected, "", 0); return;
                }
            } else {
                if (inside(mouseX, mouseY, ox + BTN_X, y, BTN_W, BTN_H)) {
                    sendAction("CADET_PROMOTE", selected, "", 0); return;
                }
                if (inside(mouseX, mouseY, ox + BTN_X, y + BTN_GAP, BTN_W, BTN_H)) {
                    awaitingGraduateInput = true;
                    inputBuffer = "ensign";
                    return;
                }
                if (inside(mouseX, mouseY, ox + BTN_X, y + BTN_GAP * 2, BTN_W, BTN_H)) {
                    sendAction("CADET_APPROVE", selected, "", 0); return;
                }
            }
        }

        if (handler.canManageMembers()) {
            if (inside(mouseX, mouseY, ox + BTN_COL2_X, y, BTN_W, BTN_H)) {
                sendAction("TRANSFER", selected, "UNASSIGNED", 0); return;
            }
            if (inside(mouseX, mouseY, ox + BTN_COL2_X, y + BTN_GAP, BTN_W, BTN_H)) {
                sendAction("DISMISS", selected, "", 0); return;
            }
        }

        if (handler.canCommend()) {
            if (inside(mouseX, mouseY, ox + BTN_COL2_X, y + BTN_GAP * 2, BTN_W, BTN_H)) {
                awaitingCommendInput = true;
                inputBuffer = "";
                commendPoints = 25;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int ox = this.x, oy = this.y;
        int delta = verticalAmount > 0 ? -1 : 1;

        if (inside(mouseX, mouseY, ox + LIST_X, oy + LIST_Y, LIST_W, LIST_H)) {
            listScroll = clamp(listScroll + delta, 0,
                    Math.max(0, handler.getMembers().size() - VISIBLE_ROWS));
            return true;
        }
        if (inside(mouseX, mouseY, ox + DETAIL_X, oy + DETAIL_Y, DETAIL_W, DETAIL_H)) {
            RosterEntry e = handler.getSelected();
            if (e != null) {
                int total = buildDetailLines(e).size();
                int visible = DETAIL_H / 11;
                detailScroll = clamp(detailScroll + delta, 0, Math.max(0, total - visible));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ── Keyboard (for input overlay) ──────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingGraduateInput || awaitingCommendInput) {
            if (keyCode == 256) { // Escape
                awaitingGraduateInput = false;
                awaitingCommendInput  = false;
                inputBuffer = "";
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                RosterEntry selected = handler.getSelected();
                if (selected != null) {
                    if (awaitingGraduateInput) {
                        sendAction("CADET_GRADUATE", selected, inputBuffer.trim(), 0);
                    } else {
                        ParsedCommendInput parsed = parseCommendInput(inputBuffer, commendPoints);
                        sendAction("COMMEND", selected, parsed.reason(), parsed.points());
                    }
                }
                awaitingGraduateInput = false;
                awaitingCommendInput  = false;
                inputBuffer = "";
                return true;
            }
            if (keyCode == 259 && !inputBuffer.isEmpty()) { // Backspace
                inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
                return true;
            }

            if (awaitingCommendInput) {
                if (keyCode == 93 || keyCode == 334) { // + / numpad +
                    commendPoints = adjustCommendPoints(commendPoints + COMMEND_STEP);
                    return true;
                }
                if (keyCode == 45 || keyCode == 333) { // - / numpad -
                    commendPoints = adjustCommendPoints(commendPoints - COMMEND_STEP);
                    return true;
                }
            }

            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (awaitingGraduateInput || awaitingCommendInput) {
            if (chr >= 32 && inputBuffer.length() < 96) {
                inputBuffer += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    // ── Packet send ───────────────────────────────────────────────────────────

    private void sendAction(String action, RosterEntry target, String stringArg, int intArg) {
        ClientPlayNetworking.send(new RosterActionC2SPacket(
                action, target.playerUuidStr(), stringArg, intArg));
    }

    // ── Commend / demerit parsing ─────────────────────────────────────────────

    private ParsedCommendInput parseCommendInput(String rawInput, int fallbackPoints) {
        String trimmed = rawInput == null ? "" : rawInput.trim();
        if (trimmed.isBlank()) {
            int normalized = normalizeNonZeroPoints(fallbackPoints);
            return new ParsedCommendInput(normalized, "");
        }

        int spaceIndex = trimmed.indexOf(' ');
        String firstToken = spaceIndex >= 0 ? trimmed.substring(0, spaceIndex) : trimmed;

        if (firstToken.matches("[+-]\\d+")) {
            try {
                int parsed = Integer.parseInt(firstToken);
                int clamped = clampParsedPoints(parsed);
                String reason = spaceIndex >= 0 ? trimmed.substring(spaceIndex + 1).trim() : "";
                return new ParsedCommendInput(clamped, reason);
            } catch (NumberFormatException ignored) {
                // Fall through to fallback behavior
            }
        }

        int normalized = normalizeNonZeroPoints(fallbackPoints);
        return new ParsedCommendInput(normalized, trimmed);
    }

    private int clampParsedPoints(int points) {
        if (points > COMMEND_MAX_ABS_POINTS) return COMMEND_MAX_ABS_POINTS;
        if (points < -COMMEND_MAX_ABS_POINTS) return -COMMEND_MAX_ABS_POINTS;
        if (points == 0) return COMMEND_STEP;
        return points;
    }

    private int normalizeNonZeroPoints(int points) {
        int clamped = Math.max(-COMMEND_MAX_ABS_POINTS, Math.min(COMMEND_MAX_ABS_POINTS, points));
        if (clamped == 0) return COMMEND_STEP;
        return clamped;
    }

    private int adjustCommendPoints(int candidate) {
        if (candidate > COMMEND_MAX_ABS_POINTS) return COMMEND_MAX_ABS_POINTS;
        if (candidate < -COMMEND_MAX_ABS_POINTS) return -COMMEND_MAX_ABS_POINTS;
        if (candidate == 0) return -COMMEND_STEP;
        return candidate;
    }

    private record ParsedCommendInput(int points, String reason) {}

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawBorder(x, y, w, h, color);
    }

    private void drawCentered(DrawContext ctx, String text, int panelX, int y, int panelW, int color) {
        int tw = textRenderer.getWidth(text);
        ctx.drawText(this.textRenderer, text, panelX + (panelW - tw) / 2, y, color, false);
    }

    private void drawScrollIndicators(DrawContext ctx, int px, int py, int pw, int ph,
                                      int scroll, int total, int visible) {
        if (total <= visible) return;
        int ax = px + pw - 7;
        if (scroll > 0)
            ctx.drawText(this.textRenderer, "^", ax, py + 2, COL_DIM, false);
        if (scroll + visible < total)
            ctx.drawText(this.textRenderer, "v", ax, py + ph - 10, COL_DIM, false);
    }

    // ── Rank display helpers ──────────────────────────────────────────────────

    private String rankAbbrev(String rankId) {
        return switch (rankId) {
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
            default                     -> "??";
        };
    }

    private int rankColor(String rankId) {
        return Integer.parseInt(rankColorHex(rankId), 16);
    }

    private String rankColorHex(String rankId) {
        if (rankId == null) return "AAAAAA";
        if (rankId.startsWith("CADET"))    return "88AAFF";
        if (rankId.equals("CAPTAIN"))      return "FFD700";
        if (rankId.equals("COMMANDER"))    return "FFB24A";
        if (rankId.startsWith("LIEUTENANT") || rankId.equals("ENSIGN")) return "38FF9A";
        return "F2E7D5";
    }

    private boolean isCadetRank(String rankId) {
        return rankId != null && rankId.startsWith("CADET");
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void clampListScroll(int total) {
        listScroll = clamp(listScroll, 0, Math.max(0, total - VISIBLE_ROWS));
    }

    private void clampDetailScroll(int total, int visible) {
        detailScroll = clamp(detailScroll, 0, Math.max(0, total - visible));
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 2)) + "..";
    }
}