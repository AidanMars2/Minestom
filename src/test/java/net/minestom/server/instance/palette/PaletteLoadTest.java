package net.minestom.server.instance.palette;

import net.minestom.server.utils.MathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PaletteLoadTest {

    @Test
    public void loadBelowMinBitsPerEntry() {
        // Test loading with bpe below minBitsPerEntry - should resize to minBitsPerEntry
        Palette palette = Palette.sized(4, 4, 8, 1 << 15, 4); // min=4, max=8, direct=15

        int[] paletteData = {0, 1, 2, 3}; // 4 values need 2 bits, but min is 4
        long[] values = new long[]{0x3210L}; // packed with 2 bits per entry

        palette.load(paletteData, values);

        // Should be resized to minBitsPerEntry (4)
        assertEquals(4, palette.bitsPerEntry());

        // Values should still be accessible correctly
        assertEquals(0, palette.get(0, 0, 0));
        assertEquals(1, palette.get(1, 0, 0));
        assertEquals(2, palette.get(2, 0, 0));
        assertEquals(3, palette.get(3, 0, 0));
    }

    @Test
    public void loadAboveMaxBitsPerEntry() {
        // Test loading with bpe above maxBitsPerEntry - should become direct palette
        Palette palette = Palette.sized(4, 1, 3, 1 << 15, 1); // min=1, max=3, direct=15

        // Create palette that would need more than 3 bits (max) - 16 values need 4 bits
        int[] paletteData = new int[16];
        for (int i = 0; i < 16; i++) {
            paletteData[i] = i + 100; // arbitrary values
        }

        // Create values array with 4 bits per entry
        long[] values = new long[4]; // 64 entries, 4 bits each = 16 longs per entry, 4 longs total
        for (int i = 0; i < 64; i++) {
            int longIndex = i / 16;
            int bitIndex = (i % 16) * 4;
            values[longIndex] |= ((long) (i % 16)) << bitIndex;
        }

        palette.load(paletteData, values);

        // Should become direct palette (directBits = 15)
        assertEquals(15, palette.bitsPerEntry());

        // Should not have a palette anymore (direct mode)
        assertNull(((PaletteImpl) palette).paletteToValueList);
    }

    @Test
    public void loadWithinRange() {
        // Test loading with bpe within min-max range - should use calculated bpe
        Palette palette = Palette.sized(4, 2, 6, 1 << 15, 2); // min=2, max=6, direct=15

        int[] paletteData = {0, 10, 20, 30, 40}; // 5 values need 3 bits
        long[] values = new long[12]; // 64 entries, 3 bits each

        // Fill with some test pattern
        for (int i = 0; i < 64; i++) {
            int longIndex = i / 21; // 21 values per long with 3 bits each (63 bits used)
            int bitIndex = (i % 21) * 3;
            values[longIndex] |= ((long) (i % 5)) << bitIndex;
        }

        palette.load(paletteData, values);

        // Should use 3 bits (calculated from palette size)
        assertEquals(3, palette.bitsPerEntry());

        // Should have palette
        assertNotNull(((PaletteImpl) palette).paletteToValueList);

        // Verify palette contents
        assertEquals(5, ((PaletteImpl) palette).paletteToValueList.size());
        assertEquals(0, ((PaletteImpl) palette).paletteToValueList.getInt(0));
        assertEquals(10, ((PaletteImpl) palette).paletteToValueList.getInt(1));
        assertEquals(20, ((PaletteImpl) palette).paletteToValueList.getInt(2));
        assertEquals(30, ((PaletteImpl) palette).paletteToValueList.getInt(3));
        assertEquals(40, ((PaletteImpl) palette).paletteToValueList.getInt(4));
    }

    @Test
    public void loadExactlyMinBitsPerEntry() {
        // Test loading where calculated bpe equals minBitsPerEntry
        Palette palette = Palette.sized(4, 3, 8, 1 << 15, 3); // min=3, max=8, direct=15

        int[] paletteData = {0, 1, 2, 3, 4, 5, 6, 7}; // 8 values need exactly 3 bits
        long[] values = new long[12]; // 64 entries, 3 bits each

        palette.load(paletteData, values);

        // Should use exactly minBitsPerEntry (3)
        assertEquals(3, palette.bitsPerEntry());

        // Should have palette
        assertNotNull(((PaletteImpl) palette).paletteToValueList);
        assertEquals(8, ((PaletteImpl) palette).paletteToValueList.size());
    }

    @Test
    public void loadExactlyMaxBitsPerEntry() {
        // Test loading where calculated bpe equals maxBitsPerEntry
        Palette palette = Palette.sized(4, 2, 4, 1 << 15, 2); // min=2, max=4, direct=15

        int[] paletteData = new int[16]; // 16 values need exactly 4 bits
        for (int i = 0; i < 16; i++) {
            paletteData[i] = i * 10;
        }
        long[] values = new long[16]; // 64 entries, 4 bits each

        palette.load(paletteData, values);

        // Should use exactly maxBitsPerEntry (4)
        assertEquals(4, palette.bitsPerEntry());

        // Should still have palette (not direct)
        assertNotNull(((PaletteImpl) palette).paletteToValueList);
        assertEquals(16, ((PaletteImpl) palette).paletteToValueList.size());
    }

    @Test
    public void loadValuesCloned() {
        // Test that values array is properly cloned
        Palette palette = Palette.sized(4, 2, 6, 1 << 15, 2);

        int[] paletteData = {0, 1, 2};
        long[] originalValues = {0x123456789ABCDEFL, 0xFEDCBA9876543210L};

        palette.load(paletteData, originalValues);

        // Modify original array
        originalValues[0] = 0L;
        originalValues[1] = 0L;

        // Palette should still have the original values
        long[] paletteValues = palette.indexedValues();
        assertNotNull(paletteValues);
        assertEquals(0x123456789ABCDEFL, paletteValues[0]);
        assertEquals(0xFEDCBA9876543210L, paletteValues[1]);
    }

    @Test
    public void loadThousandsOfIndicesBecomesDirectPalette() {
        // Test loading with thousands of indices to ensure it becomes a direct palette
        Palette palette = Palette.blocks(); // min=4, max=8, direct=15

        // Create palette with thousands of unique values (way more than max palette size of 2^8=256)
        final int uniqueValueCount = 5000;
        int[] paletteData = new int[uniqueValueCount];
        for (int i = 0; i < uniqueValueCount; i++) {
            paletteData[i] = i + 1000; // Use offset to avoid zero values
        }

        // Calculate bits needed: log2(5000) ≈ 13 bits, which exceeds maxBitsPerEntry (8)
        // This should force direct palette mode
        int calculatedBits = 13; // Math.ceil(Math.log(uniqueValueCount) / Math.log(2))

        // Create values array for 4096 entries (16x16x16) with calculated bits per entry
        final int totalEntries = 16 * 16 * 16; // 4096 entries
        final int valuesPerLong = 64 / calculatedBits;
        final int valuesArrayLength = (totalEntries + valuesPerLong - 1) / valuesPerLong;
        long[] values = new long[valuesArrayLength];

        // Fill with pattern using modulo to cycle through available palette indices
        final long mask = (1L << calculatedBits) - 1;
        for (int i = 0; i < totalEntries; i++) {
            int paletteIndex = i % uniqueValueCount;
            int longIndex = i / valuesPerLong;
            int bitIndex = (i % valuesPerLong) * calculatedBits;
            values[longIndex] |= ((long) paletteIndex & mask) << bitIndex;
        }

        palette.load(paletteData, values);

        // Should become direct palette since uniqueValueCount >> 2^maxBitsPerEntry
        assertEquals(MathUtils.bitsToRepresent(Palette.BLOCK_PALETTE_REGISTRY_SIZE - 1), palette.bitsPerEntry(),
                "Palette should use direct bits when loaded with thousands of indices");

        // Should not have indirect palette structures (direct mode)
        PaletteImpl impl = (PaletteImpl) palette;
        assertNull(impl.paletteToValueList,
                "Direct palette should not have paletteToValueList");

        // Verify we can still read some values correctly
        // In direct mode, palette indices become the actual values
        int firstValue = palette.get(0, 0, 0);
        assertTrue(firstValue >= 1000 && firstValue < 1000 + uniqueValueCount,
                "Value should be within expected range for direct palette: " + firstValue);

        // Verify the palette has proper count (non-zero blocks)
        assertTrue(palette.count() > 0, "Palette should have non-zero count");
        assertTrue(palette.count() <= palette.maxSize(), "Count should not exceed max size");
    }
}
