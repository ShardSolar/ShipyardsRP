package net.shard.seconddawnrp.medical;

import net.minecraft.network.PacketByteBuf;

/**
 * A single treatment step sent to the Medical PADD client screen.
 *
 * Phase 8.1: timing fields added (windowState, secondsRemaining).
 * PADD does NOT show timers — windowState used only for step colour coding.
 * Tricorder handles the actual countdown display server-side.
 */
public record MedicalPadStepData(
        String stepKey,
        String label,
        String item,
        int quantity,
        boolean requiresSurgery,
        boolean completed,
        // Timing state for colour coding on PADD
        TimingWindowState windowState
) {
    public enum TimingWindowState {
        NONE,        // No timing constraint
        WAITING,     // Min not yet reached — too early
        OPEN,        // Within valid window
        EXPIRED      // Max exceeded — will fail if attempted
    }

    public void encode(PacketByteBuf buf) {
        buf.writeString(stepKey);
        buf.writeString(label);
        buf.writeString(item);
        buf.writeInt(quantity);
        buf.writeBoolean(requiresSurgery);
        buf.writeBoolean(completed);
        buf.writeString(windowState.name());
    }

    public static MedicalPadStepData decode(PacketByteBuf buf) {
        return new MedicalPadStepData(
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                TimingWindowState.valueOf(buf.readString())
        );
    }
}