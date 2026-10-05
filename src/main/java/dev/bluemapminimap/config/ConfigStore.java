package dev.bluemapminimap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public MinimapConfig load() throws IOException {
        if (!Files.exists(file)) {
            MinimapConfig defaults = new MinimapConfig();
            save(defaults);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MinimapConfig config = GSON.fromJson(reader, MinimapConfig.class);
            if (config == null) config = new MinimapConfig();
            config.sanitize();
            return config;
        } catch (JsonParseException ex) {
            throw new IOException("Invalid BlueMap Minimap configuration", ex);
        }
    }

    public void save(MinimapConfig config) throws IOException {
        config.sanitize();
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        // Preserve the first pre-upgrade configuration, including malformed JSON.
        // Never replace a backup left by an earlier save or attempt a destructive migration.
        Path backup = file.resolveSibling(file.getFileName() + ".pre-rc4.bak");
        if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
        moveAtomically(temporary, file);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
