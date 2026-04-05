package net.shard.seconddawnrp.transporter.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.transporter.TransporterControllerNetworking;
import net.shard.seconddawnrp.transporter.TransporterControllerNetworking.*;

import java.util.*;

/**
 * Transporter Controller Screen.
 * Extends HandledScreen to suppress Minecraft's world blur effect.
 */
public class TransporterControllerScreen extends HandledScreen<TransporterScreenHandler> {

    private static final int GUI_W = 400;
    private static final int GUI_H = 240;

    private final Set<String> selectedPlayers = new HashSet<>();
    private String selectedDestinationId = null;
    private boolean destinationIsDimension = true;

    // Colors
    private static final int COL_BG       = 0xE0101820;
    private static final int COL_TITLE_BG = 0xFF1A2840;
    private static final int COL_BORDER   = 0xFF2A3C55;
    private static final int COL_HEADER   = 0xFF8FD7E8;
    private static final int COL_TEXT     = 0xFFF2E7D5;
    private static final int COL_DIM_C    = 0xFFAAAAAA;
    private static final int COL_ACTIVE   = 0xFF38FF9A;
    private static final int COL_INACTIVE = 0xFFFF6060;
    private static final int COL_SELECTED = 0x44FFFFFF;
    private static final int COL_BEAMUP   = 0xFFFFB24A;
    private static final int COL_BTN      = 0xFF1A3050;
    private static final int COL_BTN_HOV  = 0xFF2A4870;

    public TransporterControllerScreen(TransporterScreenHandler handler,
                                       PlayerInventory inventory,
                                       Text title) {
        super(handler, inventory, title);
        this.backgroundWidth  = GUI_W;
        this.backgroundHeight = GUI_H;
        this.playerInventoryTitleY = 10000; // hide inventory title
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

        // Background
        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, COL_BG);

        // Title bar
        ctx.fill(ox, oy, ox + GUI_W, oy + 22, COL_TITLE_BG);
        ctx.drawBorder(ox, oy, GUI_W, GUI_H, COL_BORDER);
        String title = "⬡ TRANSPORTER CONTROL";
        ctx.drawText(textRenderer, title,
                ox + (GUI_W - textRenderer.getWidth(title)) / 2,
                oy + 7, COL_HEADER, false);
        ctx.fill(ox + 4, oy + 22, ox + GUI_W - 4, oy + 23, COL_BORDER);

        int contentY = oy + 28;
        int halfW = (GUI_W - 16) / 2;

        // Left — ready players
        ctx.drawText(textRenderer, "READY PLAYERS", ox + 8, contentY, COL_HEADER, false);
        ctx.fill(ox + 4, contentY + 10, ox + 4 + halfW, contentY + 11, COL_BORDER);
        renderReadyPlayers(ctx, ox + 8, contentY + 14, halfW - 8, mouseX, mouseY);

        // Right — destinations
        int destX = ox + halfW + 12;
        ctx.drawText(textRenderer, "DESTINATION", destX, contentY, COL_HEADER, false);
        ctx.fill(destX - 4, contentY + 10, destX + halfW, contentY + 11, COL_BORDER);
        renderDestinations(ctx, destX, contentY + 14, halfW - 4, mouseX, mouseY);

        // Beam-up queue
        int beamY = contentY + 145;
        ctx.fill(ox + 4, beamY - 2, ox + GUI_W - 4, beamY - 1, COL_BORDER);
        ctx.drawText(textRenderer,
                beamUpRequests().isEmpty() ? "§7BEAM-UP REQUESTS"
                        : "§eBEAM-UP REQUESTS §c[" + beamUpRequests().size() + "]",
                ox + 8, beamY + 2, COL_BEAMUP, false);
        renderBeamUpQueue(ctx, ox + 8, beamY + 14, GUI_W - 16, mouseX, mouseY);

        // Energize button
        boolean canEnergize = !selectedPlayers.isEmpty() && selectedDestinationId != null;
        int btnX = ox + GUI_W / 2 - 55;
        int btnY = oy + GUI_H - 24;
        int btnColor = canEnergize
                ? (inBounds(mouseX, mouseY, btnX, btnY, 110, 16) ? COL_BTN_HOV : COL_BTN)
                : 0xFF111820;
        ctx.fill(btnX, btnY, btnX + 110, btnY + 16, btnColor);
        ctx.drawBorder(btnX, btnY, 110, 16, COL_BORDER);
        String btnLabel = canEnergize ? "§a⚡ ENERGIZE" : "§7⚡ ENERGIZE";
        ctx.drawText(textRenderer, btnLabel,
                btnX + (110 - textRenderer.getWidth(btnLabel)) / 2,
                btnY + 4, 0xFFFFFFFF, false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // Intentionally empty — drawBackground handles everything
    }

    // ── Sub-renderers ─────────────────────────────────────────────────────────

    private void renderReadyPlayers(DrawContext ctx, int x, int y, int w, int mx, int my) {
        List<ReadyPlayerData> ready = handler.getReady();
        if (ready.isEmpty()) {
            ctx.drawText(textRenderer, "§7No players ready", x, y, COL_DIM_C, false);
            ctx.drawText(textRenderer, "§8/transporter ready", x, y + 11, 0xFF556677, false);
            return;
        }
        int rowY = y;
        for (ReadyPlayerData p : ready) {
            boolean sel = selectedPlayers.contains(p.playerUuid());
            boolean hov = inBounds(mx, my, x - 2, rowY - 1, w, 12);
            if (sel)      ctx.fill(x - 2, rowY - 1, x + w, rowY + 11, COL_SELECTED);
            else if (hov) ctx.fill(x - 2, rowY - 1, x + w, rowY + 11, 0x22FFFFFF);
            ctx.drawText(textRenderer,
                    (sel ? "§a✓ " : "§7○ ") + "§f" + p.playerName(),
                    x, rowY, COL_TEXT, false);
            rowY += 13;
        }
    }

    private void renderDestinations(DrawContext ctx, int x, int y, int w, int mx, int my) {
        List<DestinationData> dims = handler.getDimensions();
        List<DestinationData> locs = handler.getShipLocations();
        int rowY = y;

        if (!dims.isEmpty()) {
            ctx.drawText(textRenderer, "§8── Dimensions", x, rowY, COL_DIM_C, false);
            rowY += 11;
            for (DestinationData dim : dims) {
                boolean sel = selectedDestinationId != null
                        && selectedDestinationId.equals(dim.id()) && destinationIsDimension;
                boolean hov = inBounds(mx, my, x - 2, rowY - 1, w, 12);
                if (sel)      ctx.fill(x - 2, rowY - 1, x + w, rowY + 11, COL_SELECTED);
                else if (hov) ctx.fill(x - 2, rowY - 1, x + w, rowY + 11, 0x22FFFFFF);
                ctx.drawText(textRenderer, "● ", x, rowY, dim.available() ? COL_ACTIVE : COL_INACTIVE, false);
                ctx.drawText(textRenderer,
                        (dim.available() ? "§f" : "§8") + dim.displayName(),
                        x + 10, rowY, COL_TEXT, false);
                rowY += 13;
            }
        }
        if (!locs.isEmpty()) {
            rowY += 3;
            ctx.drawText(textRenderer, "§8── Ship Locations", x, rowY, COL_DIM_C, false);
            rowY += 11;
            for (DestinationData loc : locs) {
                boolean sel = selectedDestinationId != null
                        && selectedDestinationId.equals(loc.id()) && !destinationIsDimension;
                boolean hov = inBounds(mx, my, x - 2, rowY - 1, w, 12);
                if (sel)      ctx.fill(x - 2, rowY - 1, x + w, rowY + 11, COL_SELECTED);
                else if (hov) ctx.fill(x - 2, rowY - 1, x + w, rowY + 11, 0x22FFFFFF);
                ctx.drawText(textRenderer, "● ", x, rowY, COL_ACTIVE, false);
                ctx.drawText(textRenderer, "§f" + loc.displayName(), x + 10, rowY, COL_TEXT, false);
                rowY += 13;
            }
        }
        if (dims.isEmpty() && locs.isEmpty()) {
            ctx.drawText(textRenderer, "§7No destinations available", x, rowY, COL_DIM_C, false);
            ctx.drawText(textRenderer, "§8/gm location activate <id>", x, rowY + 11, 0xFF556677, false);
        }
    }

    private void renderBeamUpQueue(DrawContext ctx, int x, int y, int w, int mx, int my) {
        List<BeamUpData> reqs = beamUpRequests();
        if (reqs.isEmpty()) {
            ctx.drawText(textRenderer, "§7No pending requests", x, y, COL_DIM_C, false);
            return;
        }
        int rowY = y;
        for (BeamUpData req : reqs) {
            long agoSec = (System.currentTimeMillis() - req.requestedAt()) / 1000;
            String ago = agoSec < 60 ? agoSec + "s ago" : (agoSec / 60) + "m ago";
            int btnX = x + w - 58;
            boolean btnHov = inBounds(mx, my, btnX, rowY - 1, 56, 12);
            ctx.fill(btnX, rowY - 1, btnX + 56, rowY + 11, btnHov ? COL_BTN_HOV : COL_BTN);
            ctx.drawBorder(btnX, rowY - 1, 56, 12, COL_BORDER);
            ctx.drawText(textRenderer, "APPROVE",
                    btnX + (56 - textRenderer.getWidth("APPROVE")) / 2,
                    rowY + 1, COL_ACTIVE, false);
            ctx.drawText(textRenderer,
                    "§e" + req.playerName() + " §7— §b" + req.sourceDimension() + " §8" + ago,
                    x, rowY + 1, COL_TEXT, false);
            rowY += 15;
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int ox = this.x, oy = this.y;
        int contentY = oy + 28;
        int halfW = (GUI_W - 16) / 2;

        // Ready players
        int rowY = contentY + 14;
        for (ReadyPlayerData p : handler.getReady()) {
            if (inBounds(mouseX, mouseY, ox + 6, rowY - 1, halfW - 8, 12)) {
                if (selectedPlayers.contains(p.playerUuid()))
                    selectedPlayers.remove(p.playerUuid());
                else
                    selectedPlayers.add(p.playerUuid());
                return true;
            }
            rowY += 13;
        }

        // Dimensions
        int destX = ox + halfW + 12;
        rowY = contentY + 14;
        if (!handler.getDimensions().isEmpty()) {
            rowY += 11;
            for (DestinationData dim : handler.getDimensions()) {
                if (inBounds(mouseX, mouseY, destX - 2, rowY - 1, halfW - 4, 12)) {
                    if (dim.available()) { selectedDestinationId = dim.id(); destinationIsDimension = true; }
                    return true;
                }
                rowY += 13;
            }
        }
        if (!handler.getShipLocations().isEmpty()) {
            rowY += 14;
            for (DestinationData loc : handler.getShipLocations()) {
                if (inBounds(mouseX, mouseY, destX - 2, rowY - 1, halfW - 4, 12)) {
                    selectedDestinationId = loc.id(); destinationIsDimension = false;
                    return true;
                }
                rowY += 13;
            }
        }

        // Beam-up approve
        int beamY = contentY + 145 + 14;
        for (BeamUpData req : beamUpRequests()) {
            int btnX = ox + 8 + (GUI_W - 16) - 58;
            if (inBounds(mouseX, mouseY, btnX, beamY - 1, 56, 12)) {
                sendApproveBeamUp(req.requestId());
                return true;
            }
            beamY += 15;
        }

        // Energize
        int btnX = ox + GUI_W / 2 - 55;
        int btnY = oy + GUI_H - 24;
        if (inBounds(mouseX, mouseY, btnX, btnY, 110, 16)) {
            if (!selectedPlayers.isEmpty() && selectedDestinationId != null) {
                sendTransportAction();
                this.close();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void sendTransportAction() {
        TransporterControllerNetworking.ActionType type = destinationIsDimension
                ? TransporterControllerNetworking.ActionType.TRANSPORT_DIMENSION
                : TransporterControllerNetworking.ActionType.TRANSPORT_LOCATION;
        ClientPlayNetworking.send(new TransporterControllerNetworking.ActionPayload(
                type, selectedDestinationId, new ArrayList<>(selectedPlayers)));
    }

    private void sendApproveBeamUp(String requestId) {
        ClientPlayNetworking.send(new TransporterControllerNetworking.ActionPayload(
                TransporterControllerNetworking.ActionType.APPROVE_BEAMUP,
                requestId, List.of()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<BeamUpData> beamUpRequests() { return handler.getBeamUpRequests(); }

    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}