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

import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



import bisq.bridge.protobuf.BsqBlockDto;
import bisq.bridge.protobuf.BsqBlockSubscription;
import bisq.bridge.protobuf.BsqBlockSubscriptionEvent;
import bisq.bridge.protobuf.BsqBlocksRequest;
import bisq.bridge.protobuf.BsqBlocksResponse;

public class BsqBlockGrpcServiceTest {
    private DaoStateService daoStateService;
    private BsqBlockGrpcService service;

    @BeforeEach
    public void setUp() {
        daoStateService = mock(DaoStateService.class);
        BondedReputationRepository bondedReputationRepository = mock(BondedReputationRepository.class);
        when(bondedReputationRepository.getBondedReputationStream()).thenAnswer(invocation -> Stream.empty());
        service = new BsqBlockGrpcService(daoStateService, bondedReputationRepository);
    }

    @AfterEach
    public void tearDown() {
        service.shutDown();
    }

    @Test
    public void liveSubscriptionReceivesBlocksWithoutExportedTransactions() {
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(941_123);
        when(block.getTime()).thenReturn(1_723_000_000L);
        when(block.getTxs()).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        StreamObserver<BsqBlockDto> streamObserver = mock(StreamObserver.class);
        service.subscribe(BsqBlockSubscription.getDefaultInstance(), streamObserver);

        service.onParseBlockCompleteAfterBatchProcessing(block);

        ArgumentCaptor<BsqBlockDto> blockCaptor = ArgumentCaptor.forClass(BsqBlockDto.class);
        verify(streamObserver, timeout(2_000)).onNext(blockCaptor.capture());
        BsqBlockDto publishedBlock = blockCaptor.getValue();
        assertEquals(941_123, publishedBlock.getHeight());
        assertEquals(1_723_000_000L, publishedBlock.getTime());
        assertTrue(publishedBlock.getTxDtoList().isEmpty());
    }

    @Test
    public void failingObserverIsDeregisteredAndDoesNotBlockRemainingObservers() {
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(941_123);
        when(block.getTime()).thenReturn(1_723_000_000L);
        when(block.getTxs()).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        StreamObserver<BsqBlockDto> failing = mock(StreamObserver.class);
        doThrow(new IllegalStateException("stream broken")).when(failing).onNext(any());
        doThrow(new IllegalStateException("call already closed")).when(failing).onError(any());
        @SuppressWarnings("unchecked")
        StreamObserver<BsqBlockDto> healthy = mock(StreamObserver.class);
        // Registration order matters: the failing observer is notified first.
        service.subscribe(BsqBlockSubscription.getDefaultInstance(), failing);
        service.subscribe(BsqBlockSubscription.getDefaultInstance(), healthy);

        service.onParseBlockCompleteAfterBatchProcessing(block);
        verify(healthy, timeout(2_000)).onNext(any());

        // The failing observer was deregistered even though its onError threw, so a second block skips it.
        service.onParseBlockCompleteAfterBatchProcessing(block);
        verify(healthy, timeout(2_000).times(2)).onNext(any());
        verify(failing, times(1)).onNext(any());
    }

    @Test
    public void snapshotSubscriptionAcknowledgesRegistrationBeforePublishingBlocks() {
        when(daoStateService.getChainHeight()).thenReturn(941_122);
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(941_123);
        when(block.getTime()).thenReturn(1_723_000_000L);
        when(block.getTxs()).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        StreamObserver<BsqBlockSubscriptionEvent> streamObserver = mock(StreamObserver.class);

        service.subscribeWithSnapshot(BsqBlockSubscription.getDefaultInstance(), streamObserver);
        service.onParseBlockCompleteAfterBatchProcessing(block);

        ArgumentCaptor<BsqBlockSubscriptionEvent> eventCaptor = ArgumentCaptor.forClass(BsqBlockSubscriptionEvent.class);
        verify(streamObserver, timeout(2_000).times(2)).onNext(eventCaptor.capture());
        List<BsqBlockSubscriptionEvent> events = eventCaptor.getAllValues();
        assertEquals(BsqBlockSubscriptionEvent.PayloadCase.SUBSCRIPTIONREADYHEIGHT, events.get(0).getPayloadCase());
        assertEquals(941_122, events.get(0).getSubscriptionReadyHeight());
        assertEquals(BsqBlockSubscriptionEvent.PayloadCase.BSQBLOCK, events.get(1).getPayloadCase());
        assertEquals(941_123, events.get(1).getBsqBlock().getHeight());
    }

    @Test
    public void snapshotSubscriptionQueuesReadyEventBeforeConcurrentBlockPublication() throws Exception {
        CountDownLatch registeredButReadyEventNotQueued = new CountDownLatch(1);
        CountDownLatch releaseChainHeightRead = new CountDownLatch(1);
        when(daoStateService.getChainHeight()).thenAnswer(invocation -> {
            registeredButReadyEventNotQueued.countDown();
            if (!releaseChainHeightRead.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Chain height read was never released");
            }
            return 941_122;
        });
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(941_123);
        when(block.getTime()).thenReturn(1_723_000_000L);
        when(block.getTxs()).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        StreamObserver<BsqBlockSubscriptionEvent> streamObserver = mock(StreamObserver.class);

        AtomicReference<Throwable> subscriberFailure = new AtomicReference<>();
        AtomicReference<Throwable> publisherFailure = new AtomicReference<>();
        Thread subscriber = new Thread(() ->
                service.subscribeWithSnapshot(BsqBlockSubscription.getDefaultInstance(), streamObserver));
        subscriber.setUncaughtExceptionHandler((thread, throwable) -> subscriberFailure.set(throwable));
        subscriber.start();
        assertTrue(registeredButReadyEventNotQueued.await(5, TimeUnit.SECONDS));

        // The observer is registered but its ready event is not queued yet, which is the window in which a block
        // must not be queued ahead of it.
        Thread publisher = new Thread(() -> service.onParseBlockCompleteAfterBatchProcessing(block));
        publisher.setUncaughtExceptionHandler((thread, throwable) -> publisherFailure.set(throwable));
        publisher.start();
        awaitContendedOrDone(publisher);
        releaseChainHeightRead.countDown();
        subscriber.join(5_000);
        publisher.join(5_000);
        assertNull(subscriberFailure.get());
        assertNull(publisherFailure.get());

        ArgumentCaptor<BsqBlockSubscriptionEvent> eventCaptor = ArgumentCaptor.forClass(BsqBlockSubscriptionEvent.class);
        verify(streamObserver, timeout(2_000).times(2)).onNext(eventCaptor.capture());
        List<BsqBlockSubscriptionEvent> events = eventCaptor.getAllValues();
        assertEquals(BsqBlockSubscriptionEvent.PayloadCase.SUBSCRIPTIONREADYHEIGHT, events.get(0).getPayloadCase());
        assertEquals(BsqBlockSubscriptionEvent.PayloadCase.BSQBLOCK, events.get(1).getPayloadCase());
    }

    // The publisher either contends on the subscription lock or, if that ordering is not enforced, runs to
    // completion. Both are terminal for this test, so we gate on the state instead of timing the handover.
    private static void awaitContendedOrDone(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.BLOCKED || state == Thread.State.TERMINATED) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Publisher thread neither blocked nor completed");
    }

    @Test
    public void historicalResponseReportsTheCompleteSnapshotHeight() {
        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        when(daoStateService.getChainHeight()).thenReturn(941_124);
        when(daoStateService.getBlocksFromBlockHeight(941_000)).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        StreamObserver<BsqBlocksResponse> streamObserver = mock(StreamObserver.class);

        service.requestBsqBlocks(BsqBlocksRequest.newBuilder()
                .setStartBlockHeight(941_000)
                .build(), streamObserver);

        ArgumentCaptor<BsqBlocksResponse> responseCaptor = ArgumentCaptor.forClass(BsqBlocksResponse.class);
        verify(streamObserver, timeout(2_000)).onNext(responseCaptor.capture());
        assertEquals(941_124, responseCaptor.getValue().getSnapshotHeight());
        assertTrue(responseCaptor.getValue().getBsqBlocksList().isEmpty());
        verify(streamObserver, timeout(2_000)).onCompleted();
    }
}
