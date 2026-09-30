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

package bisq.core.dao;

import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.user.Preferences;

import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.app.Version;

import org.bitcoinj.core.Transaction;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.mock;

/** Real checkpoint detection and readiness, with all external effects mocked. */
public final class DaoCheckpointTestFixture {
    public static final int CHECKPOINT_HEIGHT = 572000;

    public final DaoStateService daoStateService =
            new DaoStateService(new DaoState(), mock(GenesisTxInfo.class), null);
    public final DaoStateStorageService storage = mock(DaoStateStorageService.class);
    public final DaoStateMonitoringService monitor = new DaoStateMonitoringService(
            daoStateService, storage, mock(DaoStateNetworkService.class), mock(GenesisTxInfo.class),
            mock(SeedNodeRepository.class), mock(Preferences.class), null, false, false);
    public final DaoFacade facade = new DaoFacade(
            null, null, null, null, null, daoStateService, monitor, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, storage);

    public DaoCheckpointTestFixture() {
        monitor.addListeners();
        daoStateService.onParseBlockChainComplete();
    }

    // Adds the tx to the DAO state as a parsed BSQ tx with the given output types. As the DAO parser does, only BSQ
    // outputs become unspent tx outputs. The tx gets a block at the genesis height, so add only one tx per fixture.
    public void addParsedTx(Transaction transaction, TxOutputType... outputTypes) {
        int height = daoStateService.getGenesisBlockHeight();
        String txId = transaction.getTxId().toString();
        protobuf.Tx.Builder tx = protobuf.Tx.newBuilder().setTxType(TxType.TRANSFER_BSQ.toProtoMessage());
        for (int i = 0; i < outputTypes.length; i++) {
            tx.addTxOutputs(protobuf.BaseTxOutput.newBuilder()
                    .setIndex(i)
                    .setValue(transaction.getOutput(i).getValue().value)
                    .setTxId(txId)
                    .setBlockHeight(height)
                    .setTxOutput(protobuf.TxOutput.newBuilder()
                            .setTxOutputType(outputTypes[i].toProtoMessage())
                            .setLockTime(-1)));
        }
        Tx daoTx = Tx.fromProto(protobuf.BaseTx.newBuilder()
                .setTxVersion(Version.BSQ_TX_VERSION)
                .setId(txId)
                .setBlockHeight(height)
                .setBlockHash("block")
                .setTx(tx)
                .build());
        Block block = new Block(height, 0, "block", "previous-block");
        daoStateService.onNewBlockHeight(height);
        daoStateService.onNewBlockWithEmptyTxs(block);
        daoStateService.onNewTxForLastBlock(block, daoTx);
        daoTx.getTxOutputs().stream()
                .filter(daoStateService::isBsqTxOutputType)
                .forEach(daoStateService::addUnspentTxOutput);
    }

    public void failCheckpoint() {
        monitor.applySnapshot(new LinkedList<>(List.of(
                new DaoStateHash(CHECKPOINT_HEIGHT, new byte[20], true))));
        daoStateService.onParseBlockChainComplete();
    }
}
