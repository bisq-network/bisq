package bisq.core.trade.protocol.bisq_v1.tasks.maker;

import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MakerProcessesInputsForDepositTxRequestTest {
    private static final int GENESIS_HEIGHT = 102;
    private static final int GRID_SIZE = DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE;

    @Test
    void burningManSelectionHeightSameBlock() {
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 139, GRID_SIZE, 0));
        boolean isValid = MakerProcessesInputsForDepositTxRequest
                .verifyBurningManSelectionHeight(130, 130);
        assertTrue(isValid);
    }

    @Test
    void burningManSelectionHeightMakerOneBlockInFuture() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));
        boolean isValid = MakerProcessesInputsForDepositTxRequest
                .verifyBurningManSelectionHeight(120, 130);
        assertTrue(isValid);
    }

    @Test
    void burningManSelectionHeightTakerOneBlockInFuture() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));
        boolean isValid = MakerProcessesInputsForDepositTxRequest
                .verifyBurningManSelectionHeight(130, 120);
        assertTrue(isValid);
    }
}
