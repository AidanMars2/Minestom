package net.minestom.server.instance.palette;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minestom.server.utils.MathUtils;

import java.util.Arrays;

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

    public static int count(int bitsPerEntry, long[] values) {
        final int valuesPerLong = 64 / bitsPerEntry;
        int count = 0;
        for (long block : values) {
            for (int i = 0; i < valuesPerLong; i++) {
                count += (int) ((block >>> i * bitsPerEntry) & ((1 << bitsPerEntry) - 1));
            }
        }
        return count;
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
        final long[] result = new long[arrayLength(dimension, newBitsPerEntry)];
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

    public static int partialFill(int dimension, int bpe, long[] values,
                                  int fillValue, boolean isAir, int airValue,
                                  int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ) {
        final int dimensionBits = MathUtils.bitsToRepresent(dimension - 1);
        final int initialZTravel = minZ << dimensionBits;
        final int finalZTravel = (dimension - maxZ) << dimensionBits;
        final int finalXTravel = dimension - minX;

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
                    // Update count
                    final boolean wasAir = ((block >>> bitIndex) & mask) == airValue;
                    if (wasAir != isAir) countDelta += wasAir ? 1 : -1;

                    // Write to block
                    block = (block & ~(mask << bitIndex)) | ((long) fillValue << bitIndex);
                    bitIndex += bpe;

                    // Write block to values
                    if (bitIndex >= maxBitIndex) {
                        values[blockIndex++] = block;
                        if (blockIndex >= values.length) return countDelta;
                        block = values[blockIndex];
                        bitIndex = 0;
                    }
                }
                values[blockIndex] = block;
                index += finalXTravel;
            }
            index += finalZTravel;
        }
        return countDelta;
    }

    static int partialOffsetCopy(PaletteImpl sourcePalette, PaletteImpl target,
                                 int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ) {
        if (target.bitsPerEntry == 0) target.initIndirect();
        final int dimension = target.dimension;

        final long[] sourceValues = sourcePalette.values;
        final int sourceBpe = sourcePalette.bitsPerEntry;
        final int sourceValuesPerLong = 64 / sourceBpe;
        final int sourceMaxBitIndex = sourceValuesPerLong * sourceBpe;
        final long sourceMask = (1L << sourceBpe) - 1;
        final int[] sourcePaletteIds = sourcePalette.hasPalette() ? sourcePalette.paletteToValueList.elements() : null;

        final int dimensionBits = MathUtils.bitsToRepresent(dimension - 1);
        final int initialZTravel = minZ << dimensionBits;
        final int finalZTravel = (dimension - maxZ) << dimensionBits;
        final int finalXTravel = dimension - maxX;
        final int finalSourceXTravel = dimension - minX;

        final int airPaletteIndex = target.valueToPalettIndexOrDefault(0);

        int bpe = target.bitsPerEntry;
        int valuesPerLong = 64 / bpe;
        int maxBitIndex = bpe * valuesPerLong;
        long mask = (1L << bpe) - 1;

        int index = minY << (dimensionBits << 1);
        int sourceIndex = (dimension - maxY) << (dimensionBits << 1);
        int countDelta = 0;
        for (int y = 0; y < maxY; y++) {
            index += initialZTravel;
            sourceIndex += finalZTravel;
            for (int z = 0; z < maxZ; z++) {
                index += minX;
                sourceIndex += finalXTravel;
                int blockIndex = index / valuesPerLong;
                int bitIndex = (index - blockIndex * valuesPerLong) * bpe;

                int sourceBlockIndex = sourceIndex / sourceValuesPerLong;
                int sourceBitIndex = (sourceIndex - sourceBlockIndex * sourceValuesPerLong) * sourceBpe;
                long sourceBlock = sourceValues[sourceBlockIndex];

                for (int x = 0; x < maxX; x++) {
                    if (sourceBitIndex >= sourceMaxBitIndex) {
                        sourceBlock = sourceValues[++sourceBlockIndex];
                        sourceBitIndex = 0;
                    }
                    final int sourcePaletteIndex = (int) ((sourceBlock >>> sourceBitIndex) & sourceMask);
                    final int sourceValue = sourcePaletteIds == null ? sourcePaletteIndex : sourcePaletteIds[sourcePaletteIndex];
                    final int newPaletteIndex = target.valueToPaletteIndex(sourceValue);
                    sourceBitIndex += sourceBpe;

                    // Recalculate cached values if palette resized
                    if (target.bitsPerEntry != bpe) {
                        bpe = target.bitsPerEntry;
                        valuesPerLong = 64 / bpe;
                        maxBitIndex = bpe * valuesPerLong;
                        mask = (1L << bpe) - 1;

                        blockIndex = index / valuesPerLong;
                        bitIndex = (index % valuesPerLong) * bpe;
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
                index += finalXTravel;
                sourceIndex += finalSourceXTravel;
            }
            index += finalZTravel;
            sourceIndex += initialZTravel;
        }
        return countDelta;
    }
}
