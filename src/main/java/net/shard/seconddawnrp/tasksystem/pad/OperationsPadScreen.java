package net.shard.seconddawnrp.tasksystem.pad;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.network.PushSubmissionsS2CPacket;
import net.shard.seconddawnrp.dice.network.ReviewSubmissionC2SPacket;
import net.shard.seconddawnrp.dice.network.SelectSubmissionC2SPacket;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.network.AssignTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.network.CreateTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.network.ReviewTaskActionC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class OperationsPadScreen extends HandledScreen<AdminTaskScreenHandler> {

    private static final Identifier TEXTURE = SecondDawnRP.id("textures/gui/operations_pad.png");
    private static final int TEX_WIDTH  = 512;
    private static final int TEX_HEIGHT = 256;
    private static final int GUI_WIDTH  = 420;
    private static final int GUI_HEIGHT = 210;

    // ── Existing panels ───────────────────────────────────────────────────────
    private static final int LIST_X      = 22;
    private static final int LIST_Y      = 80;
    private static final int LIST_WIDTH  = 168;
    private static final int LIST_HEIGHT = 112;
    private static final int ROW_HEIGHT  = 24;

    private static final int DETAIL_X      = 260;
    private static final int DETAIL_Y      = 80;
    private static final int DETAIL_WIDTH  = 172;
    private static final int DETAIL_HEIGHT = 112;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final int TASKS_TAB_X  = 20;  private static final int TASKS_TAB_Y  = 34; private static final int TASKS_TAB_W  = 60;  private static final int TASKS_TAB_H  = 20;
    private static final int DETAIL_TAB_X = 88;  private static final int DETAIL_TAB_Y = 34; private static final int DETAIL_TAB_W = 70;  private static final int DETAIL_TAB_H = 20;
    private static final int CREATE_TAB_X = 165; private static final int CREATE_TAB_Y = 34; private static final int CREATE_TAB_W = 70;  private static final int CREATE_TAB_H = 20;
    private static final int ASSIGN_TAB_X = 242; private static final int ASSIGN_TAB_Y = 34; private static final int ASSIGN_TAB_W = 70;  private static final int ASSIGN_TAB_H = 20;
    private static final int SUBS_TAB_X   = 319; private static final int SUBS_TAB_Y   = 34; private static final int SUBS_TAB_W   = 90;  private static final int SUBS_TAB_H   = 20;

    // ── Detail action buttons ─────────────────────────────────────────────────
    private static final int DETAIL_BUTTON_X   = 260;
    private static final int DETAIL_BUTTON_Y   = 194;
    private static final int DETAIL_BUTTON_W   = 40;
    private static final int DETAIL_BUTTON_H   = 12;
    private static final int DETAIL_BUTTON_GAP = 4;

    // ── Create tab ────────────────────────────────────────────────────────────
    private static final int CR_X = 22, CR_W = 192, CR_ROW_H = 12, CR_DESC_H = 38, DESC_MAX = 192;
    private static final int CR_Y_NAME = 62, CR_Y_DESC = 78, CR_Y_DIV = 120, CR_Y_OBJ = 136;
    private static final int CR_Y_TARGET = 152, CR_Y_AMOUNT = 168, CR_Y_REWARD = 180;
    private static final int CR_RX = 220, CR_Y_APPROV = 62, CR_Y_HINT1 = 82, CR_Y_HINT2 = 92;
    private static final int CR_Y_CBTN = 192, CR_BTN_W = 106, CR_BTN_H = 14;

    private static final int VISIBLE_ROWS         = LIST_HEIGHT / ROW_HEIGHT;
    private static final int DETAIL_LINE_H        = 10;
    private static final int DETAIL_TITLE_H       = 14;
    private static final int DETAIL_USABLE_H      = DETAIL_HEIGHT - 18 - DETAIL_TITLE_H;
    private static final int VISIBLE_DETAIL_LINES = DETAIL_USABLE_H / DETAIL_LINE_H;

    // ── Submissions tab — stacked layout ──────────────────────────────────────
    // Full width, list on top, log below.
    // Content area: x=22..408 (386px wide), y=58..192 (134px tall)
    private static final int SUB_X         = 22;    // left edge
    private static final int SUB_W         = 386;   // full usable width
    private static final int SUB_TOP_Y     = 58;    // top of content area
    // List: 4 compact rows of 14px each = 56px
    private static final int SUB_ROW_H     = 14;
    private static final int SUB_ROWS      = 4;
    private static final int SUB_LIST_H    = SUB_ROW_H * SUB_ROWS;  // 56
    // Divider at y=58+56=114
    private static final int SUB_DIV_Y     = SUB_TOP_Y + SUB_LIST_H;     // 114
    // Log header at y=116
    private static final int SUB_HDR_Y     = SUB_DIV_Y + 2;              // 116
    // Log lines start at y=127 (header=10px)
    private static final int SUB_LOG_Y     = SUB_HDR_Y + 10;             // 126
    private static final int SUB_LOG_LH    = 10;
    private static final int SUB_LOG_LINES = 5;
    private static final int SUB_LOG_H     = SUB_LOG_LH * SUB_LOG_LINES; // 50
    // Buttons at y=126+50+2=178
    private static final int SUB_BTN_Y2    = SUB_LOG_Y + SUB_LOG_H + 2;  // 178
    private static final int SUB_BTN_H2    = 13;
    private static final int SUB_CONFIRM_W = 68;
    private static final int SUB_DISPUTE_W = 68;
    private static final int SUB_BTN_GAP2  = 6;
    // Max chars that fit in SUB_W (~6px/char avg)
    private static final int SUB_LOG_CHARS = 60;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private int listScrollOffset   = 0;
    private int detailScrollOffset = 0;
    private int subListScroll      = 0;
    private int subLogScroll       = 0;

    // ── State ─────────────────────────────────────────────────────────────────
    private AdminTab          selectedTab         = AdminTab.TASKS;
    private CreateField       selectedCreateField = CreateField.DISPLAY_NAME;
    private AssignField       selectedAssignField = AssignField.MODE;
    private AssignMode        assignMode          = AssignMode.PLAYER;

    private String            createDisplayName                 = "";
    private String            createDescription                 = "";
    private Division          createDivision                    = Division.OPERATIONS;
    private TaskObjectiveType createObjectiveType               = TaskObjectiveType.BREAK_BLOCK;
    private String            createTargetId                    = "";
    private int               createRequiredAmount              = 1;
    private int               createRewardPoints                = 10;
    private boolean           createOfficerConfirmationRequired = false;
    private String            assignPlayerName                  = "";
    private Division          assignDivision                    = Division.OPERATIONS;
    private int               validationFlashTicks              = 0;
    private CreateField       validationFlashField              = null;

    private String  disputeNote        = "";
    private boolean enteringDisputeNote = false;
    private boolean showResolved        = false; // toggle: show confirmed/disputed

    public enum CreateField  { DISPLAY_NAME, DESCRIPTION, DIVISION, OBJECTIVE_TYPE, TARGET_ID, REQUIRED_AMOUNT, REWARD_POINTS, OFFICER_CONFIRMATION, CREATE_BUTTON }
    public enum AssignMode   { PLAYER, DIVISION, PUBLIC }
    public enum AssignField  { MODE, PLAYER_NAME, DIVISION, ASSIGN_BUTTON }
    private enum AdminTab    { TASKS, DETAIL, CREATE, ASSIGN, SUBMISSIONS }
    private enum DetailAction{ APPROVE, RETURN, FAIL, CANCEL }

    public OperationsPadScreen(AdminTaskScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth  = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
        this.playerInventoryTitleY = 10000;
    }

    @Override protected void init() { super.init(); this.titleX = 0; this.titleY = 0; }
    public AdminTaskScreenHandler getScreenHandler() { return this.handler; }

    public void handleRefreshApplied() {
        if (selectedTab == AdminTab.DETAIL && handler.getSelectedTask() == null)
            selectedTab = AdminTab.TASKS;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (validationFlashTicks > 0) validationFlashTicks--;
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, TEX_WIDTH, TEX_HEIGHT);
        drawTabHighlight(ctx, x, y);
        drawTabLabels(ctx, x, y);
        switch (selectedTab) {
            case TASKS       -> drawTaskList(ctx, x, y);
            case DETAIL      -> drawTaskDetails(ctx, x, y);
            case CREATE      -> drawCreateTab(ctx, x, y);
            case ASSIGN      -> drawAssignTab(ctx, x, y);
            case SUBMISSIONS -> drawSubmissionsTab(ctx, x, y);
        }
    }

    @Override protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {}

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void drawTabHighlight(DrawContext ctx, int x, int y) {
        int hl = 0x10FFFFFF;
        switch (selectedTab) {
            case TASKS       -> ctx.fill(x+TASKS_TAB_X,  y+TASKS_TAB_Y,  x+TASKS_TAB_X+TASKS_TAB_W,   y+TASKS_TAB_Y+TASKS_TAB_H,   hl);
            case DETAIL      -> ctx.fill(x+DETAIL_TAB_X, y+DETAIL_TAB_Y, x+DETAIL_TAB_X+DETAIL_TAB_W, y+DETAIL_TAB_Y+DETAIL_TAB_H, hl);
            case CREATE      -> ctx.fill(x+CREATE_TAB_X, y+CREATE_TAB_Y, x+CREATE_TAB_X+CREATE_TAB_W, y+CREATE_TAB_Y+CREATE_TAB_H, hl);
            case ASSIGN      -> ctx.fill(x+ASSIGN_TAB_X, y+ASSIGN_TAB_Y, x+ASSIGN_TAB_X+ASSIGN_TAB_W, y+ASSIGN_TAB_Y+ASSIGN_TAB_H, hl);
            case SUBMISSIONS -> ctx.fill(x+SUBS_TAB_X,   y+SUBS_TAB_Y,   x+SUBS_TAB_X+SUBS_TAB_W,     y+SUBS_TAB_Y+SUBS_TAB_H,     hl);
        }
    }

    private void drawTabLabels(DrawContext ctx, int x, int y) {
        drawCenteredTabText(ctx, "TASKS",  x+TASKS_TAB_X,  y+TASKS_TAB_Y,  TASKS_TAB_W,  TASKS_TAB_H);
        drawCenteredTabText(ctx, "DETAIL", x+DETAIL_TAB_X, y+DETAIL_TAB_Y, DETAIL_TAB_W, DETAIL_TAB_H);
        drawCenteredTabText(ctx, "CREATE", x+CREATE_TAB_X, y+CREATE_TAB_Y, CREATE_TAB_W, CREATE_TAB_H);
        drawCenteredTabText(ctx, "ASSIGN", x+ASSIGN_TAB_X, y+ASSIGN_TAB_Y, ASSIGN_TAB_W, ASSIGN_TAB_H);
        long pending = handler.getSubmissions().stream()
                .filter(s -> "PENDING".equals(s.status())).count();
        String subsLabel = pending > 0 ? "PADS [" + pending + "]" : "PADS";
        drawCenteredTabText(ctx, subsLabel, x+SUBS_TAB_X, y+SUBS_TAB_Y, SUBS_TAB_W, SUBS_TAB_H);
    }

    private void drawCenteredTabText(DrawContext ctx, String text, int bx, int by, int bw, int bh) {
        int tw = this.textRenderer.getWidth(text);
        ctx.drawText(this.textRenderer, text, bx+(bw-tw)/2, by+(bh-8)/2+3, 0xFFF2E7D5, false);
    }

    // ── TASKS tab ─────────────────────────────────────────────────────────────

    private void drawTaskList(DrawContext ctx, int x, int y) {
        List<AdminTaskViewModel> tasks = handler.getTasks();
        clampListScroll(tasks.size());
        ctx.enableScissor(x+LIST_X, y+LIST_Y, x+LIST_X+LIST_WIDTH, y+LIST_Y+LIST_HEIGHT);
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int di = i + listScrollOffset; if (di >= tasks.size()) break;
            int rx = x+LIST_X, ry = y+LIST_Y+(i*ROW_HEIGHT);
            boolean sel = di == handler.getSelectedIndex();
            ctx.fill(rx+2, ry+2, rx+LIST_WIDTH-6, ry+ROW_HEIGHT-4, sel ? 0x14FFFFFF : 0x06000000);
            AdminTaskViewModel task = tasks.get(di);
            ctx.drawText(this.textRenderer, trim(task.getTitle(),  18), rx+8, ry+6,  0xFFF2E7D5, false);
            ctx.drawText(this.textRenderer, trim(task.getStatus(), 18), rx+8, ry+16, 0xFFD0D0D0, false);
        }
        ctx.disableScissor();
        drawScrollIndicators(ctx, x+LIST_X, y+LIST_Y, LIST_WIDTH, LIST_HEIGHT, listScrollOffset, tasks.size(), VISIBLE_ROWS);
    }

    // ── DETAIL tab ────────────────────────────────────────────────────────────

    private void drawTaskDetails(DrawContext ctx, int x, int y) {
        AdminTaskViewModel selected = handler.getSelectedTask();
        int tx = x+DETAIL_X+4, ty = y+DETAIL_Y+2;
        ctx.enableScissor(x+DETAIL_X, y+DETAIL_Y, x+DETAIL_X+DETAIL_WIDTH, y+DETAIL_Y+DETAIL_HEIGHT-18);
        if (selected == null) {
            ctx.drawText(this.textRenderer, "No task selected", tx, ty, 0xFFF2E7D5, false);
            ctx.disableScissor(); drawDetailButtons(ctx, x, y); return;
        }
        ctx.drawText(this.textRenderer, trim(selected.getTitle(), 20), tx, ty, 0xFFF2E7D5, false);
        ty += DETAIL_TITLE_H;
        List<String> lines = selected.getDetailLines();
        clampDetailScroll(lines.size());
        for (int i = 0; i < VISIBLE_DETAIL_LINES; i++) {
            int di = i + detailScrollOffset; if (di >= lines.size()) break;
            ctx.drawText(this.textRenderer, trim(lines.get(di), 22), tx, ty, getDetailLineColor(lines.get(di)), false);
            ty += DETAIL_LINE_H;
        }
        ctx.disableScissor();
        drawScrollIndicators(ctx, x+DETAIL_X, y+DETAIL_Y+DETAIL_TITLE_H, DETAIL_WIDTH,
                DETAIL_HEIGHT-18-DETAIL_TITLE_H, detailScrollOffset, lines.size(), VISIBLE_DETAIL_LINES);
        drawDetailButtons(ctx, x, y);
    }

    private void drawDetailButtons(DrawContext ctx, int x, int y) {
        drawDetailActionButton(ctx, x+DETAIL_BUTTON_X,                                       y+DETAIL_BUTTON_Y, "APP",  DetailAction.APPROVE);
        drawDetailActionButton(ctx, x+DETAIL_BUTTON_X+(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),   y+DETAIL_BUTTON_Y, "BACK", DetailAction.RETURN);
        drawDetailActionButton(ctx, x+DETAIL_BUTTON_X+2*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP), y+DETAIL_BUTTON_Y, "FAIL", DetailAction.FAIL);
        drawDetailActionButton(ctx, x+DETAIL_BUTTON_X+3*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP), y+DETAIL_BUTTON_Y, "X",    DetailAction.CANCEL);
    }

    // ── SUBMISSIONS tab — stacked layout ──────────────────────────────────────

    private void drawSubmissionsTab(DrawContext ctx, int x, int y) {
        List<PushSubmissionsS2CPacket.SubmissionEntry> subs = handler.getSubmissions();
        subListScroll = clamp(subListScroll, 0, Math.max(0, subs.size() - SUB_ROWS));

        int lx = x + SUB_X;

        // ── List (top, full width) ────────────────────────────────────────────
        ctx.enableScissor(lx, y+SUB_TOP_Y, lx+SUB_W, y+SUB_TOP_Y+SUB_LIST_H);
        if (subs.isEmpty()) {
            ctx.drawText(this.textRenderer, "No submissions yet.",
                    lx+4, y+SUB_TOP_Y+4, 0xFF666666, false);
        } else {
            for (int i = 0; i < SUB_ROWS; i++) {
                int di = i + subListScroll;
                if (di >= subs.size()) break;
                PushSubmissionsS2CPacket.SubmissionEntry sub = subs.get(di);
                int ry = y + SUB_TOP_Y + i * SUB_ROW_H;
                boolean sel = di == handler.getSelectedSubmissionIndex();

                ctx.fill(lx, ry, lx+SUB_W, ry+SUB_ROW_H-1,
                        sel ? 0x20FFFFFF : (di % 2 == 0 ? 0x08FFFFFF : 0x04000000));

                // Status dot
                int dotCol = switch (sub.status()) {
                    case "CONFIRMED" -> 0xFF2D8214;
                    case "DISPUTED"  -> 0xFFA01C12;
                    default          -> 0xFFB97308;
                };
                ctx.fill(lx+3, ry+4, lx+7, ry+9, dotCol);

                // Submitter label — wide now, 32 chars fits ~192px
                ctx.drawText(this.textRenderer, trim(sub.displayLabel(), 32),
                        lx+12, ry+3, sel ? 0xFFFFFFFF : 0xFFF2E7D5, false);

                // Date — right side
                ctx.drawText(this.textRenderer, sub.submittedAt(),
                        lx+210, ry+3, 0xFF888888, false);

                // Status — far right
                int statusCol = dotCol;
                ctx.drawText(this.textRenderer, sub.status(),
                        lx+SUB_W - textRenderer.getWidth(sub.status()) - 2,
                        ry+3, statusCol, false);
            }
        }
        ctx.disableScissor();

        // List scroll arrows
        if (subs.size() > SUB_ROWS) {
            if (subListScroll > 0)
                ctx.drawText(this.textRenderer, "^", lx+SUB_W-8, y+SUB_TOP_Y, 0xFFAAAAAA, false);
            if (subListScroll + SUB_ROWS < subs.size())
                ctx.drawText(this.textRenderer, "v", lx+SUB_W-8,
                        y+SUB_TOP_Y+SUB_LIST_H-8, 0xFFAAAAAA, false);
        }

        // ── Divider ───────────────────────────────────────────────────────────
        ctx.fill(lx, y+SUB_DIV_Y, lx+SUB_W, y+SUB_DIV_Y+1, 0x40FFFFFF);

        // ── Log (bottom, full width) ──────────────────────────────────────────
        PushSubmissionsS2CPacket.SubmissionEntry selSub = handler.getSelectedSubmission();
        List<String> log = handler.getSelectedSubmissionLog();
        subLogScroll = clamp(subLogScroll, 0, Math.max(0, log.size() - SUB_LOG_LINES));

        if (selSub == null) {
            ctx.drawText(this.textRenderer, "Click a submission above to view its log.",
                    lx+4, y+SUB_HDR_Y, 0xFF555555, false);
        } else {
            // Header line: name | date | status
            int statusHdrCol = switch (selSub.status()) {
                case "CONFIRMED" -> 0xFF2D8214;
                case "DISPUTED"  -> 0xFFA01C12;
                default          -> 0xFFB97308;
            };
            String hdr = trim(selSub.displayLabel(), 28) + "  "
                    + selSub.submittedAt() + "  " + selSub.status();
            ctx.drawText(this.textRenderer, hdr, lx+4, y+SUB_HDR_Y, statusHdrCol, false);

            // Log lines
            ctx.enableScissor(lx, y+SUB_LOG_Y, lx+SUB_W, y+SUB_LOG_Y+SUB_LOG_H);
            if (log.isEmpty()) {
                ctx.drawText(this.textRenderer, "(empty log)",
                        lx+4, y+SUB_LOG_Y, 0xFF444444, false);
            } else {
                for (int i = 0; i < SUB_LOG_LINES; i++) {
                    int di = i + subLogScroll;
                    if (di >= log.size()) break;
                    String line = log.get(di);
                    int lineCol = line.contains("| ROLL |") ? 0xFFD7820A
                            : line.contains("| RP |") ? 0xFFF2E7D5
                            : 0xFF888888;
                    // Trim to fit full width
                    String display = line.length() > SUB_LOG_CHARS
                            ? line.substring(0, SUB_LOG_CHARS-1) + "…" : line;
                    ctx.drawText(this.textRenderer, display,
                            lx+4, y+SUB_LOG_Y + i*SUB_LOG_LH, lineCol, false);
                }
            }
            ctx.disableScissor();

            // Log scroll arrows
            if (log.size() > SUB_LOG_LINES) {
                if (subLogScroll > 0)
                    ctx.drawText(this.textRenderer, "^", lx+SUB_W-8, y+SUB_LOG_Y, 0xFFAAAAAA, false);
                if (subLogScroll + SUB_LOG_LINES < log.size())
                    ctx.drawText(this.textRenderer, "v", lx+SUB_W-8,
                            y+SUB_LOG_Y+SUB_LOG_H-8, 0xFFAAAAAA, false);
            }
        }

        // ── Action buttons ────────────────────────────────────────────────────
        boolean canAct = selSub != null && "PENDING".equals(selSub.status());
        int bx = lx;
        int by = y + SUB_BTN_Y2;

        // CONFIRM
        ctx.fill(bx, by, bx+SUB_CONFIRM_W, by+SUB_BTN_H2,
                canAct ? 0x3038FF9A : 0x10FFFFFF);
        ctx.drawText(this.textRenderer, "✔ CONFIRM",
                bx+6, by+3, canAct ? 0xFF88FFB8 : 0xFF444444, false);

        // DISPUTE
        int bx2 = bx + SUB_CONFIRM_W + SUB_BTN_GAP2;
        ctx.fill(bx2, by, bx2+SUB_DISPUTE_W, by+SUB_BTN_H2,
                canAct ? 0x30FF6666 : 0x10FFFFFF);
        ctx.drawText(this.textRenderer, "✖ DISPUTE",
                bx2+6, by+3, canAct ? 0xFFFF9999 : 0xFF444444, false);

        // Dispute note input — shown to the right of buttons when active
        if (enteringDisputeNote) {
            int nx = bx2 + SUB_DISPUTE_W + SUB_BTN_GAP2;
            int nw = SUB_W - SUB_CONFIRM_W - SUB_DISPUTE_W - SUB_BTN_GAP2*2;
            ctx.fill(nx, by, nx+nw, by+SUB_BTN_H2, 0x40FFFFFF);
            // Show last 40 chars if long
            String nd = "Note: " + disputeNote + "_";
            if (nd.length() > 40) nd = nd.substring(nd.length()-40);
            ctx.drawText(this.textRenderer, nd, nx+3, by+3, 0xFFF2E7D5, false);
        } else if (selSub != null && !"PENDING".equals(selSub.status())) {
            int nx = bx2 + SUB_DISPUTE_W + SUB_BTN_GAP2;
            ctx.drawText(this.textRenderer, "Already resolved — archive PADD in inventory.",
                    nx, by+3, 0xFF555555, false);
        }

        // Show-resolved toggle — far right of button row, never overlaps list
        int tW = 72;
        int tX = lx + SUB_W - tW;
        int tY = by;
        ctx.fill(tX, tY, tX+tW, tY+SUB_BTN_H2,
                showResolved ? 0x30FFFFFF : 0x10FFFFFF);
        String tLabel = showResolved ? "Archive ON" : "Archive OFF";
        int tCol = showResolved ? 0xFFF2E7D5 : 0xFF555555;
        ctx.drawText(this.textRenderer, tLabel,
                tX + (tW - textRenderer.getWidth(tLabel)) / 2,
                tY + 3, tCol, false);
    }

    // ── CREATE tab ────────────────────────────────────────────────────────────

    private void drawCreateTab(DrawContext ctx, int x, int y) {
        String idPreview = createDisplayName.isBlank() ? "auto-generated" : toAutoId(createDisplayName);
        drawHintText(ctx, x+CR_X+4, y+CR_Y_NAME-12, "ID: " + idPreview);
        drawCreateRow(ctx, x, y, "NAME",     withCursor(createDisplayName, selectedCreateField==CreateField.DISPLAY_NAME), CR_X, CR_Y_NAME,   CR_ROW_H, selectedCreateField==CreateField.DISPLAY_NAME,   CreateField.DISPLAY_NAME);
        drawDescriptionArea(ctx, x, y);
        drawCreateRow(ctx, x, y, "DIV",      createDivision.name(),      CR_X, CR_Y_DIV,    CR_ROW_H, selectedCreateField==CreateField.DIVISION,       CreateField.DIVISION);
        drawCreateRow(ctx, x, y, "OBJ",      createObjectiveType.name(), CR_X, CR_Y_OBJ,    CR_ROW_H, selectedCreateField==CreateField.OBJECTIVE_TYPE,  CreateField.OBJECTIVE_TYPE);
        drawCreateRow(ctx, x, y, "TARGET",   withCursor(createTargetId, selectedCreateField==CreateField.TARGET_ID), CR_X, CR_Y_TARGET, CR_ROW_H, selectedCreateField==CreateField.TARGET_ID, CreateField.TARGET_ID);
        drawCreateRow(ctx, x, y, "AMOUNT",   String.valueOf(createRequiredAmount), CR_X, CR_Y_AMOUNT, CR_ROW_H, selectedCreateField==CreateField.REQUIRED_AMOUNT, CreateField.REQUIRED_AMOUNT);
        drawCreateRow(ctx, x, y, "REWARD",   String.valueOf(createRewardPoints),   CR_X, CR_Y_REWARD, CR_ROW_H, selectedCreateField==CreateField.REWARD_POINTS,   CreateField.REWARD_POINTS);
        drawCreateRow(ctx, x, y, "APPROVAL", createOfficerConfirmationRequired?"YES":"NO", CR_RX, CR_Y_APPROV, CR_ROW_H, selectedCreateField==CreateField.OFFICER_CONFIRMATION, CreateField.OFFICER_CONFIRMATION);
        drawHintText(ctx, x+CR_RX+2, y+CR_Y_HINT1, "LEFT CLICK = UP");
        drawHintText(ctx, x+CR_RX+2, y+CR_Y_HINT2, "RIGHT CLICK = DOWN");
        drawCreateButton(ctx, x, y);
    }

    private void drawDescriptionArea(DrawContext ctx, int x, int y) {
        int x1=x+CR_X, y1=y+CR_Y_DESC, x2=x1+CR_W, y2=y1+CR_DESC_H;
        boolean focused=selectedCreateField==CreateField.DESCRIPTION;
        boolean flashing=focused&&validationFlashField==CreateField.DESCRIPTION&&validationFlashTicks>0;
        ctx.fill(x1,y1,x2,y2,focused?0x22FFFFFF:0x0A000000);
        if(flashing){ctx.fill(x1,y1,x2,y1+1,0xAAFF4444);ctx.fill(x1,y2-1,x2,y2,0xAAFF4444);ctx.fill(x1,y1,x1+1,y2,0xAAFF4444);ctx.fill(x2-1,y1,x2,y2,0xAAFF4444);}
        ctx.drawText(this.textRenderer,"DESC",x1+4,y1+2,flashing?0xFFFF6666:0xFFFFB24A,false);
        String display=focused?createDescription+"_":createDescription;
        List<String> wrapped=wrapText(display,22); int lineY=y1+2;
        for(int i=0;i<Math.min(wrapped.size(),3);i++){ctx.drawText(this.textRenderer,wrapped.get(i),x1+34,lineY,0xFFF2E7D5,false);lineY+=11;}
        String counter=createDescription.length()+"/"+DESC_MAX;
        ctx.drawText(this.textRenderer,counter,x2-this.textRenderer.getWidth(counter)-3,y2-9,createDescription.length()>=DESC_MAX?0xFFFF6666:0xFF888888,false);
    }

    private void drawCreateButton(DrawContext ctx, int x, int y) {
        int x1=x+CR_RX, y1=y+CR_Y_CBTN;
        boolean sel=selectedCreateField==CreateField.CREATE_BUTTON;
        boolean canCreate=!createDisplayName.isBlank()&&!createDescription.isBlank()&&!createTargetId.isBlank();
        ctx.fill(x1,y1,x1+CR_BTN_W,y1+CR_BTN_H,sel?0x30FFFFFF:canCreate?0x1A00FF88:0x0A000000);
        String text="CREATE TASK"; int tw=this.textRenderer.getWidth(text);
        ctx.drawText(this.textRenderer,text,x1+(CR_BTN_W-tw)/2,y1+3,canCreate?0xFF88FFB8:0xFF888888,false);
    }

    // ── ASSIGN tab ────────────────────────────────────────────────────────────

    private void drawAssignTab(DrawContext ctx, int x, int y) {
        AdminTaskViewModel sel=handler.getSelectedTask();
        drawAssignRow(ctx,x,y,"TASK",  trim(sel==null?"NONE":sel.getTitle(),20),220,64,false);
        drawAssignRow(ctx,x,y,"MODE",  assignMode.name(),220,80,selectedAssignField==AssignField.MODE);
        drawAssignRow(ctx,x,y,"PLAYER",withCursor(assignPlayerName,selectedAssignField==AssignField.PLAYER_NAME),220,96,selectedAssignField==AssignField.PLAYER_NAME);
        drawAssignRow(ctx,x,y,"DIV",   assignDivision.name(),220,112,selectedAssignField==AssignField.DIVISION);
        drawAssignButton(ctx,x,y,220,136,selectedAssignField==AssignField.ASSIGN_BUTTON);
        String hint=switch(assignMode){case PLAYER->"Assign to online player";case DIVISION->"Send to division pool";case PUBLIC->"Publish to public pool";};
        drawHintText(ctx,x+222,y+156,hint);
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private void drawCreateRow(DrawContext ctx,int bX,int bY,String label,String value,int lX,int lY,int rH,boolean sel,CreateField f){
        int x1=bX+lX,y1=bY+lY,x2=x1+CR_W,y2=y1+rH;
        boolean fl=sel&&validationFlashField==f&&validationFlashTicks>0;
        ctx.fill(x1,y1,x2,y2,sel?0x20FFFFFF:0x0A000000);
        ctx.drawText(this.textRenderer,label,x1+4,y1+2,fl?0xFFFF6666:0xFFFFB24A,false);
        ctx.drawText(this.textRenderer,trim(value,20),x1+58,y1+2,0xFFF2E7D5,false);
    }
    private void drawAssignRow(DrawContext ctx,int bX,int bY,String label,String value,int lX,int lY,boolean sel){
        int x1=bX+lX,y1=bY+lY;ctx.fill(x1,y1,x1+180,y1+12,sel?0x20FFFFFF:0x0A000000);
        ctx.drawText(this.textRenderer,label,x1+4,y1+2,0xFF8FD7E8,false);
        ctx.drawText(this.textRenderer,trim(value,20),x1+58,y1+2,0xFFF2E7D5,false);
    }
    private void drawAssignButton(DrawContext ctx,int bX,int bY,int lX,int lY,boolean sel){
        int x1=bX+lX,y1=bY+lY;ctx.fill(x1,y1,x1+100,y1+14,sel?0x30FFFFFF:0x14000000);
        String t="ASSIGN TASK";int tw=this.textRenderer.getWidth(t);
        ctx.drawText(this.textRenderer,t,x1+(100-tw)/2,y1+3,0xFFF2E7D5,false);
    }
    private void drawDetailActionButton(DrawContext ctx,int x,int y,String label,DetailAction action){
        int col=switch(action){case APPROVE->0x2038FF9A;case RETURN->0x20FFE08A;case FAIL->0x20FF8A8A;case CANCEL->0x20D0D0D0;};
        ctx.fill(x,y,x+DETAIL_BUTTON_W,y+DETAIL_BUTTON_H,col);
        int tw=this.textRenderer.getWidth(label);
        ctx.drawText(this.textRenderer,label,x+(DETAIL_BUTTON_W-tw)/2,y+2,0xFFF2E7D5,false);
    }
    private void drawScrollIndicators(DrawContext ctx,int pX,int pY,int pW,int pH,int scroll,int total,int vis){
        if(total<=vis)return;int aX=pX+pW-8;
        if(scroll>0)ctx.drawText(this.textRenderer,"^",aX,pY+2,0xFFAAAAAA,false);
        if(scroll+vis<total)ctx.drawText(this.textRenderer,"v",aX,pY+pH-10,0xFFAAAAAA,false);
    }
    private void drawHintText(DrawContext ctx,int x,int y,String t){ctx.drawText(this.textRenderer,t,x,y,0xFFB8B8B8,false);}
    private String withCursor(String v,boolean sel){return sel?v+"_":v;}
    private int getDetailLineColor(String line){
        if(line==null||line.isBlank())return 0xFFFFFFFF;
        if(line.startsWith("Task ID:"))return 0xFFD0D0D0;
        if(line.startsWith("Description:"))return 0xFFFFFFFF;
        if(line.startsWith("Objective:")||line.startsWith("Target:")||line.startsWith("Reward:"))return 0xFFFFB24A;
        if(line.startsWith("Division:"))return 0xFF8FD7E8;
        if(line.startsWith("Status:"))return 0xFF9ED9D6;
        return 0xFFD7D7D7;
    }
    private String trim(String t,int max){if(t==null)return"";return t.length()<=max?t:t.substring(0,Math.max(0,max-3))+"...";}
    private String toAutoId(String n){return n.trim().toLowerCase().replaceAll("[^a-z0-9]","_").replaceAll("_+","_").replaceAll("^_|_$","");}
    private List<String> wrapText(String text,int maxChars){
        java.util.List<String> lines=new java.util.ArrayList<>();if(text==null||text.isEmpty())return lines;
        int start=0;while(start<text.length()){int end=Math.min(start+maxChars,text.length());
            if(end<text.length()){int ls=text.lastIndexOf(' ',end);if(ls>start)end=ls+1;}
            lines.add(text.substring(start,end));start=end;}return lines;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x=this.x, y=this.y;

        // Tab switching
        if(inside(mx,my,x+TASKS_TAB_X, y+TASKS_TAB_Y, TASKS_TAB_W, TASKS_TAB_H)) { selectedTab=AdminTab.TASKS; return true; }
        if(inside(mx,my,x+DETAIL_TAB_X,y+DETAIL_TAB_Y,DETAIL_TAB_W,DETAIL_TAB_H)){ selectedTab=AdminTab.DETAIL; return true; }
        if(inside(mx,my,x+CREATE_TAB_X,y+CREATE_TAB_Y,CREATE_TAB_W,CREATE_TAB_H)){ selectedTab=AdminTab.CREATE; return true; }
        if(inside(mx,my,x+ASSIGN_TAB_X,y+ASSIGN_TAB_Y,ASSIGN_TAB_W,ASSIGN_TAB_H)){ selectedTab=AdminTab.ASSIGN; return true; }
        if(inside(mx,my,x+SUBS_TAB_X,  y+SUBS_TAB_Y,  SUBS_TAB_W,  SUBS_TAB_H)) {
            selectedTab=AdminTab.SUBMISSIONS;
            ClientPlayNetworking.send(new SelectSubmissionC2SPacket(
                    handler.getSelectedSubmissionId() != null ? handler.getSelectedSubmissionId() : "", showResolved));
            return true;
        }

        if(selectedTab==AdminTab.SUBMISSIONS) {
            int lx=x+SUB_X;
            // List rows
            List<PushSubmissionsS2CPacket.SubmissionEntry> subs=handler.getSubmissions();
            for(int i=0;i<SUB_ROWS;i++){
                int di=i+subListScroll; if(di>=subs.size())break;
                int ry=y+SUB_TOP_Y+i*SUB_ROW_H;
                if(inside(mx,my,lx,ry,SUB_W,SUB_ROW_H-1)){
                    handler.setSelectedSubmissionIndex(di);
                    subLogScroll=0; enteringDisputeNote=false;
                    ClientPlayNetworking.send(new SelectSubmissionC2SPacket(subs.get(di).submissionId(), showResolved));
                    return true;
                }
            }
            // Action buttons
            PushSubmissionsS2CPacket.SubmissionEntry sel=handler.getSelectedSubmission();
            {
                // Show-resolved toggle click — far right of button row
                int tW2=72;
                int tX2=x+SUB_X+SUB_W-tW2;
                int tY2=y+SUB_BTN_Y2;
                if(inside(mx,my,tX2,tY2,tW2,SUB_BTN_H2)){
                    showResolved=!showResolved;
                    subListScroll=0;
                    ClientPlayNetworking.send(new SelectSubmissionC2SPacket("",showResolved));
                    return true;
                }
            }
            if(sel!=null&&"PENDING".equals(sel.status())){
                int by=y+SUB_BTN_Y2;
                // CONFIRM
                if(inside(mx,my,lx,by,SUB_CONFIRM_W,SUB_BTN_H2)){
                    ClientPlayNetworking.send(new ReviewSubmissionC2SPacket(sel.submissionId(),"CONFIRM","",""));
                    enteringDisputeNote=false;
                    // After action, re-request list with current filter
                    ClientPlayNetworking.send(new SelectSubmissionC2SPacket("", showResolved));
                    return true;
                }
                // DISPUTE
                int bx2=lx+SUB_CONFIRM_W+SUB_BTN_GAP2;
                if(inside(mx,my,bx2,by,SUB_DISPUTE_W,SUB_BTN_H2)){
                    if(enteringDisputeNote){
                        ClientPlayNetworking.send(new ReviewSubmissionC2SPacket(sel.submissionId(),"DISPUTE","",disputeNote));
                        enteringDisputeNote=false; disputeNote="";
                    } else { enteringDisputeNote=true; disputeNote=""; }
                    return true;
                }
            }
        }

        if(selectedTab==AdminTab.CREATE){
            if(inside(mx,my,x+CR_X,y+CR_Y_NAME,  CR_W,CR_ROW_H)) { selectedCreateField=CreateField.DISPLAY_NAME; return true; }
            if(inside(mx,my,x+CR_X,y+CR_Y_DESC,  CR_W,CR_DESC_H)){ selectedCreateField=CreateField.DESCRIPTION; return true; }
            if(inside(mx,my,x+CR_X,y+CR_Y_DIV,   CR_W,CR_ROW_H)) { selectedCreateField=CreateField.DIVISION; cycleCreateDivision(); return true; }
            if(inside(mx,my,x+CR_X,y+CR_Y_OBJ,   CR_W,CR_ROW_H)) { selectedCreateField=CreateField.OBJECTIVE_TYPE; cycleCreateObjectiveType(); return true; }
            if(inside(mx,my,x+CR_X,y+CR_Y_TARGET,CR_W,CR_ROW_H)) { selectedCreateField=CreateField.TARGET_ID; return true; }
            if(inside(mx,my,x+CR_X,y+CR_Y_AMOUNT,CR_W,CR_ROW_H)) { selectedCreateField=CreateField.REQUIRED_AMOUNT; if(button==0)incrementCreateRequiredAmount(); else decrementCreateRequiredAmount(); return true; }
            if(inside(mx,my,x+CR_X,y+CR_Y_REWARD,CR_W,CR_ROW_H)) { selectedCreateField=CreateField.REWARD_POINTS; if(button==0)incrementCreateRewardPoints(); else decrementCreateRewardPoints(); return true; }
            if(inside(mx,my,x+CR_RX,y+CR_Y_APPROV,CR_W,CR_ROW_H)){ selectedCreateField=CreateField.OFFICER_CONFIRMATION; toggleCreateOfficerConfirmationRequired(); return true; }
            if(inside(mx,my,x+CR_RX,y+CR_Y_CBTN,CR_BTN_W,CR_BTN_H)){ selectedCreateField=CreateField.CREATE_BUTTON; tryCreateTask(); return true; }
        }
        if(selectedTab==AdminTab.ASSIGN){
            if(inside(mx,my,x+220,y+80, 180,12)){ selectedAssignField=AssignField.MODE; cycleAssignMode(); return true; }
            if(inside(mx,my,x+220,y+96, 180,12)){ selectedAssignField=AssignField.PLAYER_NAME; return true; }
            if(inside(mx,my,x+220,y+112,180,12)){ selectedAssignField=AssignField.DIVISION; cycleAssignDivision(); return true; }
            if(inside(mx,my,x+220,y+136,100,14)){ selectedAssignField=AssignField.ASSIGN_BUTTON; sendAssignTaskPacket(); return true; }
        }
        if(selectedTab==AdminTab.DETAIL){
            AdminTaskViewModel sel=handler.getSelectedTask();
            if(sel!=null){
                String tid=sel.getTaskId();
                if(inside(mx,my,x+DETAIL_BUTTON_X,                                       y+DETAIL_BUTTON_Y,DETAIL_BUTTON_W,DETAIL_BUTTON_H)){sendReviewActionPacket(tid,"APPROVE");return true;}
                if(inside(mx,my,x+DETAIL_BUTTON_X+(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),   y+DETAIL_BUTTON_Y,DETAIL_BUTTON_W,DETAIL_BUTTON_H)){sendReviewActionPacket(tid,"RETURN"); return true;}
                if(inside(mx,my,x+DETAIL_BUTTON_X+2*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP), y+DETAIL_BUTTON_Y,DETAIL_BUTTON_W,DETAIL_BUTTON_H)){sendReviewActionPacket(tid,"FAIL");   return true;}
                if(inside(mx,my,x+DETAIL_BUTTON_X+3*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP), y+DETAIL_BUTTON_Y,DETAIL_BUTTON_W,DETAIL_BUTTON_H)){sendReviewActionPacket(tid,"CANCEL"); return true;}
            }
        }
        if(selectedTab==AdminTab.TASKS||selectedTab==AdminTab.DETAIL||selectedTab==AdminTab.ASSIGN){
            List<AdminTaskViewModel> tasks=handler.getTasks();
            for(int i=0;i<VISIBLE_ROWS;i++){
                int di=i+listScrollOffset; if(di>=tasks.size())break;
                int rx=x+LIST_X,ry=y+LIST_Y+(i*ROW_HEIGHT);
                if(inside(mx,my,rx+2,ry+2,LIST_WIDTH-8,ROW_HEIGHT-6)){
                    handler.setSelectedIndex(di); detailScrollOffset=0;
                    if(selectedTab==AdminTab.TASKS)selectedTab=AdminTab.DETAIL;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double h,double v){
        int x=this.x,y=this.y; int delta=v>0?-1:1;
        if(selectedTab==AdminTab.SUBMISSIONS){
            int lx=x+SUB_X;
            if(inside(mx,my,lx,y+SUB_TOP_Y,SUB_W,SUB_LIST_H)){
                subListScroll=clamp(subListScroll+delta,0,Math.max(0,handler.getSubmissions().size()-SUB_ROWS));
                return true;
            }
            if(inside(mx,my,lx,y+SUB_LOG_Y,SUB_W,SUB_LOG_H)){
                subLogScroll=clamp(subLogScroll+delta,0,Math.max(0,handler.getSelectedSubmissionLog().size()-SUB_LOG_LINES));
                return true;
            }
        }
        if(inside(mx,my,x+LIST_X,y+LIST_Y,LIST_WIDTH,LIST_HEIGHT)){
            listScrollOffset=clamp(listScrollOffset+delta,0,Math.max(0,handler.getTasks().size()-VISIBLE_ROWS));
            return true;
        }
        if(selectedTab==AdminTab.DETAIL&&inside(mx,my,x+DETAIL_X,y+DETAIL_Y,DETAIL_WIDTH,DETAIL_HEIGHT)){
            AdminTaskViewModel sel=handler.getSelectedTask();
            if(sel!=null) detailScrollOffset=clamp(detailScrollOffset+delta,0,Math.max(0,sel.getDetailLines().size()-VISIBLE_DETAIL_LINES));
            return true;
        }
        return super.mouseScrolled(mx,my,h,v);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    @Override
    public boolean charTyped(char chr,int modifiers){
        if(selectedTab==AdminTab.SUBMISSIONS&&enteringDisputeNote){if(chr>=32&&chr!=127&&disputeNote.length()<80)disputeNote+=chr;return true;}
        if(selectedTab==AdminTab.CREATE&&isCreateTypingField(selectedCreateField)){appendToSelectedCreateField(chr);return true;}
        if(selectedTab==AdminTab.ASSIGN&&selectedAssignField==AssignField.PLAYER_NAME){appendToSelectedAssignField(chr);return true;}
        return super.charTyped(chr,modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode,int scanCode,int modifiers){
        if(selectedTab==AdminTab.SUBMISSIONS&&enteringDisputeNote){
            if(keyCode==GLFW.GLFW_KEY_BACKSPACE&&!disputeNote.isEmpty()){disputeNote=disputeNote.substring(0,disputeNote.length()-1);return true;}
            if(keyCode==GLFW.GLFW_KEY_ESCAPE){enteringDisputeNote=false;disputeNote="";return true;}
            if(keyCode==GLFW.GLFW_KEY_ENTER||keyCode==GLFW.GLFW_KEY_KP_ENTER){
                PushSubmissionsS2CPacket.SubmissionEntry sel=handler.getSelectedSubmission();
                if(sel!=null)ClientPlayNetworking.send(new ReviewSubmissionC2SPacket(sel.submissionId(),"DISPUTE","",disputeNote));
                enteringDisputeNote=false;disputeNote="";return true;
            }
            return true;
        }
        if(selectedTab==AdminTab.CREATE){
            if(isCreateTypingField(selectedCreateField)){
                if(keyCode==GLFW.GLFW_KEY_BACKSPACE){backspaceSelectedCreateField();return true;}
                if(keyCode!=GLFW.GLFW_KEY_ESCAPE&&keyCode!=GLFW.GLFW_KEY_ENTER&&keyCode!=GLFW.GLFW_KEY_KP_ENTER&&keyCode!=GLFW.GLFW_KEY_TAB)return true;
            }
            if((keyCode==GLFW.GLFW_KEY_ENTER||keyCode==GLFW.GLFW_KEY_KP_ENTER)&&selectedCreateField==CreateField.CREATE_BUTTON){tryCreateTask();return true;}
        }
        if(selectedTab==AdminTab.ASSIGN){
            if(selectedAssignField==AssignField.PLAYER_NAME){
                if(keyCode==GLFW.GLFW_KEY_BACKSPACE){backspaceSelectedAssignField();return true;}
                if(keyCode!=GLFW.GLFW_KEY_ESCAPE&&keyCode!=GLFW.GLFW_KEY_ENTER&&keyCode!=GLFW.GLFW_KEY_KP_ENTER&&keyCode!=GLFW.GLFW_KEY_TAB)return true;
            }
            if((keyCode==GLFW.GLFW_KEY_ENTER||keyCode==GLFW.GLFW_KEY_KP_ENTER)&&selectedAssignField==AssignField.ASSIGN_BUTTON){sendAssignTaskPacket();return true;}
        }
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void tryCreateTask(){if(createDisplayName.isBlank()){flashField(CreateField.DISPLAY_NAME);return;}if(createDescription.isBlank()){flashField(CreateField.DESCRIPTION);return;}if(createTargetId.isBlank()){flashField(CreateField.TARGET_ID);return;}sendCreateTaskPacket();resetCreateForm();}
    private void flashField(CreateField f){validationFlashField=f;validationFlashTicks=40;selectedCreateField=f;}
    private void resetCreateForm(){createDisplayName="";createDescription="";createDivision=Division.OPERATIONS;createObjectiveType=TaskObjectiveType.BREAK_BLOCK;createTargetId="";createRequiredAmount=1;createRewardPoints=10;createOfficerConfirmationRequired=false;selectedCreateField=CreateField.DISPLAY_NAME;validationFlashField=null;validationFlashTicks=0;}
    private void sendCreateTaskPacket(){ClientPlayNetworking.send(new CreateTaskC2SPacket("",createDisplayName,createDescription,createDivision.name(),createObjectiveType.name(),createTargetId,createRequiredAmount,createRewardPoints,createOfficerConfirmationRequired));}
    private void sendAssignTaskPacket(){AdminTaskViewModel sel=handler.getSelectedTask();if(sel==null)return;ClientPlayNetworking.send(new AssignTaskC2SPacket(sel.getTaskId(),assignMode.name(),assignPlayerName,assignDivision.name()));}
    private void sendReviewActionPacket(String tid,String action){ClientPlayNetworking.send(new ReviewTaskActionC2SPacket(tid,action));}
    private boolean isCreateTypingField(CreateField f){return f==CreateField.DISPLAY_NAME||f==CreateField.DESCRIPTION||f==CreateField.TARGET_ID;}
    private void appendToSelectedCreateField(char c){if(!isAllowedChar(c))return;switch(selectedCreateField){case DISPLAY_NAME->{if(createDisplayName.length()<48)createDisplayName+=c;}case DESCRIPTION->{if(createDescription.length()<DESC_MAX)createDescription+=c;}case TARGET_ID->{if(createTargetId.length()<48)createTargetId+=c;}default->{}}}
    private void backspaceSelectedCreateField(){switch(selectedCreateField){case DISPLAY_NAME->createDisplayName=backspace(createDisplayName);case DESCRIPTION->createDescription=backspace(createDescription);case TARGET_ID->createTargetId=backspace(createTargetId);default->{}}}
    private void appendToSelectedAssignField(char c){if(!isAllowedChar(c))return;if(selectedAssignField==AssignField.PLAYER_NAME&&assignPlayerName.length()<32)assignPlayerName+=c;}
    private void backspaceSelectedAssignField(){if(selectedAssignField==AssignField.PLAYER_NAME)assignPlayerName=backspace(assignPlayerName);}
    private void cycleCreateDivision(){Division[]v=Division.values();createDivision=v[(createDivision.ordinal()+1)%v.length];}
    private void cycleCreateObjectiveType(){TaskObjectiveType[]v=TaskObjectiveType.values();createObjectiveType=v[(createObjectiveType.ordinal()+1)%v.length];}
    private void incrementCreateRequiredAmount(){createRequiredAmount=Math.min(999,createRequiredAmount+1);}
    private void decrementCreateRequiredAmount(){createRequiredAmount=Math.max(1,createRequiredAmount-1);}
    private void incrementCreateRewardPoints(){createRewardPoints=Math.min(9999,createRewardPoints+5);}
    private void decrementCreateRewardPoints(){createRewardPoints=Math.max(0,createRewardPoints-5);}
    private void toggleCreateOfficerConfirmationRequired(){createOfficerConfirmationRequired=!createOfficerConfirmationRequired;}
    private void cycleAssignMode(){AssignMode[]v=AssignMode.values();assignMode=v[(assignMode.ordinal()+1)%v.length];}
    private void cycleAssignDivision(){Division[]v=Division.values();assignDivision=v[(assignDivision.ordinal()+1)%v.length];}
    private String backspace(String v){return(v==null||v.isEmpty())?"":v.substring(0,v.length()-1);}
    private boolean isAllowedChar(char c){return c>=32&&c!=127;}
    private boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    private void clampListScroll(int t){listScrollOffset=clamp(listScrollOffset,0,Math.max(0,t-VISIBLE_ROWS));}
    private void clampDetailScroll(int t){detailScrollOffset=clamp(detailScrollOffset,0,Math.max(0,t-VISIBLE_DETAIL_LINES));}
    private int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}