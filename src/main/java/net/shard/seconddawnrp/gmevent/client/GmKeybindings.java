package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class GmKeybindings {

    public static KeyBinding GM_SPAWN;
    public static KeyBinding GM_DESPAWN;

    public static void register() {
        GM_SPAWN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seconddawnrp.gm_spawn",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.seconddawnrp.gm"
        ));

        GM_DESPAWN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seconddawnrp.gm_despawn",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.seconddawnrp.gm"
        ));
    }
}