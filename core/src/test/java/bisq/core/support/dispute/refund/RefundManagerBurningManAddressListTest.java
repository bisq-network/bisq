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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.support.dispute.refund;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BurningManAddressListService;
import bisq.core.dao.burningman.BurningManService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundManagerBurningManAddressListTest {
    @Test
    void rejectsAddressListVersionZeroBeforeReconstructingReceivers() {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getLastBlock()).thenReturn(Optional.empty());
        BurningManService burningManService = mock(BurningManService.class);
        DelayedPayoutTxReceiverService receiverService = new DelayedPayoutTxReceiverService(daoStateService,
                burningManService,
                mock(BurningManAddressListService.class));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        RefundManager refundManager = refundManager(btcWalletService, receiverService);
        TransactionOutput depositOutput = mock(TransactionOutput.class);
        when(depositOutput.getValue()).thenReturn(Coin.valueOf(10_000));
        Transaction depositTx = mock(Transaction.class);
        when(depositTx.getOutput(0)).thenReturn(depositOutput);
        Contract contract = mock(Contract.class);
        when(contract.getBurningManAddressListVersion()).thenReturn(0);
        Dispute dispute = mock(Dispute.class);
        when(dispute.findDepositTx(btcWalletService)).thenReturn(Optional.of(depositTx));
        when(dispute.getBurningManSelectionHeight()).thenReturn(767_950);
        when(dispute.getContract()).thenReturn(contract);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDelayedPayoutTxReceivers(mock(Transaction.class), dispute));

        verify(burningManService, never()).getActiveBurningManCandidates(767_950);
    }

    private static RefundManager refundManager(BtcWalletService btcWalletService,
                                               DelayedPayoutTxReceiverService receiverService) {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        return new RefundManager(p2PService,
                mock(TradeWalletService.class),
                btcWalletService,
                mock(WalletsSetup.class),
                mock(TradeManager.class),
                mock(ClosedTradableManager.class),
                mock(FailedTradesManager.class),
                mock(OpenOfferManager.class),
                mock(DaoFacade.class),
                receiverService,
                mock(KeyRing.class),
                mock(RefundDisputeListService.class),
                mock(Config.class),
                mock(PriceFeedService.class),
                mock(MempoolService.class));
    }
}
