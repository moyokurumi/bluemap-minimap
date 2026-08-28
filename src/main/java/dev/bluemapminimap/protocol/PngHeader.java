package dev.bluemapminimap.protocol;

import java.io.IOException;

public final class PngHeader {
    private static final byte[] SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private PngHeader() {
    }

    public static Dimensions validateLowres(byte[] bytes, int tileSizeX, int tileSizeZ) throws IOException {
        Dimensions dimensions = dimensions(bytes);
        int expectedWidth = tileSizeX + 1;
        int expectedHeight = (tileSizeZ + 1) * 2;
        if (dimensions.width() != expectedWidth || dimensions.height() != expectedHeight) {
            throw new IOException("BlueMap tile dimensions are invalid");
        }
        return dimensions;
    }

    public static Dimensions dimensions(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 24) throw new IOException("PNG is truncated");
        for (int index = 0; index < SIGNATURE.length; index++) {
            if (bytes[index] != SIGNATURE[index]) throw new IOException("PNG signature is invalid");
        }
        if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') {
            throw new IOException("PNG IHDR is missing");
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width <= 0 || height <= 0) throw new IOException("PNG dimensions are invalid");
        return new Dimensions(width, height);
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24
                | (bytes[offset + 1] & 0xFF) << 16
                | (bytes[offset + 2] & 0xFF) << 8
                | bytes[offset + 3] & 0xFF;
    }

    public record Dimensions(int width, int height) {
    }
}
