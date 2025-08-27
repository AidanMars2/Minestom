package net.minestom.server.instance.palette;

import it.unimi.dsi.fastutil.ints.*;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static net.minestom.server.coordinate.CoordConversion.SECTION_BLOCK_COUNT;
import static net.minestom.server.instance.palette.Palettes.*;
import static net.minestom.server.network.NetworkBuffer.*;

final class PaletteImpl implements Palette {
    private static final ThreadLocal<int[]> WRITE_CACHE = ThreadLocal.withInitial(() -> new int[SECTION_BLOCK_COUNT]);
    final byte dimension, minBitsPerEntry, maxBitsPerEntry;
    byte directBits;
    int maxValue;

    byte bitsPerEntry = 0;
    int count = 0; // Serve as the single value if bitsPerEntry == 0

    long[] values;
    // palette index = value
    IntArrayList paletteToValueList;
    // value = palette index
    Int2IntOpenHashMap valueToPaletteMap;

    PaletteImpl(byte dimension, byte minBitsPerEntry, byte maxBitsPerEntry, int maxValue) {
        validateDimension(dimension);
        this.dimension = dimension;
        this.minBitsPerEntry = minBitsPerEntry;
        this.maxBitsPerEntry = maxBitsPerEntry;
        this.directBits = (byte) MathUtils.bitsToRepresent(maxValue);
        this.maxValue = maxValue;
        validateBitsPerEntry(minBitsPerEntry, maxBitsPerEntry, directBits);
    }

    PaletteImpl(byte dimension, byte minBitsPerEntry, byte maxBitsPerEntry, int maxValue, byte bitsPerEntry) {
        this(dimension, minBitsPerEntry, maxBitsPerEntry, maxValue);
        if (bitsPerEntry != 0
                && (bitsPerEntry < minBitsPerEntry || bitsPerEntry > maxBitsPerEntry)
                && bitsPerEntry != this.directBits) {
            throw new IllegalArgumentException("Bits per entry must be in range [" + minBitsPerEntry +
                    ", " + maxBitsPerEntry + "] or equal to " + directBits + ". Got " + bitsPerEntry);
        }

        this.bitsPerEntry = bitsPerEntry;
        if (bitsPerEntry != 0) {
            this.values = new long[arrayLength(dimension, bitsPerEntry)];

            if (hasPalette()) {
                this.paletteToValueList = new IntArrayList();
                this.valueToPaletteMap = new Int2IntOpenHashMap();
                this.valueToPaletteMap.defaultReturnValue(-1);
                this.paletteToValueList.add(0);
                this.valueToPaletteMap.put(0, 0);
            }
        }
    }

    @Override
    public int get(int x, int y, int z) {
        validateCoord(dimension, x, y, z);
        if (bitsPerEntry == 0) return count;
        final int value = read(dimension(), bitsPerEntry, values, x, y, z);
        return paletteIndexToValue(value);
    }

    @Override
    public void getAll(@NotNull EntryConsumer consumer) {
        if (bitsPerEntry == 0) {
            Palettes.getAllFill(dimension, count, consumer);
        } else {
            retrieveAll(consumer, true);
        }
    }

    @Override
    public void getAllPresent(@NotNull EntryConsumer consumer) {
        if (bitsPerEntry == 0) {
            if (count != 0) Palettes.getAllFill(dimension, count, consumer);
        } else {
            retrieveAll(consumer, false);
        }
    }

    @Override
    public int height(int x, int z, @NotNull EntryPredicate predicate) {
        validateCoord(dimension, x, 0, z);
        final int dimension = this.dimension;
        final int startY = dimension - 1;
        if (bitsPerEntry == 0) return predicate.get(x, startY, z, count) ? startY : -1;
        final long[] values = this.values;
        final int bitsPerEntry = this.bitsPerEntry;
        final int valuesPerLong = 64 / bitsPerEntry;
        final int mask = (1 << bitsPerEntry) - 1;
        final int[] paletteIds = hasPalette() ? paletteToValueList.elements() : null;
        for (int y = startY; y >= 0; y--) {
            final int index = sectionIndex(dimension, x, y, z);
            final int longIndex = index / valuesPerLong;
            final int bitIndex = (index % valuesPerLong) * bitsPerEntry;
            final int paletteIndex = (int) (values[longIndex] >> bitIndex) & mask;
            final int value = paletteIds != null && paletteIndex < paletteIds.length ? paletteIds[paletteIndex]
                    : paletteIndex;
            if (predicate.get(x, y, z, value)) return y;
        }
        return -1;
    }

    @Override
    public void set(int x, int y, int z, int value) {
        validateCoord(dimension, x, y, z);
        final int paletteIndex = valueToPaletteIndex(value);
        final int oldValue = Palettes.write(dimension(), bitsPerEntry, values, x, y, z, paletteIndex);
        // Check if block count needs to be updated
        final boolean currentAir = paletteIndexToValue(oldValue) == 0;
        if (currentAir != (value == 0)) this.count += currentAir ? 1 : -1;
    }

    @Override
    public void fill(int value) {
        validateValue(value, false);
        this.bitsPerEntry = 0;
        this.count = value;
        this.values = null;
        this.paletteToValueList = null;
        this.valueToPaletteMap = null;
    }

    @Override
    public void fill(int value, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Early exit if nothing to copy (all values are equal already)
        if (value == this.count && (this.bitsPerEntry == 0 || this.count == 0)) return;
        final int dimension = this.dimension;
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        minZ = Math.max(0, minZ);
        maxX = Math.min(dimension, maxX);
        maxY = Math.min(dimension, maxY);
        maxZ = Math.min(dimension, maxZ);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return;
        if (minX == 0 && minY == 0 && minZ == 0 &&
                maxX == dimension && maxY == dimension && maxZ == dimension) {
            fill(value);
            return;
        }

        final int paletteIndex = valueToPaletteIndex(value);
        final int dimensionBits = MathUtils.bitsToRepresent(dimension - 1);
        final int initialZTravel = minZ << dimensionBits;
        final int finalZTravel = (dimension - maxZ) << dimensionBits;
        final int finalXTravel = dimension - minX;

        final int airValue = valueToPalettIndexOrDefault(0);
        final boolean isAir = value == 0;
        final int bpe = bitsPerEntry;
        final int valuesPerLong = 64 / bpe;
        final int maxBitIndex = bpe * valuesPerLong;
        final long mask = (1L << bpe) - 1;

        int index = minY << (dimensionBits << 1);
        int countDelta = 0;
        for (int y = minY; y < maxY; y++) {
            index += initialZTravel;
            for (int z = minZ; z < maxZ; z++) {
                index += minX;
                int blockIndex = index / valuesPerLong;
                int bitIndex = (index - blockIndex * valuesPerLong) * bpe;
                long block = values[blockIndex];

                for (int x = minX; x < maxX; x++) {
                    final boolean wasAir = ((block >>> bitIndex) & mask) == airValue;
                    if (wasAir != isAir) countDelta += wasAir ? 1 : -1;
                    block = (block & ~(mask << bitIndex)) | ((long) paletteIndex << bitIndex);
                    bitIndex += bpe;

                    if (bitIndex >= maxBitIndex) {
                        values[blockIndex++] = block;
                        if (blockIndex >= values.length) {
                            this.count += countDelta;
                            return;
                        }
                        block = values[blockIndex];
                        bitIndex = 0;
                    }
                }
                values[blockIndex] = block;
                index += finalXTravel;
            }
            index += finalZTravel;
        }
        this.count += countDelta;
    }

    @Override
    public void load(int[] palette, long[] values) {
        int bpe = palette.length <= 1 ? 0 : MathUtils.bitsToRepresent(palette.length - 1);
        if (bpe == 0) {
            fill(palette[0]);
            return;
        }
        bpe = Math.max(minBitsPerEntry, bpe);
        int maxPaletteValue = 0;
        for (int value : palette) maxPaletteValue = Math.max(maxPaletteValue, value);
        validateValue(maxPaletteValue, false);

        if (bpe > maxBitsPerEntry) {
            // Direct mode: convert from palette indices to direct values
            this.paletteToValueList = null;
            this.valueToPaletteMap = null;
            this.bitsPerEntry = directBits;

            var count = new AtomicInteger();
            this.values = Palettes.remap(dimension, bpe, directBits, values, true, v -> {
                final int value = palette[v];
                if (value != 0) count.setPlain(count.getPlain() + 1);
                return value;
            });
            this.count = count.getPlain();
        } else {
            // Indirect mode: use palette
            this.bitsPerEntry = (byte) bpe;
            this.paletteToValueList = new IntArrayList(palette);
            this.valueToPaletteMap = new Int2IntOpenHashMap(palette.length);
            this.valueToPaletteMap.defaultReturnValue(-1);
            for (int i = 0; i < palette.length; i++) {
                this.valueToPaletteMap.put(palette[i], i);
            }
            this.values = Arrays.copyOf(values, arrayLength(dimension, bitsPerEntry));
            recount();
        }
    }

    @Override
    public void replace(int oldValue, int newValue) {
        if (oldValue == newValue) return;
        if (bitsPerEntry == 0) {
            if (oldValue == count) this.count = newValue;
            return;
        }
        final int oldIndex;
        final int newIndex;
        final boolean countUpdate = newValue == 0 || oldValue == 0;
        if (hasPalette()) {
            oldIndex = valueToPalettIndexOrDefault(oldValue);
            if (oldIndex == -1) return;
            newIndex = valueToPalettIndexOrDefault(newValue);

            if (newIndex == -1) {
                final int count = countUpdate ? count(oldValue) : -1;
                if (count == 0) return; // No blocks to replace
                paletteToValueList.set(oldIndex, newValue);
                valueToPaletteMap.remove(oldValue);
                valueToPaletteMap.put(newValue, oldIndex);
                // Update count
                if (countUpdate) this.count += oldValue == 0 ? count : -count;
                return;
            }
        } else {
            validateValue(newValue, true);
            newIndex = newValue;
            oldIndex = oldValue;
        }
        var count = new AtomicInteger();
        this.values = Palettes.remap(dimension, bitsPerEntry, bitsPerEntry, values, value -> {
            if (value == oldIndex) count.setPlain(count.getPlain() + 1);
            return value == oldIndex ? newIndex : value;
        });
        if (countUpdate) this.count += oldValue == 0 ? count.getPlain() : -count.getPlain();
    }

    @Override
    public void setAll(@NotNull EntrySupplier supplier) {
        int[] cache = WRITE_CACHE.get();
        final int dimension = dimension();
        // Fill cache with values
        int fillValue = -1;
        int count = 0;
        int index = 0;
        for (int y = 0; y < dimension; y++) {
            for (int z = 0; z < dimension; z++) {
                for (int x = 0; x < dimension; x++) {
                    int value = supplier.get(x, y, z);
                    validateValue(value, false);
                    // Support for fill fast exit if the supplier returns a constant value
                    if (fillValue != -2) {
                        if (fillValue == -1) {
                            fillValue = value;
                        } else if (fillValue != value) {
                            fillValue = -2;
                        }
                    }
                    // Set value in cache
                    if (value != 0) count++;
                    cache[index++] = value;
                }
            }
        }
        assert index == maxSize();
        // Update palette content
        if (fillValue < 0) {
            makeDirect();
            updateAll(cache);
            this.count = count;
        } else {
            fill(fillValue);
        }
    }

    @Override
    public void replace(int x, int y, int z, @NotNull IntUnaryOperator operator) {
        validateCoord(dimension, x, y, z);
        final int oldValue = get(x, y, z);
        final int newValue = operator.applyAsInt(oldValue);
        if (oldValue != newValue) set(x, y, z, newValue);
    }

    @Override
    public void replaceAll(@NotNull EntryFunction function) {
        int[] cache = WRITE_CACHE.get();
        AtomicInteger arrayIndex = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        getAll((x, y, z, value) -> {
            final int newValue = function.apply(x, y, z, value);
            validateValue(value, false);
            final int index = arrayIndex.getPlain();
            arrayIndex.setPlain(index + 1);
            cache[index] = newValue;
            if (newValue != 0) count.setPlain(count.getPlain() + 1);
        });
        assert arrayIndex.getPlain() == maxSize();
        // Update palette content
        makeDirect();
        updateAll(cache);
        this.count = count.getPlain();
    }

    @Override
    public void copyFrom(@NotNull Palette source, int offsetX, int offsetY, int offsetZ) {
        copyFrom(source, offsetX, offsetY, offsetZ, false, 0);
    }

    @Override
    public void copyFrom(@NotNull Palette source) {
        final PaletteImpl sourcePalette = (PaletteImpl) source;
        final int sourceDimension = sourcePalette.dimension();
        final int targetDimension = this.dimension();
        if (sourceDimension != targetDimension) {
            throw new IllegalArgumentException("Source palette dimension (" + sourceDimension +
                    ") must equal target palette dimension (" + targetDimension + ")");
        }

        if (sourcePalette.bitsPerEntry == 0) {
            fill(sourcePalette.count);
            return;
        }
        if (sourcePalette.count == 0) {
            fill(0);
            return;
        }

        // Copy
        this.bitsPerEntry = sourcePalette.bitsPerEntry;
        this.count = sourcePalette.count;
        this.directBits = sourcePalette.directBits;
        this.maxValue = sourcePalette.maxValue;

        if (sourcePalette.values != null) {
            this.values = sourcePalette.values.clone();
        } else {
            this.values = null;
        }

        if (sourcePalette.paletteToValueList != null) {
            this.paletteToValueList = new IntArrayList(sourcePalette.paletteToValueList);
        } else {
            this.paletteToValueList = null;
        }

        if (sourcePalette.valueToPaletteMap != null) {
            this.valueToPaletteMap = new Int2IntOpenHashMap(sourcePalette.valueToPaletteMap);
            this.valueToPaletteMap.defaultReturnValue(-1);
        } else {
            this.valueToPaletteMap = null;
        }
    }

    @Override
    public void copyPresentFrom(@NotNull Palette source, int offsetX, int offsetY, int offsetZ, int valueOffset) {
        copyFrom(source, offsetX, offsetY, offsetZ, true, valueOffset);
    }

    @Override
    public void copyPresentFrom(@NotNull Palette source, int valueOffset) {
        copyFrom(source, 0 ,0 ,0, true, valueOffset);
    }

    void copyFrom(@NotNull Palette source, int offsetX, int offsetY, int offsetZ,
                  boolean ignoreEmpty, int valueOffset) {
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0 && !ignoreEmpty) {
            copyFrom(source);
            return;
        }

        final PaletteImpl sourcePalette = (PaletteImpl) source;
        final int sourceDimension = sourcePalette.dimension;
        final int dimension = this.dimension;
        if (sourceDimension != dimension) {
            throw new IllegalArgumentException("Source palette dimension (" + sourceDimension +
                    ") must equal target palette dimension (" + dimension + ")");
        }

        // Early exit if nothing to copy (offset pushes everything out of bounds)
        if (Math.abs(offsetX) >= dimension || Math.abs(offsetY) >= dimension || Math.abs(offsetZ) >= dimension) return;

        int sourceCount = sourcePalette.count;
        if (sourceCount == 0 && ignoreEmpty) return;
        // Fast Path: partially fill if all values are equal
        if (sourcePalette.bitsPerEntry == 0 || sourceCount == 0) {
            sourceCount += valueOffset;
            fill(sourceCount, offsetX, offsetY, offsetZ,
                    dimension + offsetX, dimension + offsetY, dimension + offsetZ);
            return;
        }

        // General case: copy each value individually
        if (bitsPerEntry == 0) initIndirect();
        this.count += Palettes.partialOffsetCopy(sourcePalette, this,
                offsetX, offsetY, offsetZ, ignoreEmpty, valueOffset);
    }

    @Override
    public int count() {
        if (bitsPerEntry == 0) {
            return count == 0 ? 0 : maxSize();
        } else {
            return count;
        }
    }

    @Override
    public int count(int value) {
        if (bitsPerEntry == 0) return count == value ? maxSize() : 0;
        if (value == 0) return maxSize() - count();
        final int queryValue = valueToPalettIndexOrDefault(value);
        return countPaletteIndex(queryValue);
    }

    void recount() {
        if (bitsPerEntry != 0) {
            this.count = maxSize() - countPaletteIndex(valueToPalettIndexOrDefault(0));
        }
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    int countPaletteIndex(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex > maxValue) return 0;
        int result = 0;
        final int size = maxSize();
        final int bits = bitsPerEntry;
        final int valuesPerLong = 64 / bits;
        final int mask = (1 << bits) - 1;
        for (int i = 0, idx = 0; i < values.length; i++) {
            long block = values[i];
            int end = Math.min(valuesPerLong, size - idx);
            for (int j = 0; j < end; j++, idx++) {
                if (((int) (block & mask)) == paletteIndex) result++;
                block >>>= bits;
            }
        }
        return result;
    }

    @Override
    public boolean any(int value) {
        if (value > maxValue) return false;
        if (bitsPerEntry == 0) return count == value;
        if (value == 0) return maxSize() != count;
        int queryValue = valueToPalettIndexOrDefault(value);
        if (queryValue == -1) return false;
        // Scan through the values
        final int size = maxSize();
        final int bits = bitsPerEntry;
        final int valuesPerLong = 64 / bits;
        final int mask = (1 << bits) - 1;
        for (int i = 0, idx = 0; i < values.length; i++) {
            long block = values[i];
            int end = Math.min(valuesPerLong, size - idx);
            for (int j = 0; j < end; j++, idx++) {
                if (((int) (block & mask)) == queryValue) return true;
                block >>>= bits;
            }
        }
        return false;
    }

    @Override
    public int bitsPerEntry() {
        return bitsPerEntry;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public void optimize(Optimization focus) {
        final int bitsPerEntry = this.bitsPerEntry;
        if (bitsPerEntry == 0) {
            // Already optimized (single value)
            return;
        }

        // Count unique values
        IntSet uniqueValues = new IntOpenHashSet();
        getAll((x, y, z, value) -> uniqueValues.add(value));
        final int uniqueCount = uniqueValues.size();

        // If only one unique value, use fill for maximum optimization
        if (uniqueCount == 1) {
            fill(uniqueValues.iterator().nextInt());
            return;
        }

        if (focus == Optimization.SPEED) {
            // Speed optimization - use direct storage
            makeDirect();
        } else if (focus == Optimization.SIZE) {
            // Size optimization - calculate minimum bits needed for unique values
            final var paletteList = new IntArrayList(uniqueCount);
            uniqueValues.forEach(paletteList::add);
            downsizeWithPalette(paletteList);
        }
    }

    @Override
    public boolean compare(@NotNull Palette p) {
        final PaletteImpl palette = (PaletteImpl) p;
        final int dimension = this.dimension();
        if (palette.dimension() != dimension) return false;
        if (palette.count != this.count) return false;
        if (palette.count == 0) return true;
        if (palette.bitsPerEntry == 0 && this.bitsPerEntry == 0) return true;
        for (int y = 0; y < dimension; y++) {
            for (int z = 0; z < dimension; z++) {
                for (int x = 0; x < dimension; x++) {
                    final int value1 = this.get(x, y, z);
                    final int value2 = palette.get(x, y, z);
                    if (value1 != value2) return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NotNull Palette clone() {
        PaletteImpl clone = new PaletteImpl(dimension, minBitsPerEntry, maxBitsPerEntry, maxValue);
        clone.bitsPerEntry = this.bitsPerEntry;
        clone.count = this.count;
        if (bitsPerEntry == 0) return clone;
        clone.values = values.clone();
        if (paletteToValueList != null) clone.paletteToValueList = paletteToValueList.clone();
        if (valueToPaletteMap != null) clone.valueToPaletteMap = valueToPaletteMap.clone();
        return clone;
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    private void retrieveAll(@NotNull EntryConsumer consumer, boolean consumeEmpty) {
        if (!consumeEmpty && count == 0) return;
        final long[] values = this.values;
        final int[] ids = hasPalette() ? paletteToValueList.elements() : null;
        final int dimensionMinus = dimension - 1;
        final int dimensionBitCount = MathUtils.bitsToRepresent(dimensionMinus);
        final int shiftedDimensionBitCount = dimensionBitCount << 1;

        final int size = maxSize();
        final int bits = bitsPerEntry;
        final int valuesPerLong = 64 / bits;
        final int mask = (1 << bits) - 1;
        for (int i = 0, idx = 0; i < values.length; i++) {
            long block = values[i];
            int end = Math.min(valuesPerLong, size - idx);
            for (int j = 0; j < end; j++, idx++) {
                final int paletteIndex = (int) (block & mask);
                final int result = ids != null ? ids[paletteIndex] : paletteIndex;
                if (consumeEmpty || result != 0) {
                    final int y = idx >> shiftedDimensionBitCount;
                    final int z = idx >> dimensionBitCount & dimensionMinus;
                    final int x = idx & dimensionMinus;
                    consumer.accept(x, y, z, result);
                }
                block >>>= bits;
            }
        }
    }

    private void updateAll(int[] paletteValues) {
        final int size = maxSize();
        assert paletteValues.length >= size;
        final long[] values = this.values;
        final int bits = bitsPerEntry;
        final int valuesPerLong = 64 / bits;
        for (int i = 0, idx = 0; i < values.length; i++) {
            long block = 0;
            int end = Math.min(valuesPerLong, size - idx);
            int bitIndex = 0;
            for (int j = 0; j < end; j++, idx++) {
                block |= (long) paletteValues[idx] << bitIndex;
                bitIndex += bits;
            }
            values[i] = block;
        }
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    private void downsizeWithPalette(IntArrayList palette) {
        final byte bpe = this.bitsPerEntry;
        final byte newBpe = (byte) Math.max(MathUtils.bitsToRepresent(palette.size() - 1), minBitsPerEntry);
        if (newBpe >= bpe || newBpe > maxBitsPerEntry) return;

        // Fill new palette <-> value objects
        final Int2IntOpenHashMap newValueToPaletteMap = new Int2IntOpenHashMap();
        newValueToPaletteMap.defaultReturnValue(-1);
        final AtomicInteger index = new AtomicInteger();
        palette.forEach(v -> {
            final int plainIndex = index.getPlain();
            newValueToPaletteMap.put(v, plainIndex);
            index.setPlain(plainIndex + 1);
        });

        if (!hasPalette()) {
            this.values = Palettes.remap(dimension, bpe, newBpe, values, newValueToPaletteMap::get);
        } else {
            final IntArrayList transformList = new IntArrayList(paletteToValueList.size());
            paletteToValueList.forEach(value -> transformList.add(newValueToPaletteMap.get(value)));
            final int[] transformArray = transformList.elements();
            this.values = Palettes.remap(dimension, bpe, newBpe, values, value -> transformArray[value]);
        }

        this.bitsPerEntry = newBpe;
        this.valueToPaletteMap = newValueToPaletteMap;
        this.paletteToValueList = palette;
    }

    void makeDirect() {
        if (!hasPalette()) return;
        if (bitsPerEntry == 0) {
            final int fillValue = this.count;
            this.values = new long[arrayLength(dimension, directBits)];
            if (fillValue != 0) {
                Palettes.fill(directBits, this.values, fillValue);
                this.count = maxSize();
            }
        } else {
            final int[] ids = paletteToValueList.elements();
            this.values = Palettes.remap(dimension, bitsPerEntry, directBits, values, v -> ids[v]);
        }
        this.paletteToValueList = null;
        this.valueToPaletteMap = null;
        this.bitsPerEntry = directBits;
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    void upsize() {
        final byte bpe = this.bitsPerEntry;
        byte newBpe = (byte) (bpe + 1);
        if (newBpe > maxBitsPerEntry) {
            makeDirect();
        } else {
            this.values = Palettes.remap(dimension, bpe, newBpe, values, Int2IntFunction.identity());
            this.bitsPerEntry = newBpe;
        }
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} == 0
    void initIndirect() {
        final int fillValue = this.count;
        this.valueToPaletteMap = new Int2IntOpenHashMap();
        this.valueToPaletteMap.defaultReturnValue(-1);
        this.paletteToValueList = new IntArrayList();
        this.valueToPaletteMap.put(fillValue, 0);
        paletteToValueList.add(fillValue);
        this.bitsPerEntry = minBitsPerEntry;
        this.values = new long[arrayLength(dimension, minBitsPerEntry)];
        this.count = fillValue == 0 ? 0 : maxSize();
    }

    @Override
    public int paletteIndexToValue(int value) {
        return hasPalette() ? paletteToValueList.elements()[value] : value;
    }

    @Override
    public int valueToPaletteIndex(int value) {
        validateValue(value, true);
        if (!hasPalette()) return value;
        if (values == null) initIndirect();

        final int lastPaletteIndex = this.paletteToValueList.size();
        final int lookup = valueToPaletteMap.putIfAbsent(value, lastPaletteIndex);
        if (lookup != -1) return lookup;
        if (lastPaletteIndex >= maxPaletteSize(bitsPerEntry)) {
            // Palette is full, must resize
            upsize();
            if (!hasPalette()) return value;
        }
        this.paletteToValueList.add(value);
        return lastPaletteIndex;
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    int valueToPalettIndexOrDefault(int value) {
        return hasPalette() ? valueToPaletteMap.get(value) : value;
    }

    void validateValue(int value, boolean allowResize) {
        if (value > maxValue) {
            this.maxValue = value;
            final byte newDirectBits = (byte) MathUtils.bitsToRepresent(value);
            if (allowResize && !hasPalette() && newDirectBits > bitsPerEntry) {
                this.values = Palettes.remap(dimension, bitsPerEntry, newDirectBits, values, Int2IntFunction.identity());
                this.bitsPerEntry = newDirectBits;
            }
            this.directBits = newDirectBits;
        }
    }

    @Override
    public int singleValue() {
        return bitsPerEntry == 0 || count == 0 ? count : -1;
    }

    @Override
    public long @Nullable [] indexedValues() {
        return values;
    }

    /// Returns true if palette is in indirect mode or single value mode
    boolean hasPalette() {
        return bitsPerEntry <= maxBitsPerEntry;
    }

    private static void validateBitsPerEntry(byte minBitsPerEntry, byte maxBitsPerEntry, byte directBits) {
        if (minBitsPerEntry <= 0) throw new IllegalArgumentException("Min bits per entry must be positive");
        if (maxBitsPerEntry <= minBitsPerEntry)
            throw new IllegalArgumentException("Max bits per entry must be greater than min bits per entry");
        if (directBits <= maxBitsPerEntry)
            throw new IllegalArgumentException("Direct bits per entry must be greater than max bits per entry");
    }

    private static void validateCoord(int dimension, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0)
            throw new IllegalArgumentException("Coordinates must be non-negative");
        if (x >= dimension || y >= dimension || z >= dimension)
            throw new IllegalArgumentException("Coordinates must be less than the dimension size, got " + x + ", " + y + ", " + z + " for dimension " + dimension);
    }

    private static void validateDimension(int dimension) {
        if (dimension <= 1 || (dimension & dimension - 1) != 0)
            throw new IllegalArgumentException("Dimension must be a positive power of 2, got " + dimension);
    }

    record PaletteSerializer(
            byte dimension,
            byte minIndirect,
            byte maxIndirect,
            byte directBits,
            int maxValue
    ) implements NetworkBuffer.Type<PaletteImpl> {
        @Override
        public void write(@NotNull NetworkBuffer buffer, PaletteImpl value) {
            if (directBits != value.directBits && !value.hasPalette()) {
                PaletteImpl tmp = new PaletteImpl(dimension, minIndirect, maxIndirect, maxValue);
                tmp.setAll(value::get);
                value = tmp;
            }
            final byte bitsPerEntry = value.bitsPerEntry;
            buffer.write(BYTE, bitsPerEntry);
            if (bitsPerEntry == 0) {
                buffer.write(VAR_INT, value.count);
            } else {
                if (value.hasPalette()) {
                    buffer.write(VAR_INT.list(), value.paletteToValueList);
                }
                for (long l : value.values) buffer.write(LONG, l);
            }
        }

        @Override
        public PaletteImpl read(@NotNull NetworkBuffer buffer) {
            final byte bitsPerEntry = buffer.read(BYTE);
            PaletteImpl result = new PaletteImpl(dimension, minIndirect, maxIndirect, maxValue);
            result.bitsPerEntry = bitsPerEntry;
            if (bitsPerEntry == 0) {
                // Single value palette
                result.count = buffer.read(VAR_INT);
                return result;
            }
            if (result.hasPalette()) {
                // Indirect palette
                final int[] palette = buffer.read(VAR_INT_ARRAY);
                result.paletteToValueList = new IntArrayList(palette);
                result.valueToPaletteMap = new Int2IntOpenHashMap(palette.length);
                for (int i = 0; i < palette.length; i++) {
                    result.valueToPaletteMap.put(palette[i], i);
                }
            }
            final long[] data = new long[Palettes.arrayLength(dimension, bitsPerEntry)];
            for (int i = 0; i < data.length; i++) data[i] = buffer.read(LONG);
            result.values = data;
            result.recount();
            return result;
        }
    }
}
