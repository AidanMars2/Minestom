package net.minestom.server.instance.palette;

import it.unimi.dsi.fastutil.ints.*;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;

import static net.minestom.server.coordinate.CoordConversion.SECTION_BLOCK_COUNT;
import static net.minestom.server.instance.palette.Palettes.*;

final class PaletteImpl implements Palette {
    private static final ThreadLocal<int[]> WRITE_CACHE = ThreadLocal.withInitial(() -> new int[SECTION_BLOCK_COUNT]);
    final byte dimension, minBitsPerEntry, maxBitsPerEntry;
    byte directBits;

    byte bitsPerEntry = 0;
    int count = 0; // Serve as the single value if bitsPerEntry == 0

    long[] values;
    // palette index = value
    IntArrayList paletteToValueList;
    // value = palette index
    Int2IntOpenHashMap valueToPaletteMap;

    PaletteImpl(byte dimension, byte minBitsPerEntry, byte maxBitsPerEntry, byte directBits) {
        validateDimension(dimension);
        this.dimension = dimension;
        this.minBitsPerEntry = minBitsPerEntry;
        this.maxBitsPerEntry = maxBitsPerEntry;
        this.directBits = directBits;
    }

    PaletteImpl(byte dimension, byte minBitsPerEntry, byte maxBitsPerEntry, byte directBits, byte bitsPerEntry) {
        this(dimension, minBitsPerEntry, maxBitsPerEntry, directBits);

        this.bitsPerEntry = bitsPerEntry;
        this.values = new long[arrayLength(dimension, bitsPerEntry)];

        if (hasPalette()) {
            this.paletteToValueList = new IntArrayList();
            this.valueToPaletteMap = new Int2IntOpenHashMap();
            this.paletteToValueList.add(0);
            this.valueToPaletteMap.put(0, 0);
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
        retrieveAll(consumer, true);
    }

    @Override
    public void getAllPresent(@NotNull EntryConsumer consumer) {
        retrieveAll(consumer, false);
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
        checkValue(value, false);
        this.bitsPerEntry = 0;
        this.count = value;
        this.values = null;
        this.paletteToValueList = null;
        this.valueToPaletteMap = null;
    }

    @Override
    public void load(int[] palette, long[] values) {
        int bpe = palette.length <= 1 ? 0 : MathUtils.bitsToRepresent(palette.length - 1);
        bpe = Math.max(minBitsPerEntry, bpe);
        validateValues(dimension, (byte) bpe, palette.length, values);
        for (int v : palette) checkValue(v, false);

        if (bpe > maxBitsPerEntry) {
            // Direct mode: convert from palette indices to direct values
            this.paletteToValueList = null;
            this.valueToPaletteMap = null;
            this.values = new long[arrayLength(dimension, directBits)];

            final AtomicInteger nonZeroCount = new AtomicInteger();
            this.values = Palettes.remap(this.dimension, bpe, directBits, values, true, (paletteIndex) -> {
                final int directValue = paletteIndex < palette.length ? palette[paletteIndex] : 0;
                if (directValue != 0) nonZeroCount.setPlain(nonZeroCount.getPlain() + 1);
                return directValue;
            });
            this.bitsPerEntry = directBits;
            this.count = nonZeroCount.getPlain();
        } else {
            // Indirect mode: use palette
            this.paletteToValueList = new IntArrayList(palette);
            this.valueToPaletteMap = new Int2IntOpenHashMap(palette.length);
            for (int i = 0; i < palette.length; i++) {
                this.valueToPaletteMap.put(palette[i], i);
            }
            this.values = Arrays.copyOf(values, arrayLength(dimension, bpe));
            this.bitsPerEntry = (byte) bpe;
            recount();
        }
    }

    @Override
    public void offset(int offset) {
        if (offset == 0) return;
        if (bitsPerEntry == 0) {
            this.count += offset;
        } else {
            replaceAll((x, y, z, value) -> value + offset);
        }
    }

    @Override
    public void replace(int oldValue, int newValue) {
        if (oldValue == newValue) return;
        checkValue(newValue, false);
        if (bitsPerEntry == 0) {
            if (oldValue == count) fill(newValue);
        } else {
            if (hasPalette()) {
                final int index = valueToPaletteMap.getOrDefault(oldValue, -1);
                if (index == -1) return; // Old value not present in palette
                final boolean countUpdate = newValue == 0 || oldValue == 0;
                final int count = countUpdate ? count(oldValue) : -1;
                if (count == 0) return; // No blocks to replace
                paletteToValueList.set(index, newValue);
                valueToPaletteMap.remove(oldValue);
                valueToPaletteMap.put(newValue, index);
                // Update count
                if (newValue == 0) {
                    this.count -= count; // Replacing with air
                } else if (oldValue == 0) {
                    this.count += count; // Replacing air with a block
                }
            } else {
                this.values = Palettes.remap(dimension, bitsPerEntry, directBits, values,
                        v -> v == oldValue ? newValue : v);
            }
        }
    }

    @Override
    public void setAll(@NotNull EntrySupplier supplier) {
        int[] cache = WRITE_CACHE.get();
        final int dimension = dimension();
        // Fill cache with values
        int fillValue = -1;
        int count = 0;
        int index = 0;
        int maxValue = 0;
        for (int y = 0; y < dimension; y++) {
            for (int z = 0; z < dimension; z++) {
                for (int x = 0; x < dimension; x++) {
                    int value = supplier.get(x, y, z);
                    // Support for fill fast exit if the supplier returns a constant value
                    if (fillValue != -2) {
                        if (fillValue == -1) {
                            fillValue = value;
                        } else if (fillValue != value) {
                            fillValue = -2;
                        }
                    }
                    maxValue = Math.max(maxValue, value);
                    // Set value in cache
                    if (value != 0) count++;
                    cache[index++] = value;
                }
            }
        }
        assert index == maxSize();
        // Update palette content
        if (fillValue < 0) {
            updateAll(cache, maxValue);
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
        AtomicInteger maxValue = new AtomicInteger();
        getAll((x, y, z, value) -> {
            final int newValue = function.apply(x, y, z, value);
            final int index = arrayIndex.getPlain();
            arrayIndex.setPlain(index + 1);
            maxValue.setPlain(Math.max(maxValue.getPlain(), newValue));
            cache[index] = newValue;
            if (newValue != 0) count.setPlain(count.getPlain() + 1);
        });
        assert arrayIndex.getPlain() == maxSize();
        // Update palette content
        updateAll(cache, maxValue.getPlain());
        this.count = count.getPlain();
    }

    @Override
    public void copyFrom(@NotNull Palette source, int offsetX, int offsetY, int offsetZ) {
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            copyFrom(source);
            return;
        }

        final PaletteImpl sourcePalette = (PaletteImpl) source;
        final int sourceDimension = sourcePalette.dimension();
        final int targetDimension = this.dimension();
        if (sourceDimension != targetDimension) {
            throw new IllegalArgumentException("Source palette dimension (" + sourceDimension +
                    ") must equal target palette dimension (" + targetDimension + ")");
        }

        // Calculate the actual copy bounds - only copy what fits within target bounds
        final int maxX = Math.min(sourceDimension, targetDimension - offsetX);
        final int maxY = Math.min(sourceDimension, targetDimension - offsetY);
        final int maxZ = Math.min(sourceDimension, targetDimension - offsetZ);

        // Early exit if nothing to copy (offset pushes everything out of bounds)
        if (maxX <= 0 || maxY <= 0 || maxZ <= 0) {
            return;
        }
        // Palettes already filled with air (nothing to copy)
        if (count == 0 && sourcePalette.count == 0) return;

        // Fast path: if source is single-value palette
        if (sourcePalette.bitsPerEntry == 0) {
            // Palettes are equal (nothing to copy)
            if (bitsPerEntry == 0 && count == sourcePalette.count) return;

            // Fill the region with the single value - optimized loop order
            final int value = sourcePalette.count;
            final int paletteValue = valueToPaletteIndex(value);
            final int airPaletteValue = valueToPalettIndexOrDefault(0);

            // Direct write to avoid repeated palette lookups
            for (int y = 0; y < maxY; y++) {
                final int targetY = offsetY + y;
                for (int z = 0; z < maxZ; z++) {
                    final int targetZ = offsetZ + z;
                    for (int x = 0; x < maxX; x++) {
                        final int targetX = offsetX + x;
                        final int oldValue = Palettes.write(targetDimension, bitsPerEntry, values, targetX, targetY, targetZ, paletteValue);
                        // Update count based on air transitions
                        final boolean wasAir = oldValue == airPaletteValue;
                        final boolean isAir = value == 0;
                        if (wasAir != isAir) {
                            this.count += wasAir ? 1 : -1;
                        }
                    }
                }
            }
            return;
        }

        // Source is empty, fill target region with air
        if (sourcePalette.count == 0) {
            final int airPaletteValue = valueToPaletteIndex(0);
            int removedBlocks = 0;
            for (int y = 0; y < maxY; y++) {
                final int targetY = offsetY + y;
                for (int z = 0; z < maxZ; z++) {
                    final int targetZ = offsetZ + z;
                    for (int x = 0; x < maxX; x++) {
                        final int targetX = offsetX + x;
                        final int oldValue = Palettes.write(targetDimension, bitsPerEntry, values, targetX, targetY, targetZ, airPaletteValue);
                        if (oldValue != airPaletteValue) removedBlocks++;
                    }
                }
            }
            this.count -= removedBlocks;
            return;
        }

        // General case: copy each value individually with bounds checking
        // Use optimized access patterns to minimize cache misses
        final long[] sourceValues = sourcePalette.values;
        final int sourceBitsPerEntry = sourcePalette.bitsPerEntry;
        final int sourceMask = (1 << sourceBitsPerEntry) - 1;
        final int sourceValuesPerLong = 64 / sourceBitsPerEntry;
        final int sourceDimensionBitCount = MathUtils.bitsToRepresent(sourceDimension - 1);
        final int sourceShiftedDimensionBitCount = sourceDimensionBitCount << 1;
        final int[] sourcePaletteIds = sourcePalette.hasPalette() ? sourcePalette.paletteToValueList.elements() : null;
        final int airPaletteValue = valueToPalettIndexOrDefault(0);

        int countDelta = 0;
        for (int y = 0; y < maxY; y++) {
            final int targetY = offsetY + y;
            for (int z = 0; z < maxZ; z++) {
                final int targetZ = offsetZ + z;
                for (int x = 0; x < maxX; x++) {
                    final int targetX = offsetX + x;

                    final int sourceIndex = y << sourceShiftedDimensionBitCount | z << sourceDimensionBitCount | x;
                    final int longIndex = sourceIndex / sourceValuesPerLong;
                    final int bitIndex = (sourceIndex - longIndex * sourceValuesPerLong) * sourceBitsPerEntry;
                    final int sourcePaletteIndex = (int) (sourceValues[longIndex] >> bitIndex) & sourceMask;
                    final int sourceValue = sourcePaletteIds != null && sourcePaletteIndex < sourcePaletteIds.length ?
                            sourcePaletteIds[sourcePaletteIndex] : sourcePaletteIndex;

                    // Convert to target palette index and write
                    final int targetPaletteIndex = valueToPaletteIndex(sourceValue);
                    final int oldValue = Palettes.write(targetDimension, bitsPerEntry, values, targetX, targetY, targetZ, targetPaletteIndex);

                    // Update count
                    final boolean wasAir = oldValue == (hasPalette() ? airPaletteValue : 0);
                    final boolean isAir = sourceValue == 0;
                    if (wasAir != isAir) {
                        countDelta += wasAir ? 1 : -1;
                    }
                }
            }
        }

        this.count += countDelta;
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
        if (this.directBits != sourcePalette.directBits) this.directBits = sourcePalette.directBits;

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
        } else {
            this.valueToPaletteMap = null;
        }
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
        int queryValue = valueToPalettIndexOrDefault(value);
        if (queryValue == -1) return 0;
        return Palettes.count(dimension, bitsPerEntry, queryValue, values);
    }

    @Override
    public boolean any(int value) {
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
        final IntArrayList uniqueValues = new IntArrayList();
        if (bitsPerEntry <= 6 && hasPalette()) {
            AtomicLong unique = new AtomicLong();
            retrievePaletteIndices(v -> unique.setPlain(unique.getPlain() | (1L << v)));
            long plainUnique = unique.getPlain();
            final int uniqueCount = Long.bitCount(plainUnique);
            if (MathUtils.bitsToRepresent(uniqueCount - 1) >= bitsPerEntry) return;
            final int[] oldPalette = paletteToValueList.elements();
            for (int idx = 0; idx < paletteToValueList.size(); idx++) {
                if ((plainUnique & 1) == 1) uniqueValues.add(oldPalette[idx]);
                plainUnique >>>= 1;
            }
        } else {
            IntSet uniqueSet = new IntOpenHashSet();
            final int maxCounted = maxPaletteSize(maxBitsPerEntry) + 1;
            getAll((x, y, z, value) -> {
                if (uniqueSet.size() <= maxCounted) uniqueSet.add(value);
            });
            // Can't optimize, too many different values
            if (uniqueSet.size() >= maxCounted) return;
            uniqueSet.forEach(uniqueValues::add);
        }
        final int uniqueCount = uniqueValues.size();

        // If only one unique value, use fill for maximum optimization
        if (uniqueCount == 1) {
            fill(uniqueValues.getInt(0));
            return;
        }

        if (focus == Optimization.SPEED) {
            // Speed optimization - use direct storage
            makeDirect();
        } else if (focus == Optimization.SIZE) {
            // Size optimization - calculate minimum bits needed for unique values
            downsizeWithPalette((byte) MathUtils.bitsToRepresent(uniqueCount - 1), uniqueValues);
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

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    @Override
    public int paletteIndexToValue(int value) {
        return hasPalette() ? paletteToValueList.elements()[value] : value;
    }

    @Override
    public int valueToPaletteIndex(int value) {
        checkValue(value, true);
        if (!hasPalette()) return value;
        if (values == null) initIndirect();

        return valueToPaletteMap.computeIfAbsent(value, (v) -> {
            final int lastPaletteIndex = this.paletteToValueList.size();
            final byte bpe = this.bitsPerEntry;
            if (lastPaletteIndex >= maxPaletteSize(bpe)) {
                // Palette is full, must resize
                upsize();
                if (!hasPalette()) return v;
            }
            this.paletteToValueList.add(v);
            return lastPaletteIndex;
        });
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    int valueToPalettIndexOrDefault(int value) {
        return hasPalette() ? valueToPaletteMap.getOrDefault(value, -1) : value;
    }

    @Override
    public int singleValue() {
        return bitsPerEntry == 0 ? count : -1;
    }

    @Override
    public long @Nullable [] indexedValues() {
        return values;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NotNull Palette clone() {
        PaletteImpl clone = new PaletteImpl(dimension, minBitsPerEntry, maxBitsPerEntry, directBits);
        clone.bitsPerEntry = this.bitsPerEntry;
        clone.count = this.count;
        if (bitsPerEntry == 0) return clone;
        clone.values = values.clone();
        if (paletteToValueList != null) clone.paletteToValueList = paletteToValueList.clone();
        if (valueToPaletteMap != null) clone.valueToPaletteMap = valueToPaletteMap.clone();
        return clone;
    }

    private void retrieveAll(@NotNull EntryConsumer consumer, boolean consumeEmpty) {
        if (!consumeEmpty && count == 0) return;
        if (bitsPerEntry == 0) {
            Palettes.getAllFill(dimension, count, consumer);
            return;
        }
        final long[] values = this.values;
        final int dimension = this.dimension();
        final int bitsPerEntry = this.bitsPerEntry;
        final int magicMask = (1 << bitsPerEntry) - 1;
        final int valuesPerLong = 64 / bitsPerEntry;
        final int size = maxSize();
        final int dimensionMinus = dimension - 1;
        final int[] ids = hasPalette() ? paletteToValueList.elements() : null;
        final int dimensionBitCount = MathUtils.bitsToRepresent(dimensionMinus);
        final int shiftedDimensionBitCount = dimensionBitCount << 1;
        for (int i = 0; i < values.length; i++) {
            final long value = values[i];
            final int startIndex = i * valuesPerLong;
            final int endIndex = Math.min(startIndex + valuesPerLong, size);
            for (int index = startIndex; index < endIndex; index++) {
                final int bitIndex = (index - startIndex) * bitsPerEntry;
                final int paletteIndex = (int) (value >> bitIndex & magicMask);
                if (consumeEmpty || paletteIndex != 0) {
                    final int y = index >> shiftedDimensionBitCount;
                    final int z = index >> dimensionBitCount & dimensionMinus;
                    final int x = index & dimensionMinus;
                    final int result = ids != null && paletteIndex < ids.length ? ids[paletteIndex] : paletteIndex;
                    consumer.accept(x, y, z, result);
                }
            }
        }
    }

    private void retrievePaletteIndices(IntConsumer consumer) {
        final int size = maxSize();
        final int bits = bitsPerEntry;
        final int valuesPerLong = 64 / bits;
        final int mask = (1 << bits) - 1;
        for (int i = 0, idx = 0; i < values.length; i++) {
            long block = values[i];
            int end = Math.min(valuesPerLong, size - idx);
            for (int j = 0; j < end; j++, idx++) {
                consumer.accept((int) (block & mask));
                block >>>= bits;
            }
        }
    }

    private void updateAll(int[] paletteValues, int maxValue) {
        checkValue(maxValue, false);
        if (hasPalette()) {
            this.bitsPerEntry = directBits;
            this.values = new long[arrayLength(dimension, directBits)];
            this.valueToPaletteMap = null;
            this.paletteToValueList = null;
        } else if (bitsPerEntry != directBits) {
            this.values = new long[arrayLength(dimension, directBits)];
            this.bitsPerEntry = directBits;
        }
        final int size = maxSize();
        assert paletteValues.length >= size;
        final int bitsPerEntry = this.bitsPerEntry;
        final int valuesPerLong = 64 / bitsPerEntry;
        final long clear = (1L << bitsPerEntry) - 1L;
        final long[] values = this.values;
        for (int i = 0; i < values.length; i++) {
            long block = values[i];
            final int startIndex = i * valuesPerLong;
            final int endIndex = Math.min(startIndex + valuesPerLong, size);
            for (int index = startIndex; index < endIndex; index++) {
                final int bitIndex = (index - startIndex) * bitsPerEntry;
                block = block & ~(clear << bitIndex) | ((long) paletteValues[index] << bitIndex);
            }
            values[i] = block;
        }
    }

    /// Assumes {@link PaletteImpl#bitsPerEntry} != 0
    private void downsizeWithPalette(byte newBpe, IntArrayList palette) {
        final int bpe = this.bitsPerEntry;
        newBpe = (byte) Math.max(newBpe, minBitsPerEntry);
        if (newBpe >= bpe || newBpe > maxBitsPerEntry) return;

        // Fill new palette <-> value objects
        final Int2IntOpenHashMap newValueToPaletteMap = new Int2IntOpenHashMap();
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
            this.values = Palettes.remap(dimension, bpe, newBpe, values, (v) -> v);
            this.bitsPerEntry = newBpe;
        }
    }

    void initIndirect() {
        final int fillValue = this.count;
        this.valueToPaletteMap = new Int2IntOpenHashMap();
        this.paletteToValueList = new IntArrayList();
        this.valueToPaletteMap.put(fillValue, 0);
        paletteToValueList.add(fillValue);
        this.bitsPerEntry = minBitsPerEntry;
        this.values = new long[arrayLength(dimension, minBitsPerEntry)];
        this.count = fillValue == 0 ? 0 : maxSize();
    }

    void checkValue(int value, boolean resize) {
        final byte valueBits = (byte) MathUtils.bitsToRepresent(value);
        if (directBits < valueBits) {
            if (!hasPalette() && resize) {
                this.values = Palettes.remap(dimension, bitsPerEntry, valueBits, values, v -> v);
                this.bitsPerEntry = valueBits;
            }
            this.directBits = valueBits;
        }
    }

    boolean hasPalette() {
        return bitsPerEntry <= maxBitsPerEntry;
    }

    void recount() {
        if (bitsPerEntry == 0) return;
        int queryValue = valueToPalettIndexOrDefault(0);
        if (queryValue == -1) {
            this.count = maxSize();
            return;
        }
        this.count = maxSize() - Palettes.count(dimension, bitsPerEntry, queryValue, values);
    }

    void printState() {
        String stateString = "palette state:" +
                "\n- dimension: " + dimension +
                "\n- bpe: " + bitsPerEntry +
                "\n- count: " + count +
                "\n- value -> palette: " + valueToPaletteMap +
                "\n- palette -> value: " + paletteToValueList +
                "\n- values: " + Arrays.toString(values);

        System.out.println(stateString);
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
}
