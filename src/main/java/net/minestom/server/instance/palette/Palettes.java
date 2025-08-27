package net.minestom.server.instance.palette;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;

@ApiStatus.Internal
public final class Palettes {
    private Palettes() {
    }

    public static long[] pack(int[] ints, int bitsPerEntry) {
        final int intsPerLong = (int) Math.floor(64d / bitsPerEntry);
        long[] longs = new long[(int) Math.ceil(ints.length / (double) intsPerLong)];
        final long mask = (1L << bitsPerEntry) - 1L;
        for (int i = 0; i < longs.length; i++) {
            for (int intIndex = 0; intIndex < intsPerLong; intIndex++) {
                final int bitIndex = intIndex * bitsPerEntry;
                final int intActualIndex = intIndex + i * intsPerLong;
                if (intActualIndex < ints.length) {
                    longs[i] |= (ints[intActualIndex] & mask) << bitIndex;
                }
            }
        }
        return longs;
    }

    public static void unpack(int[] out, long[] in, int bitsPerEntry) {
        assert in.length != 0 : "unpack input array is zero";

        final double intsPerLong = Math.floor(64d / bitsPerEntry);
        final int intsPerLongCeil = (int) Math.ceil(intsPerLong);

        final long mask = (1L << bitsPerEntry) - 1L;
        for (int i = 0; i < out.length; i++) {
            final int longIndex = i / intsPerLongCeil;
            final int subIndex = i % intsPerLongCeil;
            out[i] = (int) ((in[longIndex] >>> (bitsPerEntry * subIndex)) & mask);
        }
    }

    public static int maxPaletteSize(int bitsPerEntry) {
        return 1 << bitsPerEntry;
    }

    public static int arrayLength(int dimension, int bitsPerEntry) {
        final int elementCount = dimension * dimension * dimension;
        final int valuesPerLong = 64 / bitsPerEntry;
        return (elementCount + valuesPerLong - 1) / valuesPerLong;
    }

    public static int read(int dimension, int bitsPerEntry, long[] values,
                           int x, int y, int z) {
        final int sectionIndex = sectionIndex(dimension, x, y, z);
        final int valuesPerLong = 64 / bitsPerEntry;
        final int index = sectionIndex / valuesPerLong;
        final int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;
        final int mask = (1 << bitsPerEntry) - 1;
        return (int) (values[index] >> bitIndex) & mask;
    }

    public static int write(int dimension, int bitsPerEntry, long[] values,
                            int x, int y, int z, int value) {
        final int valuesPerLong = 64 / bitsPerEntry;
        final int sectionIndex = sectionIndex(dimension, x, y, z);
        final int index = sectionIndex / valuesPerLong;
        final int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;

        final long block = values[index];
        final long clear = (1L << bitsPerEntry) - 1L;
        final long oldBlock = block >> bitIndex & clear;
        values[index] = block & ~(clear << bitIndex) | ((long) value << bitIndex);
        return (int) oldBlock;
    }

    public static void fill(int bitsPerEntry, long[] values, int value) {
        final int valuesPerLong = 64 / bitsPerEntry;
        long block = 0;
        for (int i = 0; i < valuesPerLong; i++) block |= (long) value << i * bitsPerEntry;
        Arrays.fill(values, block);
    }

    public static int sectionIndex(int dimension, int x, int y, int z) {
        final int dimensionBitCount = MathUtils.bitsToRepresent(dimension - 1);
        return y << (dimensionBitCount << 1) | z << dimensionBitCount | x;
    }

    // Optimized operations

    public static void getAllFill(byte dimension, int value, Palette.EntryConsumer consumer) {
        for (byte y = 0; y < dimension; y++)
            for (byte z = 0; z < dimension; z++)
                for (byte x = 0; x < dimension; x++)
                    consumer.accept(x, y, z, value);
    }

    public static long[] remap(int dimension, int oldBitsPerEntry, int newBitsPerEntry,
                               long[] values, Int2IntFunction function) {
        return remap(dimension, oldBitsPerEntry, newBitsPerEntry, values, false, function);
    }

    public static long[] remap(int dimension, int oldBitsPerEntry, int newBitsPerEntry,
                               long[] values, boolean forceRealloc, Int2IntFunction function) {
        final int arrayLength = arrayLength(dimension, newBitsPerEntry);
        final long[] result = forceRealloc || values.length != arrayLength || oldBitsPerEntry > newBitsPerEntry ?
                new long[arrayLength(dimension, newBitsPerEntry)] : values;
        final int magicMask = (1 << oldBitsPerEntry) - 1;
        final int oldValuesPerLong = 64 / oldBitsPerEntry;
        final int newValuesPerLong = 64 / newBitsPerEntry;
        final int size = dimension * dimension * dimension;
        long newValue = 0;
        int newValueIndex = 0;
        int newBitIndex = 0;
        outer: {
            for (int i = 0; i < values.length; i++) {
                long value = values[i];
                final int startIndex = i * oldValuesPerLong;
                final int endIndex = Math.min(startIndex + oldValuesPerLong, size);
                for (int index = startIndex; index < endIndex; index++) {
                    final int paletteIndex = (int) (value & magicMask);
                    value >>>= oldBitsPerEntry;
                    newValue |= ((long) function.get(paletteIndex)) << (newBitIndex++ * newBitsPerEntry);
                    if (newBitIndex >= newValuesPerLong) {
                        result[newValueIndex++] = newValue;
                        if (newValueIndex == result.length) {
                            break outer;
                        }
                        newBitIndex = 0;
                        newValue = 0;
                    }
                }
            }
            result[newValueIndex] = newValue;
        }
        return result;
    }

    static int partialOffsetCopy(PaletteImpl source, PaletteImpl target,
                                 int offsetX, int offsetY, int offsetZ,
                                 boolean ignoreEmpty, int valueOffset) {
        final int dimension = target.dimension;
        final long[] sourceValues = source.values;
        final int sourceBpe = source.bitsPerEntry;
        final int sourceValuesPerLong = 64 / sourceBpe;
        final int sourceMaxBitIndex = sourceValuesPerLong * sourceBpe;
        final long sourceMask = (1L << sourceBpe) - 1;
        final int[] sourcePaletteIds = source.hasPalette() ? source.paletteToValueList.elements() : null;

        final int dimensionBits = Integer.numberOfTrailingZeros(dimension);
        final int minXTravel = Math.max(0,  offsetX);
        final int maxXTravel = Math.max(0, -offsetX);
        final int minZTravel = Math.max(0,  offsetZ) << dimensionBits;
        final int maxZTravel = Math.max(0, -offsetZ) << dimensionBits;

        final int airPaletteIndex = target.valueToPalettIndexOrDefault(0);
        int bpe = target.bitsPerEntry;
        int valuesPerLong = 64 / bpe;
        int maxBitIndex = bpe * valuesPerLong;
        long mask = (1L << bpe) - 1;

        int index = Math.max(0, offsetY) << (dimensionBits << 1);
        // bounds for source palette are reversed
        int sourceIndex = Math.max(0, -offsetY) << (dimensionBits << 1);
        int countDelta = 0;
        for (int y = Math.abs(offsetY); y < dimension; y++) {
            index += minZTravel;
            sourceIndex += maxZTravel;
            for (int z = Math.abs(offsetZ); z < dimension; z++) {
                index += minXTravel;
                sourceIndex += maxXTravel;
                int blockIndex = index / valuesPerLong;
                int bitIndex = (index - blockIndex * valuesPerLong) * bpe;

                int sourceBlockIndex = sourceIndex / sourceValuesPerLong;
                int sourceBitIndex = (sourceIndex - sourceBlockIndex * sourceValuesPerLong) * sourceBpe;
                long sourceBlock = sourceValues[sourceBlockIndex];

                for (int x = Math.abs(offsetX); x < dimension; x++) {
                    if (sourceBitIndex >= sourceMaxBitIndex) {
                        sourceBlock = sourceValues[++sourceBlockIndex];
                        sourceBitIndex = 0;
                    }
                    final int sourcePaletteIndex = (int) ((sourceBlock >>> sourceBitIndex) & sourceMask);
                    final int sourceValue = sourcePaletteIds == null ? sourcePaletteIndex : sourcePaletteIds[sourcePaletteIndex];
                    sourceBitIndex += sourceBpe;
                    if (sourceValue == 0 && ignoreEmpty) {
                        bitIndex += bpe;
                        index++;
                        continue;
                    }
                    final int newPaletteIndex = target.valueToPaletteIndex(sourceValue + valueOffset);

                    // Recalculate cached values if palette resized
                    if (target.bitsPerEntry != bpe) {
                        bpe = target.bitsPerEntry;
                        valuesPerLong = 64 / bpe;
                        maxBitIndex = bpe * valuesPerLong;
                        mask = (1L << bpe) - 1;

                        blockIndex = index / valuesPerLong;
                        bitIndex = (index - blockIndex * valuesPerLong) * bpe;
                    }

                    if (bitIndex >= maxBitIndex) {
                        blockIndex++;
                        bitIndex = 0;
                    }
                    long block = target.values[blockIndex];

                    // Update count
                    final boolean wasAir = ((block >>> bitIndex) & mask) == airPaletteIndex;
                    final boolean isAir = sourceValue == 0;
                    if (wasAir != isAir) countDelta += wasAir ? 1 : -1;

                    // Write to block
                    target.values[blockIndex] = (block & ~(mask << bitIndex)) | ((long) newPaletteIndex << bitIndex);
                    bitIndex += bpe;
                    index++;
                }
                index += maxXTravel;
                sourceIndex += dimension - maxXTravel;
            }
            index += maxZTravel;
            sourceIndex += minZTravel;
        }
        return countDelta;
    }
}
