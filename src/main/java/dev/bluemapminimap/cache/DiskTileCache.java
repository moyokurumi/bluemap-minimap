package dev.bluemapminimap.cache;

import dev.bluemapminimap.model.ResourceValidators;
import dev.bluemapminimap.model.TileAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class DiskTileCache {
    private static final int MAX_TILES_PER_LAYER = 256;
    private final Path root;

    public DiskTileCache(Path root) {
        this.root = root;
    }

    public Optional<CacheEntry> read(URI base, TileAddress tile) throws IOException {
        Path image = imagePath(base, tile);
        if (!Files.isRegularFile(image)) return Optional.empty();
        byte[] bytes = Files.readAllBytes(image);
        Properties metadata = new Properties();
        Path meta = metadataPath(image);
        if (Files.isRegularFile(meta)) {
            try (InputStream input = Files.newInputStream(meta)) {
                metadata.load(input);
            }
        }
        return Optional.of(new CacheEntry(bytes, new ResourceValidators(
                metadata.getProperty("etag"),
                metadata.getProperty("lastModified")
        )));
    }

    public void write(URI base, TileAddress tile, byte[] bytes, ResourceValidators validators) throws IOException {
        Path image = imagePath(base, tile);
        Files.createDirectories(image.getParent());
        Path imageTemp = image.resolveSibling(image.getFileName() + ".tmp");
        Files.write(imageTemp, bytes);
        replace(imageTemp, image);

        Properties metadata = new Properties();
        if (validators.etag() != null) metadata.setProperty("etag", validators.etag());
        if (validators.lastModified() != null) metadata.setProperty("lastModified", validators.lastModified());
        Path meta = metadataPath(image);
        Path metaTemp = meta.resolveSibling(meta.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(metaTemp)) {
            metadata.store(output, "BlueMap Minimap cache validators");
        }
        replace(metaTemp, meta);
        pruneLayer(image.getParent());
    }

    public static String originKey(URI base) {
        String origin = base.getScheme() + "://" + base.getHost() + ":" + effectivePort(base) + base.getPath();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(origin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Path imagePath(URI base, TileAddress tile) {
        String safeMap = tile.mapId().replaceAll("[^a-zA-Z0-9._-]", "_");
        return root.resolve(originKey(base)).resolve(safeMap).resolve("lod" + tile.lod())
                .resolve("x" + tile.x() + "z" + tile.z() + ".png");
    }

    private static Path metadataPath(Path image) {
        return image.resolveSibling(image.getFileName() + ".properties");
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void pruneLayer(Path layer) throws IOException {
        List<Path> images;
        try (Stream<Path> files = Files.list(layer)) {
            images = files.filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparingLong(DiskTileCache::lastModified))
                    .toList();
        }
        int remove = Math.max(0, images.size() - MAX_TILES_PER_LAYER);
        for (int i = 0; i < remove; i++) {
            Path image = images.get(i);
            Files.deleteIfExists(image);
            Files.deleteIfExists(metadataPath(image));
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }
}
