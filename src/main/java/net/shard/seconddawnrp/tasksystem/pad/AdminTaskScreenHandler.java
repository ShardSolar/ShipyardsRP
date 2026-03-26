package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.network.PushSubmissionsS2CPacket;
import net.shard.seconddawnrp.registry.ModScreenHandlers;

import java.util.ArrayList;
import java.util.List;

public class AdminTaskScreenHandler extends ScreenHandler {

    private final List<AdminTaskViewModel> tasks = new ArrayList<>();
    private int selectedIndex = -1;

    // ── Submission state (held client-side after S2C push) ────────────────────
    private List<PushSubmissionsS2CPacket.SubmissionEntry> submissions = new ArrayList<>();
    private String selectedSubmissionId = null;
    private List<String> selectedSubmissionLog = new ArrayList<>();
    private int selectedSubmissionIndex = -1;

    public AdminTaskScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.ADMIN_TASK_SCREEN, syncId);
        reloadTasks();
        if (!tasks.isEmpty()) selectedIndex = 0;
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    public void reloadTasks() {
        tasks.clear();
        tasks.addAll(SecondDawnRP.TASK_SERVICE.buildAdminTaskViews());
        if (tasks.isEmpty()) { selectedIndex = -1; return; }
        if (selectedIndex < 0 || selectedIndex >= tasks.size()) selectedIndex = 0;
    }

    public void replaceTasks(List<AdminTaskViewModel> newTasks) {
        String selectedTaskId = getSelectedTask() != null ? getSelectedTask().getTaskId() : null;
        tasks.clear();
        tasks.addAll(newTasks);
        if (tasks.isEmpty()) { selectedIndex = -1; return; }
        if (selectedTaskId != null) {
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).getTaskId().equals(selectedTaskId)) { selectedIndex = i; return; }
            }
        }
        if (selectedIndex < 0 || selectedIndex >= tasks.size()) selectedIndex = 0;
    }

    public List<AdminTaskViewModel> getTasks()         { return tasks; }
    public int getSelectedIndex()                      { return selectedIndex; }
    public void setSelectedIndex(int i)                { if (i >= 0 && i < tasks.size()) selectedIndex = i; }
    public AdminTaskViewModel getSelectedTask()        { return (selectedIndex >= 0 && selectedIndex < tasks.size()) ? tasks.get(selectedIndex) : null; }

    // ── Submissions ───────────────────────────────────────────────────────────

    public void replaceSubmissions(List<PushSubmissionsS2CPacket.SubmissionEntry> subs,
                                   String selectedId, List<String> log) {
        this.submissions = new ArrayList<>(subs);
        this.selectedSubmissionId = selectedId;
        this.selectedSubmissionLog = new ArrayList<>(log);

        // Sync selected index
        selectedSubmissionIndex = -1;
        for (int i = 0; i < submissions.size(); i++) {
            if (submissions.get(i).submissionId().equals(selectedId)) {
                selectedSubmissionIndex = i; break;
            }
        }
        if (selectedSubmissionIndex < 0 && !submissions.isEmpty()) selectedSubmissionIndex = 0;
    }

    public List<PushSubmissionsS2CPacket.SubmissionEntry> getSubmissions() { return submissions; }
    public String getSelectedSubmissionId()    { return selectedSubmissionId; }
    public List<String> getSelectedSubmissionLog() { return selectedSubmissionLog; }
    public int getSelectedSubmissionIndex()    { return selectedSubmissionIndex; }

    public void setSelectedSubmissionIndex(int i) {
        if (i >= 0 && i < submissions.size()) {
            selectedSubmissionIndex = i;
            selectedSubmissionId = submissions.get(i).submissionId();
        }
    }

    public PushSubmissionsS2CPacket.SubmissionEntry getSelectedSubmission() {
        return (selectedSubmissionIndex >= 0 && selectedSubmissionIndex < submissions.size())
                ? submissions.get(selectedSubmissionIndex) : null;
    }

    // ── ScreenHandler ─────────────────────────────────────────────────────────

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}