package net.shard.seconddawnrp.warpcore.adapter;

/**
 * Create mod power adapter — Phase 5+ stub.
 *
 * <p>Not yet implemented. Returns safe defaults.
 * Wire in when Create is confirmed in the modpack.
 */
public class CreatePowerAdapter implements PowerSourceAdapter {

    @Override
    public int getPowerOutput() {
        System.out.println("[SecondDawnRP] CreatePowerAdapter: not implemented — returning 0");
        return 0;
    }

    @Override
    public int getFuelLevelPercent() { return 0; }

    @Override
    public int getStability() { return 0; }

    @Override
    public String getPrimaryFuelLabel() { return "Rotational Force"; }
}