/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.bridge.grpc.services;

import bisq.core.dao.burningman.BurningManService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import io.grpc.stub.StreamObserver;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



import bisq.bridge.protobuf.BurningmanBlockDto;
import bisq.bridge.protobuf.BurningmanBlockSubscription;

public class BurningmanGrpcServiceTest {
    private static final int BLOCK_HEIGHT = 941_123;
    private static final int SELECTION_HEIGHT = 941_120;

    private BurningManService burningManService;
    private BurningmanGrpcService service;
    private Block block;

    @BeforeEach
    public void setUp() {
        DaoStateService daoStateService = mock(DaoStateService.class);
        burningManService = mock(BurningManService.class);
        DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = mock(DelayedPayoutTxReceiverService.class);
        block = mock(Block.class);
        when(block.getHeight()).thenReturn(BLOCK_HEIGHT);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight(BLOCK_HEIGHT)).thenReturn(SELECTION_HEIGHT);
        when(daoStateService.getBlockAtHeight(SELECTION_HEIGHT)).thenReturn(Optional.of(block));
        service = new BurningmanGrpcService(daoStateService, burningManService, delayedPayoutTxReceiverService);
    }

    @AfterEach
    public void tearDown() {
        service.shutDown();
    }

    @Test
    public void failingObserverDoesNotBlockErrorSignallingOfRemainingObservers() {
        when(burningManService.getActiveBurningManCandidates(BLOCK_HEIGHT))
                .thenThrow(new IllegalStateException("candidate lookup failed"));
        @SuppressWarnings("unchecked")
        StreamObserver<BurningmanBlockDto> failing = mock(StreamObserver.class);
        doThrow(new IllegalStateException("call already closed")).when(failing).onError(any());
        @SuppressWarnings("unchecked")
        StreamObserver<BurningmanBlockDto> healthy = mock(StreamObserver.class);
        // Registration order matters: the failing observer is signalled first.
        service.subscribe(BurningmanBlockSubscription.getDefaultInstance(), failing);
        service.subscribe(BurningmanBlockSubscription.getDefaultInstance(), healthy);

        service.onParseBlockCompleteAfterBatchProcessing(block);

        verify(healthy, timeout(2_000)).onError(any());
    }
}
