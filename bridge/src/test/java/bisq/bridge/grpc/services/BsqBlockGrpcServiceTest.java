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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;



import bisq.bridge.protobuf.BsqBlockDto;
import bisq.bridge.protobuf.BsqBlockSubscription;

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
}
