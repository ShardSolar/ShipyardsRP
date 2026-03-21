package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.registry.ModScreenHandlers;

import java.util.ArrayList;
import java.util.List;

public class AdminTaskScreenHandler extends ScreenHandler {

    private final List<AdminTaskViewModel> tasks = new ArrayList<>();
    private int selectedIndex = -1;

    public AdminTaskScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.ADMIN_TASK_SCREEN, syncId);

        reloadTasks();
        if (!tasks.isEmpty()) {
            selectedIndex = 0;
        }
    }

    public void reloadTasks() {
        tasks.clear();
        tasks.addAll(SecondDawnRP.TASK_SERVICE.buildAdminTaskViews());

        if (tasks.isEmpty()) {
            selectedIndex = -1;
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= tasks.size()) {
            selectedIndex = 0;
        }
    }

    public void replaceTasks(List<AdminTaskViewModel> newTasks) {
        String selectedTaskId = null;
        AdminTaskViewModel selected = getSelectedTask();
        if (selected != null) {
            selectedTaskId = selected.getTaskId();
        }

        tasks.clear();
        tasks.addAll(newTasks);

        if (tasks.isEmpty()) {
            selectedIndex = -1;
            return;
        }

        if (selectedTaskId != null) {
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).getTaskId().equals(selectedTaskId)) {
                    selectedIndex = i;
                    return;
                }
            }
        }

        if (selectedIndex < 0 || selectedIndex >= tasks.size()) {
            selectedIndex = 0;
        }
    }

    public List<AdminTaskViewModel> getTasks() {
        return tasks;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < tasks.size()) {
            this.selectedIndex = selectedIndex;
        }
    }

    public AdminTaskViewModel getSelectedTask() {
        if (selectedIndex < 0 || selectedIndex >= tasks.size()) {
            return null;
        }
        return tasks.get(selectedIndex);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}