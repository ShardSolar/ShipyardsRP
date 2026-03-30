package net.shard.seconddawnrp.medical.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.medical.MedicalPadConditionData;
import net.shard.seconddawnrp.medical.MedicalPadPatientData;
import net.shard.seconddawnrp.medical.MedicalPadStepData;
import net.shard.seconddawnrp.medical.network.MedicalPadActionC2SPacket;
import net.shard.seconddawnrp.medical.network.MedicalPadOpenData;

import java.util.List;

public class MedicalPadScreen extends Screen {

    private static final int W = 270;
    private static final int H = 190;

    private static final int PAD = 6;
    private static final int TAB_W = 60;
    private static final int TAB_H = 18;

    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 18;
    private static final int LIST_W = 92;
    private static final int ROW_H = 16;

    private static final int COL_BG        = 0xFF0B1714;
    private static final int COL_BG_2      = 0xFF10211C;
    private static final int COL_PANEL     = 0xFF07110E;
    private static final int COL_BORDER    = 0xFF2ECC71;
    private static final int COL_DIV       = 0xFF1C5A3B;
    private static final int COL_SEL       = 0xFF163126;
    private static final int COL_TAB       = 0xFF153325;
    private static final int COL_TEXT      = 0xFFEAEAEA;
    private static final int COL_DIM       = 0xFF7F8A84;
    private static final int COL_GOLD      = 0xFFFFD54A;
    private static final int COL_GREEN     = 0xFF41D67D;
    private static final int COL_YELLOW    = 0xFFE5C84B;
    private static final int COL_RED       = 0xFFD45555;
    private static final int COL_PURPLE    = 0xFFC079FF;
    private static final int COL_OFFLINE   = 0xFF5A5A5A;

    private MedicalPadOpenData data;

    private int tab = 0;
    private int selectedPatient = 0;
    private int selectedCondition = 0;
    private int listScroll = 0;

    private boolean showNoteInput = false;
    private String noteInput = "";
    private String pendingResolveId = null;

    private String feedbackMsg = "";
    private long feedbackExpiry = 0L;

    private int left;
    private int top;
    private int detailX;
    private int detailW;

    private String hoveredTooltip = null;

    public MedicalPadScreen(MedicalPadOpenData data) {
        super(Text.literal("Medical PADD"));
        this.data = data;
    }

    public void refresh(MedicalPadOpenData newData) {
        this.data = newData;

        if (selectedPatient >= data.patients().size()) {
            selectedPatient = Math.max(0, data.patients().size() - 1);
            selectedCondition = 0;
        }

        clampSelections();
        clampScroll();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        detailX = left + LIST_W + PAD + 2;
        detailW = W - LIST_W - PAD * 2 - 2;

        clampSelections();
        clampScroll();
        rebuildWidgets();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty — we draw our own background.
    }

    private void rebuildWidgets() {
        clearChildren();

        addDrawableChild(ButtonWidget.builder(Text.literal("PATIENTS"), b -> {
            tab = 0;
            showNoteInput = false;
            rebuildWidgets();
        }).dimensions(left + PAD, top + 3, TAB_W, TAB_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("RECORDS"), b -> {
            tab = 1;
            showNoteInput = false;
            rebuildWidgets();
        }).dimensions(left + PAD + TAB_W + 4, top + 3, TAB_W, TAB_H).build());

        if (tab == 0 && !showNoteInput) {
            MedicalPadConditionData cond = getSelectedCondition();
            if (cond != null && cond.readyToResolve()) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Resolve"), b -> {
                    pendingResolveId = cond.conditionId();
                    showNoteInput = true;
                    noteInput = "";
                    rebuildWidgets();
                }).dimensions(left + W - 74, top + H - FOOTER_H + 1, 68, 14).build());
            }
        }

        if (showNoteInput) {
            addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> submitResolve())
                    .dimensions(left + W - 66, top + H - FOOTER_H + 1, 28, 14).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                showNoteInput = false;
                pendingResolveId = null;
                noteInput = "";
                rebuildWidgets();
            }).dimensions(left + W - 34, top + H - FOOTER_H + 1, 28, 14).build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        hoveredTooltip = null;

        ctx.fill(0, 0, width, height, 0xCC000000);

        drawFrame(ctx);
        drawHeader(ctx);
        drawPanels(ctx);

        if (tab == 0) {
            drawPatientList(ctx, mx, my);
            drawPatientScrollbar(ctx);
            drawDetailPanel(ctx, mx, my);
        } else {
            drawRecordsPanel(ctx);
        }

        drawFooter(ctx);

        if (showNoteInput) {
            drawNoteOverlay(ctx);
        }

        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            ctx.drawTooltip(textRenderer, Text.literal(hoveredTooltip), mx, my);
        }

        super.render(ctx, mx, my, delta);
    }

    private void drawFrame(DrawContext ctx) {
        ctx.fill(left, top, left + W, top + H, COL_BG);
        border(ctx, left, top, W, H, COL_BORDER);
    }

    private void drawHeader(DrawContext ctx) {
        ctx.fill(left + 1, top + 1, left + W - 1, top + HEADER_H, COL_BG_2);
        lineH(ctx, left + 1, top + HEADER_H, left + W - 1, COL_BORDER);

        int activeTabX = tab == 0 ? left + PAD : left + PAD + TAB_W + 4;
        ctx.fill(activeTabX - 1, top + 2, activeTabX + TAB_W + 1, top + TAB_H + 4, COL_TAB);
    }

    private void drawPanels(DrawContext ctx) {
        int bodyTop = top + HEADER_H + 1;
        int bodyBottom = top + H - FOOTER_H;

        ctx.fill(left + 1, bodyTop, left + LIST_W, bodyBottom, COL_PANEL);
        ctx.fill(left + LIST_W + 1, bodyTop, left + W - 1, bodyBottom, COL_PANEL);

        lineV(ctx, left + LIST_W, bodyTop, bodyBottom, COL_DIV);
        lineH(ctx, left + 1, bodyBottom, left + W - 1, COL_BORDER);
    }

    private void drawPatientList(DrawContext ctx, int mx, int my) {
        int x = left + PAD;
        int y = top + HEADER_H + 6;

        ctx.drawText(textRenderer, "Patients", x, y, COL_GOLD, false);
        y += 12;

        List<MedicalPadPatientData> patients = data.patients();
        int maxRows = getMaxVisibleRows();

        for (int i = listScroll; i < patients.size() && i < listScroll + maxRows; i++) {
            MedicalPadPatientData patient = patients.get(i);
            int rowY = y + (i - listScroll) * ROW_H;

            if (i == selectedPatient) {
                ctx.fill(x - 2, rowY - 1, left + LIST_W - 3, rowY + ROW_H - 2, COL_SEL);
            }

            ctx.fill(x, rowY + 4, x + 4, rowY + 8, patient.online() ? COL_GREEN : COL_OFFLINE);

            String fullName = patient.characterName();
            String shownName = truncate(fullName, 11);
            ctx.drawText(textRenderer, shownName, x + 8, rowY, COL_TEXT, false);

            if (mx >= x + 8 && mx <= left + LIST_W - 8 && my >= rowY && my <= rowY + 8) {
                setTooltipIfTruncated(fullName, shownName);
            }

            if (!patient.conditions().isEmpty()) {
                boolean critical = patient.conditions().stream()
                        .anyMatch(c -> "CRITICAL".equalsIgnoreCase(c.severityLabel()));

                ctx.drawText(
                        textRenderer,
                        "[" + patient.conditions().size() + "]",
                        x + 8,
                        rowY + 8,
                        critical ? COL_RED : COL_YELLOW,
                        false
                );
            }
        }
    }

    private void drawPatientScrollbar(DrawContext ctx) {
        int bodyTop = top + HEADER_H + 1;
        int listTop = bodyTop + 14;
        int listBottom = top + H - FOOTER_H - 6;

        int trackX = left + LIST_W - 5;
        int trackY = listTop;
        int trackH = listBottom - listTop;

        ctx.fill(trackX, trackY, trackX + 2, trackY + trackH, COL_DIV);

        int total = data.patients().size();
        int visible = getMaxVisibleRows();

        if (total <= visible) {
            return;
        }

        int thumbH = Math.max(8, trackH * visible / total);
        int maxScroll = Math.max(1, total - visible);
        int thumbY = trackY + (trackH - thumbH) * listScroll / maxScroll;

        ctx.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, COL_GREEN);
    }

    private void drawDetailPanel(DrawContext ctx, int mx, int my) {
        int x = detailX + 2;
        int y = top + HEADER_H + 6;
        int contentBottom = top + H - FOOTER_H - 6;

        List<MedicalPadPatientData> patients = data.patients();
        if (patients.isEmpty()) {
            ctx.drawText(textRenderer, "No patients registered.", x, y, COL_DIM, false);
            return;
        }
        if (selectedPatient >= patients.size()) {
            return;
        }

        MedicalPadPatientData patient = patients.get(selectedPatient);

        String fullHeaderName = patient.characterName();
        String shownHeaderName = truncate(fullHeaderName, 24);
        ctx.drawText(textRenderer, shownHeaderName, x, y, COL_GOLD, false);
        if (mx >= x && mx <= x + detailW - 10 && my >= y && my <= y + 8) {
            setTooltipIfTruncated(fullHeaderName, shownHeaderName);
        }

        y += 10;
        ctx.drawText(textRenderer, patient.rankDisplay(), x, y, COL_DIM, false);
        y += 14;

        if (patient.conditions().isEmpty()) {
            ctx.drawText(textRenderer, "No active conditions.", x, y, COL_GREEN, false);
            return;
        }

        y = drawConditionTabs(ctx, patient, x, y, mx, my);
        y += 6;

        MedicalPadConditionData cond = getSelectedCondition();
        if (cond == null) {
            return;
        }

        ctx.fill(x - 2, y - 2, x + detailW - 10, y + 76, 0x22000000);
        border(ctx, x - 2, y - 2, detailW - 8, 76, COL_DIV);

        String fullConditionName = cond.displayName();
        String shownConditionName = truncate(fullConditionName, 24);
        ctx.drawText(
                textRenderer,
                shownConditionName,
                x + 2,
                y + 2,
                severityColor(cond.severityLabel()),
                false
        );
        if (mx >= x + 2 && mx <= x + detailW - 12 && my >= y + 2 && my <= y + 10) {
            setTooltipIfTruncated(fullConditionName, shownConditionName);
        }

        String severity = cond.severityLabel();
        int sevColor = severityColor(severity);
        int sevTextW = textRenderer.getWidth(severity);
        int badgeX = x + 2;
        int badgeY = y + 12;
        int badgeW = sevTextW + 8;

        ctx.fill(badgeX - 2, badgeY - 1, badgeX - 2 + badgeW, badgeY + 9, 0x33000000);
        border(ctx, badgeX - 2, badgeY - 1, badgeW, 10, sevColor);
        ctx.drawText(textRenderer, severity, badgeX + 2, badgeY + 1, sevColor, false);

        int sy = y + 24;

        if (cond.requiresSurgery()) {
            ctx.drawText(textRenderer, "Surgeon required", x + 2, sy, COL_PURPLE, false);
            sy += 10;
        }

        ctx.drawText(textRenderer, "Treatment steps", x + 2, sy, COL_DIM, false);
        sy += 10;

        for (MedicalPadStepData step : cond.steps()) {
            if (sy > contentBottom) {
                break;
            }

            String fullLine = formatStep(step);
            String shownLine = truncate(fullLine, 32);
            ctx.drawText(textRenderer, shownLine, x + 4, sy, COL_TEXT, false);

            if (mx >= x + 4 && mx <= x + detailW - 12 && my >= sy && my <= sy + 8) {
                setTooltipIfTruncated(fullLine, shownLine);
            }

            sy += 10;
        }

        if (cond.readyToResolve() && sy <= contentBottom) {
            sy += 2;
            ctx.drawText(textRenderer, "Ready to resolve", x + 2, sy, COL_GREEN, false);
        }
    }

    private int drawConditionTabs(DrawContext ctx, MedicalPadPatientData patient, int x, int y, int mx, int my) {
        int cx = x;

        for (int i = 0; i < patient.conditions().size(); i++) {
            MedicalPadConditionData cond = patient.conditions().get(i);
            String fullLabel = cond.displayName();
            String shownLabel = truncate(fullLabel, 9);
            int tw = textRenderer.getWidth(shownLabel) + 8;

            if (cx + tw > left + W - 8) {
                break;
            }

            if (i == selectedCondition) {
                ctx.fill(cx - 1, y - 1, cx + tw, y + 10, COL_SEL);
                border(ctx, cx - 1, y - 1, tw + 1, 11, COL_DIV);
            }

            ctx.drawText(
                    textRenderer,
                    shownLabel,
                    cx + 3,
                    y + 1,
                    severityColor(cond.severityLabel()),
                    false
            );

            if (mx >= cx - 1 && mx < cx + tw && my >= y - 1 && my < y + 10) {
                setTooltipIfTruncated(fullLabel, shownLabel);
            }

            cx += tw + 4;
        }

        return y + 12;
    }

    private void drawRecordsPanel(DrawContext ctx) {
        int x = detailX;
        int y = top + HEADER_H + 8;

        ctx.drawText(textRenderer, "Records", x, y, COL_GOLD, false);
        y += 14;
        ctx.drawText(textRenderer, "Use /gm medical list <player>", x, y, COL_DIM, false);
        y += 10;
        ctx.drawText(textRenderer, "Full records UI later.", x, y, COL_DIM, false);
    }

    private void drawFooter(DrawContext ctx) {
        int y = top + H - FOOTER_H + 1;
        ctx.fill(left + 1, y, left + W - 1, top + H - 1, COL_BG_2);

        if (!feedbackMsg.isEmpty() && System.currentTimeMillis() <= feedbackExpiry) {
            ctx.drawText(textRenderer, feedbackMsg, left + PAD, y + 4, COL_GREEN, false);
        }
    }

    private void drawNoteOverlay(DrawContext ctx) {
        int ox = detailX + 2;
        int oy = top + H - FOOTER_H - 22;
        int ow = detailW - 8;

        ctx.fill(ox, oy, ox + ow, oy + 18, COL_PANEL);
        border(ctx, ox, oy, ow, 18, COL_BORDER);

        String fullNoteLine = "Note: " + noteInput;
        String shownNoteLine = "Note: " + truncate(noteInput, 24) + "|";
        ctx.drawText(textRenderer, shownNoteLine, ox + 4, oy + 5, COL_TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }

        if (tab == 0) {
            int x = left + PAD;
            int y = top + HEADER_H + 18;
            int maxRows = getMaxVisibleRows();

            for (int i = listScroll; i < data.patients().size() && i < listScroll + maxRows; i++) {
                int rowY = y + (i - listScroll) * ROW_H;
                if (mx >= x - 2 && mx < left + LIST_W - 3 && my >= rowY - 1 && my < rowY + ROW_H - 2) {
                    selectedPatient = i;
                    selectedCondition = 0;
                    showNoteInput = false;
                    rebuildWidgets();
                    return true;
                }
            }

            if (!data.patients().isEmpty() && selectedPatient < data.patients().size()) {
                MedicalPadPatientData patient = data.patients().get(selectedPatient);
                int cx = detailX + 2;
                int cy = top + HEADER_H + 30;

                for (int i = 0; i < patient.conditions().size(); i++) {
                    MedicalPadConditionData cond = patient.conditions().get(i);
                    String label = truncate(cond.displayName(), 9);
                    int tw = textRenderer.getWidth(label) + 8;

                    if (cx + tw > left + W - 8) {
                        break;
                    }

                    if (mx >= cx - 1 && mx < cx + tw && my >= cy - 1 && my < cy + 10) {
                        selectedCondition = i;
                        showNoteInput = false;
                        rebuildWidgets();
                        return true;
                    }

                    cx += tw + 4;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= left && mx < left + LIST_W && my >= top + HEADER_H && my < top + H - FOOTER_H) {
            int maxScroll = Math.max(0, data.patients().size() - getMaxVisibleRows());
            listScroll = Math.max(0, Math.min(maxScroll, listScroll - (int) Math.signum(v)));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (showNoteInput) {
            if (key == 259 && !noteInput.isEmpty()) {
                noteInput = noteInput.substring(0, noteInput.length() - 1);
            } else if (key == 257 || key == 335) {
                submitResolve();
            } else if (key == 256) {
                showNoteInput = false;
                pendingResolveId = null;
                noteInput = "";
                rebuildWidgets();
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (showNoteInput && noteInput.length() < 100 && chr >= 32 && chr != 127) {
            noteInput += chr;
            return true;
        }
        return super.charTyped(chr, mods);
    }

    private void submitResolve() {
        if (pendingResolveId == null) {
            return;
        }

        ClientPlayNetworking.send(new MedicalPadActionC2SPacket("resolve", pendingResolveId, noteInput));

        showNoteInput = false;
        pendingResolveId = null;
        noteInput = "";
        feedbackMsg = "Resolution submitted.";
        feedbackExpiry = System.currentTimeMillis() + 4000L;
        rebuildWidgets();
    }

    private MedicalPadConditionData getSelectedCondition() {
        if (data.patients().isEmpty()) {
            return null;
        }
        if (selectedPatient >= data.patients().size()) {
            return null;
        }

        List<MedicalPadConditionData> conditions = data.patients().get(selectedPatient).conditions();
        if (conditions.isEmpty()) {
            return null;
        }

        if (selectedCondition >= conditions.size()) {
            selectedCondition = 0;
        }

        return conditions.get(selectedCondition);
    }

    private void clampSelections() {
        if (selectedPatient < 0) {
            selectedPatient = 0;
        }
        if (selectedCondition < 0) {
            selectedCondition = 0;
        }

        if (!data.patients().isEmpty() && selectedPatient >= data.patients().size()) {
            selectedPatient = data.patients().size() - 1;
        }
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, data.patients().size() - getMaxVisibleRows());
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));
    }

    private int getMaxVisibleRows() {
        return Math.max(1, (H - HEADER_H - FOOTER_H - 26) / ROW_H);
    }

    private int severityColor(String severity) {
        if (severity == null) {
            return COL_TEXT;
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> COL_RED;
            case "SERIOUS" -> COL_YELLOW;
            case "MODERATE" -> COL_GOLD;
            case "MINOR" -> COL_GREEN;
            default -> COL_TEXT;
        };
    }

    private String formatStep(MedicalPadStepData step) {
        String status = step.completed() ? "OK" : switch (step.windowState()) {
            case WAITING -> "...";
            case EXPIRED -> "X";
            case OPEN, NONE -> "-";
        };

        String text = status + " " + step.label();

        if (step.requiresSurgery()) {
            text += " [S]";
        }

        if (!step.completed()) {
            String item = simplifyItem(step.item());
            if (!item.isEmpty()) {
                text += " " + item + "x" + step.quantity();
            }
        }

        return text;
    }

    private String simplifyItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        String simple = itemId.replace("minecraft:", "");
        return simple.length() > 10 ? simple.substring(0, 10) : simple;
    }

    private String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 1) + "…";
    }

    private void setTooltipIfTruncated(String fullText, String shownText) {
        if (fullText != null && shownText != null && !fullText.equals(shownText)) {
            hoveredTooltip = fullText;
        }
    }

    private void border(DrawContext ctx, int x, int y, int w, int h, int col) {
        ctx.fill(x, y, x + w, y + 1, col);
        ctx.fill(x, y + h - 1, x + w, y + h, col);
        ctx.fill(x, y, x + 1, y + h, col);
        ctx.fill(x + w - 1, y, x + w, y + h, col);
    }

    private void lineH(DrawContext ctx, int x0, int y, int x1, int col) {
        ctx.fill(x0, y, x1, y + 1, col);
    }

    private void lineV(DrawContext ctx, int x, int y0, int y1, int col) {
        ctx.fill(x, y0, x + 1, y1, col);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}