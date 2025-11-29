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

import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.reputation.BondedReputation;
import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.governance.proofofburn.ProofOfBurnService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.util.ExecutorFactory;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.grpc.dto.BondedReputationDto;
import bisq.bridge.grpc.dto.BsqBlockDto;
import bisq.bridge.grpc.dto.ProofOfBurnDto;
import bisq.bridge.grpc.dto.TxDto;
import bisq.bridge.grpc.messages.BsqBlocksRequest;
import bisq.bridge.grpc.messages.BsqBlocksResponse;
import bisq.bridge.protobuf.BsqBlockGrpcServiceGrpc;

@Slf4j
public class BsqBlockGrpcService extends BsqBlockGrpcServiceGrpc.BsqBlockGrpcServiceImplBase implements DaoStateListener {
    private static final int MIN_BONDED_REPUTATION_LOCK_TIME = 50_000;

    private final DaoStateService daoStateService;
    private final BondedReputationRepository bondedReputationRepository;
    private final ExecutorService notifyObserversExecutor, requestBlocksExecutor;
    private final Set<ManagedStreamObserver<bisq.bridge.protobuf.BsqBlockDto>> streamObservers = new CopyOnWriteArraySet<>();

    @Inject
    public BsqBlockGrpcService(DaoStateService daoStateService,
                               BondedReputationRepository bondedReputationRepository) {
        this.daoStateService = daoStateService;
        this.bondedReputationRepository = bondedReputationRepository;

        daoStateService.addDaoStateListener(this);

        notifyObserversExecutor = ExecutorFactory.newSingleThreadExecutor("BsqBlockGrpcService.notifyObservers");
        requestBlocksExecutor = ExecutorFactory.newSingleThreadExecutor("BsqBlockGrpcService.requestBsqBlocks");
    }

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        log.info("onParseBlockCompleteAfterBatchProcessing");
        if (streamObservers.isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, BondedReputation> bondedReputationByLockupTxId = getBondedReputationByLockupTxId();
                Map<String, BondedReputation> bondedReputationByUnlockTxId = getBondedReputationByUnlockTxId();
                BsqBlockDto bsqBlockDto = toBlockDto(block, bondedReputationByLockupTxId, bondedReputationByUnlockTxId);
                if (bsqBlockDto.getTxDtoList().isEmpty()) {
                    log.info("No relevant BSQ transactions in that block, therefor we skip publishing. bsqBlockDto={}", bsqBlockDto);
                    return;
                }

                log.info("Notify observers of new bsqBlockDto: {}", bsqBlockDto);
                var responseProto = bsqBlockDto.toProtoMessage();
                streamObservers.forEach(observer -> {
                    try {
                        observer.onNext(responseProto);
                    } catch (Exception e) {
                        log.error("Failed to notify observer", e);
                        notifyOnError(observer, e);
                    }
                });
            } catch (Exception e) {
                log.error("Error at processing new bsqBlockDto", e);
                streamObservers.forEach(observer -> notifyOnError(observer, e));
            }
        }, notifyObserversExecutor);
    }


    @Override
    public void subscribe(bisq.bridge.protobuf.BsqBlockSubscription subscription,
                          StreamObserver<bisq.bridge.protobuf.BsqBlockDto> streamObserver) {
        log.info("subscribe streamObserver {}", streamObserver);
        var managedStreamObserver = new ManagedStreamObserver<>(streamObserver, streamObservers::remove, streamObservers::remove);
        streamObservers.add(managedStreamObserver);
    }

    @Override
    public void requestBsqBlocks(bisq.bridge.protobuf.BsqBlocksRequest bsqBlocksRequest,
                                 StreamObserver<bisq.bridge.protobuf.BsqBlocksResponse> streamObserver) {
        CompletableFuture.runAsync(() -> {
            try {
                if (!daoStateService.isParseBlockChainComplete()) {
                    log.warn("Request rejected because blockchain parsing is not completed yet. Chain height={}", daoStateService.getChainHeight());
                    streamObserver.onError(Status.FAILED_PRECONDITION
                            .withDescription("Blockchain parsing is not completed yet. Chain height=" + daoStateService.getChainHeight())
                            .asRuntimeException());
                    return;
                }

                long ts = System.currentTimeMillis();
                int startBlockHeight = BsqBlocksRequest.fromProto(bsqBlocksRequest).getStartBlockHeight();
                log.info("Request Bsq blocks from block {}", startBlockHeight);

                Map<String, BondedReputation> bondedReputationByLockupTxId = getBondedReputationByLockupTxId();
                Map<String, BondedReputation> bondedReputationByUnlockTxId = getBondedReputationByUnlockTxId();
                List<BsqBlockDto> blocks = daoStateService.getBlocksFromBlockHeight(startBlockHeight).stream()
                        .map(block -> toBlockDto(block, bondedReputationByLockupTxId, bondedReputationByUnlockTxId))
                        .filter(dto -> !dto.getTxDtoList().isEmpty())
                        .collect(Collectors.toList());

                // About 108 ms for 703 BsqBlocks, responseProto is about 73kb
                log.info("Creating {} BsqBlocks took {} ms", blocks.size(), System.currentTimeMillis() - ts);
                var responseProto = new BsqBlocksResponse(blocks).toProtoMessage();
                streamObserver.onNext(responseProto);
                streamObserver.onCompleted();
            } catch (Exception e) {
                log.error("Error at processing blocks", e);
                notifyOnError(streamObserver, e);
            }
        }, requestBlocksExecutor);
    }

    public void shutDown() {
        daoStateService.removeDaoStateListener(this);

        ExecutorFactory.shutdownAndAwaitTermination(notifyObserversExecutor, 1000);
        ExecutorFactory.shutdownAndAwaitTermination(requestBlocksExecutor, 1000);

        streamObservers.clear();
    }

    private BsqBlockDto toBlockDto(Block block,
                                   Map<String, BondedReputation> bondedReputationByLockupTxId,
                                   Map<String, BondedReputation> bondedReputationByUnlockTxId) {
        try {
            List<TxDto> txDataList = block.getTxs().stream()
                    .map(tx -> {
                        Optional<ProofOfBurnDto> proofOfBurnDto = toProofOfBurnDto(tx);
                        Optional<BondedReputationDto> bondedReputationDto = toBondedReputationDto(tx, bondedReputationByLockupTxId, bondedReputationByUnlockTxId);
                        if (proofOfBurnDto.isPresent() || bondedReputationDto.isPresent()) {
                            return new TxDto(tx.getId(), proofOfBurnDto, bondedReputationDto);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return new BsqBlockDto(block.getHeight(),
                    block.getTime(),
                    txDataList);
        } catch (Exception e) {
            log.error("toBlockDto failed", e);
            throw e;
        }
    }

    private Optional<ProofOfBurnDto> toProofOfBurnDto(Tx tx) {
        try {
            if (tx.getTxType() == TxType.PROOF_OF_BURN) {
                byte[] proofOfBurnHash = ProofOfBurnService.getHashFromOpReturnData(tx);
                return Optional.of(new ProofOfBurnDto(tx.getBurntBsq(), proofOfBurnHash));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("toProofOfBurnDto failed", e);
            throw e;
        }
    }

    private Optional<BondedReputationDto> toBondedReputationDto(Tx tx,
                                                                Map<String, BondedReputation> bondedReputationByLockupTxId,
                                                                Map<String, BondedReputation> bondedReputationByUnlockTxId) {
        try {
            if (tx.getTxType() == TxType.LOCKUP) {
                return Optional.ofNullable(bondedReputationByLockupTxId.get(tx.getId()))
                        .map(bondedReputation -> new BondedReputationDto(bondedReputation.getAmount(),
                                bondedReputation.getBondedAsset().getHash(),
                                bondedReputation.getLockTime(),
                                false));
            } else if (tx.getTxType() == TxType.UNLOCK) {
                return Optional.ofNullable(bondedReputationByUnlockTxId.get(tx.getId()))
                        .map(bondedReputation -> new BondedReputationDto(bondedReputation.getAmount(),
                                bondedReputation.getBondedAsset().getHash(),
                                bondedReputation.getLockTime(),
                                true));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("toBondedReputationDto failed", e);
            throw e;
        }
    }

    private Map<String, BondedReputation> getBondedReputationByLockupTxId() {
        // We only consider lock time with at least 50 000 blocks as valid
        try {
            return bondedReputationRepository.getBondedReputationStream()
                    .filter(bondedReputation -> bondedReputation.getLockupTxId() != null)
                    .filter(bondedReputation -> bondedReputation.getLockTime() >= MIN_BONDED_REPUTATION_LOCK_TIME)
                    .collect(Collectors.toMap(Bond::getLockupTxId, bondedReputation -> bondedReputation));
        } catch (Exception e) {
            log.error("getBondedReputationByTxId failed", e);
            throw e;
        }
    }

    private Map<String, BondedReputation> getBondedReputationByUnlockTxId() {
        // We only consider lock time with at least 50 000 blocks as valid
        try {
            return bondedReputationRepository.getBondedReputationStream()
                    .filter(bondedReputation -> bondedReputation.getUnlockTxId() != null)
                    .filter(bondedReputation -> bondedReputation.getLockTime() >= MIN_BONDED_REPUTATION_LOCK_TIME)
                    .collect(Collectors.toMap(Bond::getUnlockTxId, bondedReputation -> bondedReputation));
        } catch (Exception e) {
            log.error("getBondedReputationByTxId failed", e);
            throw e;
        }
    }

    private void notifyOnError(StreamObserver<?> observer,
                               Exception exception) {
        observer.onError(Status.INTERNAL
                .withDescription("Error processing bsqBlock data")
                .withCause(exception)
                .asRuntimeException());
    }

}
