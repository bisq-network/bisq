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

package bisq.core.dao.burningman.accounting.node.lite;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.node.AccountingNode;
import bisq.core.dao.burningman.accounting.node.full.AccountingBlockParser;
import bisq.core.dao.burningman.accounting.node.lite.network.AccountingLiteNodeNetworkService;
import bisq.core.dao.burningman.accounting.node.messages.GetAccountingBlocksResponse;
import bisq.core.dao.state.DaoStateService;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Hex;

import org.bitcoinj.core.ECKey;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingLiteNodeTest {
    @Mock
    private P2PService p2PService;
    @Mock
    private DaoStateService daoStateService;
    @Mock
    private BurningManAccountingService accountingService;
    @Mock
    private AccountingBlockParser blockParser;
    @Mock
    private WalletsSetup walletsSetup;
    @Mock
    private BsqWalletService bsqWalletService;
    @Mock
    private AccountingLiteNodeNetworkService networkService;
    @Mock
    private Preferences preferences;

    private final DoubleProperty downloadPercentage = new SimpleDoubleProperty(0);

    @Test
    void waitsForWalletSyncAfterP2PStartup() {
        when(walletsSetup.downloadPercentageProperty()).thenReturn(downloadPercentage);
        when(walletsSetup.isDownloadComplete()).thenAnswer(invocation -> downloadPercentage.get() == 1);
        when(p2PService.isBootstrapped()).thenReturn(true);
        AccountingLiteNode node = createNode();

        node.onInitialDaoBlockParsingComplete();

        verify(networkService, never()).requestBlocks(anyInt());
        verify(accountingService, never()).onInitialBlockRequestsComplete();

        when(accountingService.getBlockHeightOfLastBlock()).thenReturn(100);
        when(bsqWalletService.getBestChainHeight()).thenReturn(200);
        downloadPercentage.set(1);

        verify(networkService).requestBlocks(101);
        verify(accountingService, never()).onInitialBlockRequestsComplete();
    }

    @Test
    void waitsForP2PStartupAfterWalletSync() {
        when(walletsSetup.downloadPercentageProperty()).thenReturn(downloadPercentage);
        when(walletsSetup.isDownloadComplete()).thenAnswer(invocation -> downloadPercentage.get() == 1);
        AccountingLiteNode node = createNode();

        node.onInitialDaoBlockParsingComplete();
        downloadPercentage.set(1);

        verify(networkService, never()).requestBlocks(anyInt());

        when(accountingService.getBlockHeightOfLastBlock()).thenReturn(100);
        when(bsqWalletService.getBestChainHeight()).thenReturn(200);
        node.onP2PNetworkReady();

        verify(networkService).requestBlocks(101);
    }

    @Test
    void startsImmediatelyWhenWalletAndP2PAreReady() {
        when(walletsSetup.downloadPercentageProperty()).thenReturn(downloadPercentage);
        when(walletsSetup.isDownloadComplete()).thenReturn(true);
        when(p2PService.isBootstrapped()).thenReturn(true);
        when(accountingService.getBlockHeightOfLastBlock()).thenReturn(100);
        when(bsqWalletService.getBestChainHeight()).thenReturn(200);

        createNode().onInitialDaoBlockParsingComplete();

        verify(networkService).requestBlocks(101);
    }

    @Test
    void completesWithoutRequestWhenWalletConfirmsCurrentHeight() {
        when(walletsSetup.downloadPercentageProperty()).thenReturn(downloadPercentage);
        when(walletsSetup.isDownloadComplete()).thenAnswer(invocation -> downloadPercentage.get() == 1);
        when(p2PService.isBootstrapped()).thenReturn(true);
        AccountingLiteNode node = createNode();

        node.onInitialDaoBlockParsingComplete();
        verify(accountingService, never()).onInitialBlockRequestsComplete();

        when(accountingService.getBlockHeightOfLastBlock()).thenReturn(200);
        when(bsqWalletService.getBestChainHeight()).thenReturn(200);
        downloadPercentage.set(1);

        verify(networkService, never()).requestBlocks(anyInt());
        verify(accountingService).onInitialBlockRequestsComplete();
    }

    @Test
    void partialResponseBeforeWalletSyncDoesNotCompleteInitialDownload() throws Exception {
        when(walletsSetup.downloadPercentageProperty()).thenReturn(downloadPercentage);
        when(walletsSetup.isDownloadComplete()).thenAnswer(invocation -> downloadPercentage.get() == 1);
        when(p2PService.isBootstrapped()).thenReturn(true);
        AccountingLiteNode node = createNode();
        node.onInitialDaoBlockParsingComplete();

        AccountingBlock block = new AccountingBlock(101, 1, new byte[4], new byte[4], List.of());
        List<AccountingBlock> blocks = List.of(block);
        byte[] signature = AccountingNode.getSignature(AccountingNode.getSha256Hash(blocks),
                ECKey.fromPrivate(Hex.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
        GetAccountingBlocksResponse response = new GetAccountingBlocksResponse(blocks, 1,
                DevEnv.getDEV_PRIVILEGE_PUB_KEY(), signature);
        when(accountingService.getBlockHeightOfLastBlock()).thenReturn(101);

        Executor previousExecutor = UserThread.getExecutor();
        BlockingQueue<Runnable> userActions = new LinkedBlockingQueue<>();
        try {
            UserThread.setExecutor(userActions::add);
            // A response can also arrive from the future-block retry while the wallet is still syncing.
            node.onRequestedBlocksReceived(response);
            Runnable onBlocksApplied = userActions.poll(5, TimeUnit.SECONDS);
            assertNotNull(onBlocksApplied);
            onBlocksApplied.run();

            verify(accountingService, never()).onInitialBlockRequestsComplete();
            verify(networkService, never()).requestBlocks(anyInt());

            when(bsqWalletService.getBestChainHeight()).thenReturn(200);
            downloadPercentage.set(1);

            verify(networkService).requestBlocks(102);
        } finally {
            UserThread.setExecutor(previousExecutor);
        }
    }

    private AccountingLiteNode createNode() {
        return new AccountingLiteNode(p2PService, daoStateService, accountingService, blockParser,
                walletsSetup, bsqWalletService, networkService, preferences, true);
    }
}
