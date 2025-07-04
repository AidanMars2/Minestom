package net.minestom.server.inventory.click;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.minestom.server.inventory.click.ClickUtils.*;
import static net.minestom.server.network.packet.client.play.ClientClickWindowPacket.ClickType.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClickPreprocessorTest {

    static {
        MinecraftServer.init();
    }

    @Test
    public void testPickupType() {
        assertProcessed(new Click.LeftDropCursor(), clickPacket(PICKUP, 1, 0, -999));
        assertProcessed(new Click.RightDropCursor(), clickPacket(PICKUP, 1, 1, -999));
        assertProcessed(new Click.MiddleDropCursor(), clickPacket(CLONE, 1, 2, -999));

        assertProcessed(new Click.Left(0), clickPacket(PICKUP, 1, 0, 0));
        assertProcessed(new Click.Left(SIZE + 9), clickPacket(PICKUP, 1, 0, 5));
        assertProcessed(null, clickPacket(PICKUP, 1, 0, 99));

        assertProcessed(new Click.Right(0), clickPacket(PICKUP, 1, 1, 0));
        assertProcessed(new Click.Right(SIZE + 9), clickPacket(PICKUP, 1, 1, 5));
        assertProcessed(null, clickPacket(PICKUP, 1, 1, 99));

        assertProcessed(null, clickPacket(PICKUP, 1, -1, 0));
        assertProcessed(null, clickPacket(PICKUP, 1, 2, 0));
    }

    @Test
    public void testQuickMoveType() {
        assertProcessed(new Click.LeftShift(0), clickPacket(QUICK_MOVE, 1, 0, 0));
        assertProcessed(new Click.LeftShift(SIZE + 9), clickPacket(QUICK_MOVE, 1, 0, 5));
        assertProcessed(null, clickPacket(QUICK_MOVE, 1, 0, -1));
    }

    @Test
    public void testSwapType() {
        assertProcessed(null, clickPacket(SWAP, 1, 0, -1));
        assertProcessed(new Click.HotbarSwap(0, 2), clickPacket(SWAP, 1, 0, 2));
        assertProcessed(new Click.HotbarSwap(8, 2), clickPacket(SWAP, 1, 8, 2));
        assertProcessed(new Click.OffhandSwap(2), clickPacket(SWAP, 1, 40, 2));

        assertProcessed(null, clickPacket(SWAP, 1, 9, 2));
        assertProcessed(null, clickPacket(SWAP, 1, 39, 2));
    }

    @Test
    public void testCloneType() {
        assertProcessed(new Click.Middle(0), clickPacket(CLONE, 1, 0, 0));
        assertProcessed(null, clickPacket(CLONE, 1, 0, -1));
    }

    @Test
    public void testThrowType() {
        assertProcessed(new Click.LeftDropCursor(), clickPacket(THROW, 1, 0, -999));
        assertProcessed(new Click.RightDropCursor(), clickPacket(THROW, 1, 1, -999));

        assertProcessed(new Click.DropSlot(0, true), clickPacket(THROW, 1, 1, 0));

        assertProcessed(new Click.DropSlot(0, false), clickPacket(THROW, 1, 0, 0));
        assertProcessed(new Click.DropSlot(0, true), clickPacket(THROW, 1, 1, 0));

        assertProcessed(new Click.DropSlot(1, false), clickPacket(THROW, 1, 0, 1));
        assertProcessed(new Click.DropSlot(1, true), clickPacket(THROW, 1, 1, 1));
    }

    @Test
    public void testQuickCraft() {
        var processor = new ClickPreprocessor();

        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 0, 0));
        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 1, 0));
        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 1, 1));
        assertProcessed(processor, new Click.LeftDrag(IntList.of(0, 1)), clickPacket(QUICK_CRAFT, 1, 2, -999));

        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 4, 0));
        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 5, 0));
        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 5, 1));
        assertProcessed(processor, new Click.RightDrag(IntList.of(0, 1)), clickPacket(QUICK_CRAFT, 1, 6, -999));

        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 8, 0));
        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 9, 0));
        assertProcessed(processor, null, clickPacket(QUICK_CRAFT, 1, 9, 1));
        assertProcessed(processor, new Click.MiddleDrag(IntList.of(0, 1)), clickPacket(QUICK_CRAFT, 1, 10, -999));
    }

    @Test
    public void testCreativeClicks() {
        var processor = new ClickPreprocessor();

        assertTrue(processor.isCreativeClick(new Click.Middle(0), false));
        assertFalse(processor.isCreativeClick(new Click.Middle(0), true));

        assertTrue(processor.isCreativeClick(new Click.MiddleDrag(List.of(1, 2)), false));
        assertTrue(processor.isCreativeClick(new Click.MiddleDrag(List.of(1, 2)), true));

        assertFalse(processor.isCreativeClick(new Click.Left(5), true));
        assertFalse(processor.isCreativeClick(new Click.Right(5), true));
    }

}