package net.minestom.server.instance.palette;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.function.IntUnaryOperator;

/**
 * Represents a palette used to store blocks and biomes.
 * <p>
 * 0 is the default value.
 */
public sealed interface Palette permits PaletteImpl {
    int BLOCK_DIMENSION = 16;
    int BLOCK_PALETTE_MIN_BITS = 4;
    int BLOCK_PALETTE_MAX_BITS = 8;
    int BLOCK_PALETTE_REGISTRY_SIZE = Block.statesCount();

    int BIOME_DIMENSION = 4;
    int BIOME_PALETTE_MIN_BITS = 1;
    int BIOME_PALETTE_MAX_BITS = 3;
    int DEFAULT_BIOME_REGISTRY_SIZE = 128;

    @ApiStatus.Internal
    static int biomeRegistrySize() {
        if (MinecraftServer.process() == null) return DEFAULT_BIOME_REGISTRY_SIZE;
        return MinecraftServer.getBiomeRegistry().size();
    }

    static Palette blocks(int bitsPerEntry) {
        return sized(BLOCK_DIMENSION, BLOCK_PALETTE_MIN_BITS, BLOCK_PALETTE_MAX_BITS, BLOCK_PALETTE_REGISTRY_SIZE, bitsPerEntry);
    }

    static Palette biomes(int bitsPerEntry) {
        return sized(BIOME_DIMENSION, BIOME_PALETTE_MIN_BITS, BIOME_PALETTE_MAX_BITS, biomeRegistrySize(), bitsPerEntry);
    }

    static Palette blocks() {
        return empty(BLOCK_DIMENSION, BLOCK_PALETTE_MIN_BITS, BLOCK_PALETTE_MAX_BITS, BLOCK_PALETTE_REGISTRY_SIZE);
    }

    static Palette biomes() {
        return empty(BIOME_DIMENSION, BIOME_PALETTE_MIN_BITS, BIOME_PALETTE_MAX_BITS, biomeRegistrySize());
    }

    static Palette empty(int dimension, int minBitsPerEntry, int maxBitsPerEntry, int registrySize) {
        return new PaletteImpl((byte) dimension, (byte) minBitsPerEntry, (byte) maxBitsPerEntry, registrySize - 1);
    }

    static Palette sized(int dimension, int minBitsPerEntry, int maxBitsPerEntry, int registrySize, int bitsPerEntry) {
        return new PaletteImpl((byte) dimension, (byte) minBitsPerEntry, (byte) maxBitsPerEntry, registrySize - 1, (byte) bitsPerEntry);
    }

    int get(int x, int y, int z);

    void getAll(@NotNull EntryConsumer consumer);

    void getAllPresent(@NotNull EntryConsumer consumer);

    int height(int x, int z, @NotNull EntryPredicate predicate);

    void set(int x, int y, int z, int value);

    void fill(int value);

    /**
     * Efficiently fills a cube within the section in the range [min, max) for all dimensions.
     * <p>
     * All coordinates are clamped to the palette boundaries and
     * nothing is filled if any min coordinate is greater than or equal to its max coordinate.
     *
     * @param value the value to fill with
     */
    void fill(int value, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    void load(int[] palette, long[] values);

    void replace(int oldValue, int newValue);

    void setAll(@NotNull EntrySupplier supplier);

    void replace(int x, int y, int z, @NotNull IntUnaryOperator operator);

    void replaceAll(@NotNull EntryFunction function);

    /**
     * Efficiently copies values from another palette with the given offset.
     * <p>
     * Both palettes must have the same dimension.
     *
     * @param source  the source palette to copy from
     * @param offsetX the X offset to apply when copying
     * @param offsetY the Y offset to apply when copying
     * @param offsetZ the Z offset to apply when copying
     */
    void copyFrom(@NotNull Palette source, int offsetX, int offsetY, int offsetZ);

    /**
     * Efficiently copies values from another palette starting at position (0, 0, 0).
     * <p>
     * Both palettes must have the same dimension.
     * <p>
     * This is a convenience method equivalent to calling {@code copyFrom(source, 0, 0, 0)}.
     *
     * @param source the source palette to copy from
     */
    void copyFrom(@NotNull Palette source);

    /**
     * Efficiently copies present values from another palette with the given offset.
     * All values are offset by the given offset.
     * <p>
     * Both palettes must have the same dimension.
     *
     * @param source  the source palette to copy from
     * @param offsetX the X offset to apply when copying
     * @param offsetY the Y offset to apply when copying
     * @param offsetZ the Z offset to apply when copying
     * @param valueOffset the offset to apply to values when copying
     */
    void copyPresentFrom(@NotNull Palette source, int offsetX, int offsetY, int offsetZ, int valueOffset);

    /**
     * Efficiently copies present values from another palette starting at position (0, 0, 0).
     * All values are offset by the given offset.
     * <p>
     * Both palettes must have the same dimension.
     * <p>
     * This is a convenience method equivalent to calling {@code copyPresentFrom(source, 0, 0, 0)}.
     *
     * @param source the source palette to copy from
     * @param valueOffset the offset to apply to values when copying
     */
    void copyPresentFrom(@NotNull Palette source, int valueOffset);

    /**
     * Returns the number of entries in this palette.
     */
    int count();

    /**
     * Returns the number of entries in this palette that match the given value.
     *
     * @param value the value to count
     * @return the number of entries matching the value
     */
    int count(int value);

    default boolean isEmpty() {
        return count() == 0;
    }

    /**
     * Checks if the palette contains the given value.
     *
     * @param value the value to check
     * @return true if the palette contains the value, false otherwise
     */
    boolean any(int value);

    /**
     * Returns the number of bits used per entry.
     */
    int bitsPerEntry();

    int dimension();

    /**
     * Returns the maximum number of entries in this palette.
     */
    default int maxSize() {
        final int dimension = dimension();
        return dimension * dimension * dimension;
    }

    void optimize(Optimization focus);

    enum Optimization {
        SIZE,
        SPEED,
    }

    /**
     * Compare palettes content independently of their storage format.
     *
     * @param palette the palette to compare with
     * @return true if the palettes are equivalent, false otherwise
     */
    boolean compare(@NotNull Palette palette);

    @NotNull Palette clone();

    @ApiStatus.Internal
    int paletteIndexToValue(int value);

    @ApiStatus.Internal
    int valueToPaletteIndex(int value);

    /**
     * Gets fill value of this palette, if it is guaranteed to be filled with that value. Otherwise, returns -1.
     * If this returns -1, {@link Palette#bitsPerEntry()} != 0.
     */
    @ApiStatus.Internal
    int singleValue();

    /**
     * If {@link Palette#bitsPerEntry()} == 0: returns null,
     * else returns the value array.<p>
     * Should not be modified.
     */
    @ApiStatus.Internal
    long @UnknownNullability [] indexedValues();

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

    @FunctionalInterface
    interface EntryPredicate {
        boolean get(int x, int y, int z, int value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    NetworkBuffer.Type<Palette> BLOCK_SERIALIZER = (NetworkBuffer.Type) new PaletteImpl.PaletteSerializer(
            (byte) BLOCK_DIMENSION, (byte) BLOCK_PALETTE_MIN_BITS, (byte) BLOCK_PALETTE_MAX_BITS,
            (byte) MathUtils.bitsToRepresent(BLOCK_PALETTE_REGISTRY_SIZE - 1), BLOCK_PALETTE_REGISTRY_SIZE - 1);

    @SuppressWarnings({"unchecked", "rawtypes"})
    static NetworkBuffer.Type<Palette> biomeSerializer() {
        // Use cached
        final var cached = PaletteImpl.PaletteSerializer.CACHED_BIOME_SERIALIZER;
        final int maxValue = biomeRegistrySize() - 1;
        final int directBits = MathUtils.bitsToRepresent(maxValue);
        if (cached != null && cached.directBits() == directBits) return (NetworkBuffer.Type) cached;

        final var newCached = new PaletteImpl.PaletteSerializer(
                (byte) BIOME_DIMENSION, (byte) BIOME_PALETTE_MIN_BITS,
                (byte) BIOME_PALETTE_MAX_BITS, (byte) directBits, maxValue);
        PaletteImpl.PaletteSerializer.CACHED_BIOME_SERIALIZER = newCached;
        return (NetworkBuffer.Type) newCached;
    }
}
