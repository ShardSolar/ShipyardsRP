package net.shard.seconddawnrp.warpcore.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.shard.seconddawnrp.warpcore.data.WarpCoreConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves {@link WarpCoreConfig} from
 * {@code config/assets/seconddawnrp/warpcore_config.json}.
 * Writes defaults if the file does not exist.
 */
public class WarpCoreConfigRepository {

    private static final String FILE_NAME = "warpcore_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public WarpCoreConfigRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            save(WarpCoreConfig.defaults());
        }
    }

    public WarpCoreConfig load() {
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj == null) return WarpCoreConfig.defaults();
            return new WarpCoreConfig(
                    getInt(obj, "startupDurationTicks", 200),
                    getInt(obj, "shutdownDurationTicks", 100),
                    getLong(obj, "fuelDrainIntervalMs", 5 * 60 * 1000L),
                    getInt(obj, "fuelDrainPerTickBase", 1),
                    getInt(obj, "fuelDrainOutputScale", 2),
                    getInt(obj, "fuelWarningThreshold", 20),
                    getInt(obj, "fuelCriticalThreshold", 5),
                    getInt(obj, "coilInstabilityThreshold", 35),
                    getInt(obj, "coilStartupMinimumHealth", 20),
                    getInt(obj, "powerOutputNominal", 100),
                    getInt(obj, "powerOutputUnstable", 60),
                    getInt(obj, "powerOutputCritical", 25),
                    getInt(obj, "powerOutputOffline", 0),
                    getDouble(obj, "criticalDegradationMultiplier", 2.0),
                    getLong(obj, "faultTaskCooldownMs", 30 * 60 * 1000L),
                    getInt(obj, "maxFuelRods", 64)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + FILE_NAME, e);
        }
    }

    public void save(WarpCoreConfig c) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("startupDurationTicks", c.getStartupDurationTicks());
        obj.addProperty("shutdownDurationTicks", c.getShutdownDurationTicks());
        obj.addProperty("fuelDrainIntervalMs", c.getFuelDrainIntervalMs());
        obj.addProperty("fuelDrainPerTickBase", c.getFuelDrainPerTickBase());
        obj.addProperty("fuelDrainOutputScale", c.getFuelDrainOutputScale());
        obj.addProperty("fuelWarningThreshold", c.getFuelWarningThreshold());
        obj.addProperty("fuelCriticalThreshold", c.getFuelCriticalThreshold());
        obj.addProperty("coilInstabilityThreshold", c.getCoilInstabilityThreshold());
        obj.addProperty("coilStartupMinimumHealth", c.getCoilStartupMinimumHealth());
        obj.addProperty("powerOutputNominal", c.getPowerOutputNominal());
        obj.addProperty("powerOutputUnstable", c.getPowerOutputUnstable());
        obj.addProperty("powerOutputCritical", c.getPowerOutputCritical());
        obj.addProperty("powerOutputOffline", c.getPowerOutputOffline());
        obj.addProperty("criticalDegradationMultiplier", c.getCriticalDegradationMultiplier());
        obj.addProperty("faultTaskCooldownMs", c.getFaultTaskCooldownMs());
        obj.addProperty("maxFuelRods", c.getMaxFuelRods());
        try (Writer w = Files.newBufferedWriter(filePath)) {
            GSON.toJson(obj, w);
        }
    }

    private static int getInt(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    private static long getLong(JsonObject o, String k, long d) { return o.has(k) ? o.get(k).getAsLong() : d; }
    private static double getDouble(JsonObject o, String k, double d) { return o.has(k) ? o.get(k).getAsDouble() : d; }
}