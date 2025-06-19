package net.minestom.server.instance.palette;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

import static net.minestom.server.network.NetworkBuffer.*;

/**
 * Represents a palette used to store blocks and biomes.
 * <p>
 * 0 is the default value.
 */
public interface Palette {
    static Palette blocks() {
        return newPalette(16, 8, 4, Block.staticRegistry().size());
    }

    static Palette biomes() {
        return newPalette(4, 3, 1, MinecraftServer.getBiomeRegistry().size());
    }

    static Palette newPalette(int dimension, int maxBitsPerEntry, int minBitsPerEntry, int registrySize) {
        final int directBitsPerEntry = MathUtils.bitsToRepresent(registrySize - 1);
        return new AdaptivePalette((byte) dimension, (byte) directBitsPerEntry, (byte) maxBitsPerEntry, (byte) minBitsPerEntry);
    }

    int get(int x, int y, int z);

    void getAll(@NotNull EntryConsumer consumer);

    void getAllPresent(@NotNull EntryConsumer consumer);

    void set(int x, int y, int z, int value);

    void fill(int value);

    void setAll(@NotNull EntrySupplier supplier);

    void replace(int x, int y, int z, @NotNull IntUnaryOperator operator);

    void replaceAll(@NotNull EntryFunction function);

    /**
     * Returns the number of entries in this palette.
     */
    int count();

    /**
     * Returns the number of bits used per entry.
     */
    int bitsPerEntry();

    int maxBitsPerEntry();

    int directBitsPerEntry();

    int dimension();

    /**
     * Returns the maximum number of entries in this palette.
     */
    default int maxSize() {
        final int dimension = dimension();
        return dimension * dimension * dimension;
    }

    @NotNull Palette clone();

    @FunctionalInterface
    interface EntrySupplier {
        int get(int x, int y, int z);
    }

    @FunctionalInterface
    interface EntryConsumer {
        void accept(int x, int y, int z, int value);
    }

    @FunctionalInterface
    interface EntryFunction {
        int apply(int x, int y, int z, int value);
    }

    NetworkBuffer.Type<Palette> BLOCK_SERIALIZER = serializer((byte) 16, (byte) 8, (byte) 4,
            () -> MathUtils.bitsToRepresent(Block.staticRegistry().size()));
    NetworkBuffer.Type<Palette> BIOME_SERIALIZER = serializer((byte) 4, (byte) 3, (byte) 1,
            () -> MathUtils.bitsToRepresent(MinecraftServer.getBiomeRegistry().size()));

    static NetworkBuffer.Type<Palette> serializer(byte dimension, byte maxIndirect, byte minIndirect,
                                                  IntSupplier directBitsPerEntrySupplier) {
        return new NetworkBuffer.Type<>() {
            @Override
            public void write(@NotNull NetworkBuffer buffer, Palette value) {
                switch (value) {
                    case AdaptivePalette adaptive -> {
                        final SpecializedPalette optimized = adaptive.optimizedPalette();
                        adaptive.palette = optimized;
                        BLOCK_SERIALIZER.write(buffer, optimized);
                    }
                    case PaletteSingle single -> {
                        buffer.write(BYTE, (byte) 0);
                        buffer.write(VAR_INT, single.value());
                    }
                    case PaletteIndirect indirect -> {
                        buffer.write(BYTE, (byte) value.bitsPerEntry());
                        if (indirect.bitsPerEntry() <= indirect.maxBitsPerEntry()) { // Palette index
                            buffer.write(VAR_INT.list(), indirect.paletteToValueList);
                        }
                        for (long l : indirect.values) {
                            buffer.write(LONG, l);
                        }
                    }
                    default -> throw new UnsupportedOperationException("Unsupported palette type: " + value.getClass());
                }
            }

            @Override
            public Palette read(@NotNull NetworkBuffer buffer) {
                final byte bitsPerEntry = buffer.read(BYTE);
                if (bitsPerEntry == 0) {
                    // Single valued 0-0
                    final int value = buffer.read(VAR_INT);
                    return new PaletteSingle(dimension, value);
                } else if (bitsPerEntry >= minIndirect && bitsPerEntry <= maxIndirect) {
                    // Indirect palette
                    final int[] palette = buffer.read(VAR_INT_ARRAY);
                    int entriesPerLong = 64 / bitsPerEntry;
                    final long[] data = new long[(dimension * dimension * dimension) / entriesPerLong + 1];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = buffer.read(LONG);
                    }
                    final PaletteIndirect result = new PaletteIndirect(dimension, (byte) directBitsPerEntrySupplier.getAsInt(),
                            maxIndirect, bitsPerEntry, 0, palette, data);
                    result.recount();
                    return result;
                } else {
                    // Direct palette
                    final long[] data = buffer.read(LONG_ARRAY);
                    final byte directBitsPerEntry = (byte) directBitsPerEntrySupplier.getAsInt();
                    Check.argCondition(directBitsPerEntry != bitsPerEntry,
                            "direct palette has to have registry sized bit count");
                    final PaletteIndirect result = new PaletteIndirect(dimension, directBitsPerEntry,
                            maxIndirect, bitsPerEntry, 0, new int[0], data);
                    result.recount();
                    return result;
                }
            }
        };
    }
}
