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
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.util.ExecutorFactory;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.grpc.dto.BurningmanBlockDto;
import bisq.bridge.grpc.dto.BurningmanDto;
import bisq.bridge.protobuf.BurningmanGrpcServiceGrpc;

@Slf4j
public class BurningmanGrpcService extends BurningmanGrpcServiceGrpc.BurningmanGrpcServiceImplBase implements DaoStateListener {
    private static final int NUM_PAST_BLOCKS = 10000; // Approximately 70 days of blocks

    private final DaoStateService daoStateService;
    private final BurningManService burningManService;
    private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
    private final ExecutorService notifyObserversExecutor, requestBlocksExecutor;
    private final Set<ManagedStreamObserver<bisq.bridge.protobuf.BurningmanBlockDto>> streamObservers = new CopyOnWriteArraySet<>();
    private final Object snapshotLock = new Object();
    private int snapshotHeight = -1;

    @Inject
    public BurningmanGrpcService(DaoStateService daoStateService,
                                 BurningManService burningManService,
                                 DelayedPayoutTxReceiverService delayedPayoutTxReceiverService) {
        this.daoStateService = daoStateService;
        this.burningManService = burningManService;
        this.delayedPayoutTxReceiverService = delayedPayoutTxReceiverService;

        daoStateService.addDaoStateListener(this);

        notifyObserversExecutor = ExecutorFactory.newSingleThreadExecutor("BurningmanGrpcService.notifyObservers");
        requestBlocksExecutor = ExecutorFactory.newSingleThreadExecutor("BurningmanGrpcService.requestBurningmanBlocks");
    }

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        log.info("onParseBlockCompleteAfterBatchProcessing");
        if (streamObservers.isEmpty()) {
            return;
        }

        int burningManSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight(block.getHeight());
        synchronized (snapshotLock) {
            if (snapshotHeight == burningManSelectionHeight) {
                return;
            }

            snapshotHeight = burningManSelectionHeight;
        }

        // We got a new snapshot height and notify our observer.
        // The block height is the last mod(10) height from the range of the last 10-20 blocks (139 -> 120; 140 -> 130, 141 -> 130).
        // This ensures that we have not to deal with re-orgs as it is expected that re-orgs never are that
        // deep (about 3-4 blocks have been historically deepest reorgs).
        daoStateService.getBlockAtHeight(snapshotHeight).ifPresent(blockAtSnapshotHeight -> {
            CompletableFuture.runAsync(() -> {
                try {
                    BurningmanBlockDto burningManBlockDto = toBurningmanBlockDto(blockAtSnapshotHeight);
                    log.info("Notify observers of new burningManBlockDto: {}", burningManBlockDto);
                    var responseProto = burningManBlockDto.toProtoMessage();
                    streamObservers.forEach(observer -> {
                        try {
                            observer.onNext(responseProto);
                        } catch (Exception e) {
                            if (e instanceof StatusRuntimeException &&
                                    ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.CANCELLED) {
                                log.debug("Observer cancelled; removing from subscribers.");
                                streamObservers.remove(observer);
                            } else {
                                log.error("Failed to notify observer", e);
                                notifyOnError(observer, e);
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Error at processing new burningManBlockDto", e);
                    streamObservers.forEach(observer -> notifyOnError(observer, e));
                }
            }, notifyObserversExecutor);
        });
    }

    @Override
    public void subscribe(bisq.bridge.protobuf.BurningmanBlockSubscription subscription,
                          StreamObserver<bisq.bridge.protobuf.BurningmanBlockDto> streamObserver) {
        log.info("subscribe streamObserver {}", streamObserver);
        var managedStreamObserver = new ManagedStreamObserver<>(streamObserver, streamObservers::remove, streamObservers::remove);
        streamObservers.add(managedStreamObserver);
    }

    @Override
    public void requestBurningmanBlocks(bisq.bridge.protobuf.BurningmanBlocksRequest burningmanBlocksRequest,
                                        StreamObserver<bisq.bridge.protobuf.BurningmanBlocksResponse> streamObserver) {
        io.grpc.Context context = io.grpc.Context.current();
        CompletableFuture.runAsync(context.wrap(() -> {
            try {
                if (context.isCancelled()) {
                    log.warn("requestBurningmanBlocks cancelled by client before processing.");
                    return;
                }
                if (!daoStateService.isParseBlockChainComplete()) {
                    log.warn("Request rejected because blockchain parsing is not completed yet. Chain height={}", daoStateService.getChainHeight());
                    streamObserver.onError(Status.FAILED_PRECONDITION
                            .withDescription("Blockchain parsing is not completed yet. Chain height=" + daoStateService.getChainHeight())
                            .asRuntimeException());
                    return;
                }

                long ts = System.currentTimeMillis();
                int chainHeight = daoStateService.getChainHeight();
                int genesisHeight = daoStateService.getGenesisBlockHeight();
                int startBlockHeight = Math.max(genesisHeight, chainHeight - NUM_PAST_BLOCKS);
                // We only provide blocks with heights which can be a snapshot height (mod 10)
                List<BurningmanBlockDto> blocks = daoStateService.getBlocksFromBlockHeight(startBlockHeight).stream()
                        .filter(block -> BurningmanRetention.includeBlock(chainHeight, block.getHeight()))
                        .map(this::toBurningmanBlockDto)
                        .collect(Collectors.toList());
                // Takes about 13 sec for 28 items, size about 43 KB
                log.info("Creating {} BurningmanBlockDto blocks took {} ms", blocks.size(), System.currentTimeMillis() - ts);
                var responseProto = new bisq.bridge.grpc.messages.BurningmanBlocksResponse(blocks).toProtoMessage();

                if (context.isCancelled()) {
                    log.warn("requestBurningmanBlocks cancelled by client before sending response");
                    return;
                }

                streamObserver.onNext(responseProto);
                streamObserver.onCompleted();
            } catch (Exception e) {
                log.error("Error at processing burningman blocks", e);
                notifyOnError(streamObserver, e);
            }
        }), requestBlocksExecutor);
    }

    public void shutDown() {
        daoStateService.removeDaoStateListener(this);

        ExecutorFactory.shutdownAndAwaitTermination(notifyObserversExecutor, 1000);
        ExecutorFactory.shutdownAndAwaitTermination(requestBlocksExecutor, 1000);

        streamObservers.clear();
    }

    private BurningmanBlockDto toBurningmanBlockDto(Block block) {
        try {
            List<BurningmanDto> burningmanDtoList = toBurningmanDtoList(block);
            return new BurningmanBlockDto(block.getHeight(), burningmanDtoList);
        } catch (Exception e) {
            log.error("toBurningmanBlockDto failed", e);
            throw e;
        }
    }

    private List<BurningmanDto> toBurningmanDtoList(Block block) {
        try {
            return burningManService.getActiveBurningManCandidates(block.getHeight(), true).stream()
                    .filter(burningManCandidate -> burningManCandidate.getReceiverAddress().isPresent())
                    .map(burningManCandidate -> {
                        String receiverAddress = burningManCandidate.getReceiverAddress().orElseThrow();
                        double cappedBurnAmountShare = burningManCandidate.getCappedBurnAmountShare();
                        return new BurningmanDto(receiverAddress, cappedBurnAmountShare);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("toBurningmanDtoList failed", e);
            throw e;
        }
    }

    private void notifyOnError(StreamObserver<?> observer,
                               Exception exception) {
        observer.onError(Status.INTERNAL
                .withDescription("Error processing burningman data")
                .withCause(exception)
                .asRuntimeException());
    }
}
