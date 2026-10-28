package agro;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;


public final class GameConfig {
    public final Properties properties = new Properties();
    public final int sessionSeconds;
    public final int growthMillis;

    public GameConfig() {
        properties.setProperty("session.seconds", "60");
        properties.setProperty("growth.millis", "2700");
        properties.setProperty("weight.small", "60");
        properties.setProperty("weight.medium", "30");
        properties.setProperty("weight.large", "10");
        Path location = locateConfiguration();
        if (location != null && Files.isRegularFile(location)) {
            try (Reader reader = Files.newBufferedReader(location, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + location, e);
            }
        }
        sessionSeconds = positiveInt("session.seconds", 60, 1, 60);
        growthMillis = positiveInt("growth.millis", 2700, 250, 8000);
    }

    private Path locateConfiguration() {
        String explicit = System.getProperty("agro.config");
        if (explicit != null) return Path.of(explicit);
        Path cwd = Path.of("config", "game.properties");
        if (Files.isRegularFile(cwd)) return cwd;
        try {
            Path code = Path.of(GameConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path folder = Files.isRegularFile(code) ? code.getParent() : code;
            return folder.resolve("config").resolve("game.properties");
        } catch (URISyntaxException | SecurityException e) {
            return null;
        }
    }

    private int positiveInt(String key, int fallback, int min, int max) {
        int value;
        try { value = Integer.parseInt(properties.getProperty(key, "" + fallback).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Bad config: " + key, e); }
        if (value < min || value > max) throw new IllegalArgumentException("Out-of-range " + key + ": " + value);
        return value;
    }
}
