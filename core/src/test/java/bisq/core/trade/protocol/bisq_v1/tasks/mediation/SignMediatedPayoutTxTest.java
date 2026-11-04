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

package bisq.core.trade.protocol.bisq_v1.tasks.mediation;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignMediatedPayoutTxTest {
    private static final RegTestParams PARAMS = RegTestParams.get();
    private static final String TRADE_ID = "trade-id";
    private static final ECKey BUYER_KEY = new ECKey();
    private static final ECKey SELLER_KEY = new ECKey();
    private static final Script REDEEM_SCRIPT = ScriptBuilder.createMultiSigOutputScript(2,
            List.of(SELLER_KEY, BUYER_KEY));
    private static final String BUYER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    private static final String SELLER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();

    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private final Trade trade = mock(Trade.class);
    private final ProcessModel processModel = mock(ProcessModel.class);
    private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
    private Transaction depositTx;
    private byte[] acceptedSignature;

    @BeforeEach
    void setUp() {
        depositTx = new Transaction(PARAMS);
        depositTx.addInput(new TransactionInput(PARAMS,
                depositTx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(120_000)));
        depositTx.addOutput(Coin.valueOf(120_000), ScriptBuilder.createP2WSHOutputScript(REDEEM_SCRIPT));

        Transaction acceptedPayout = new Transaction(PARAMS);
        acceptedPayout.addInput(depositTx.getOutput(0));
        acceptedPayout.addOutput(Coin.valueOf(80_000), Address.fromString(PARAMS, BUYER_ADDRESS));
        acceptedPayout.addOutput(Coin.valueOf(30_000), Address.fromString(PARAMS, SELLER_ADDRESS));
        Sha256Hash signatureHash = acceptedPayout.hashForWitnessSignature(0,
                REDEEM_SCRIPT,
                depositTx.getOutput(0).getValue(),
                Transaction.SigHash.ALL,
                false);
        acceptedSignature = BUYER_KEY.sign(signatureHash).encodeToDER();

        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        Offer offer = mock(Offer.class);
        Contract contract = mock(Contract.class);
        TradingPeer tradingPeer = new TradingPeer();
        tradingPeer.setPayoutAddressString(SELLER_ADDRESS);
        tradingPeer.setMultiSigPubKey(SELLER_KEY.getPubKey());
        AddressEntry payoutEntry = mock(AddressEntry.class);
        AddressEntry multisigEntry = mock(AddressEntry.class);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getDepositTx()).thenReturn(depositTx);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getAmount()).thenReturn(Coin.valueOf(80_000));
        when(trade.getContract()).thenReturn(contract);
        when(offer.getBuyerSecurityDeposit()).thenReturn(Coin.valueOf(10_000));
        when(offer.getSellerSecurityDeposit()).thenReturn(Coin.valueOf(20_000));
        when(contract.isMyRoleBuyer(any(PubKeyRing.class))).thenReturn(true);
        when(processModel.getPubKeyRing()).thenReturn(mock(PubKeyRing.class));
        when(processModel.getTradePeer()).thenReturn(tradingPeer);
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(processModel.getMyMultiSigPubKey()).thenReturn(BUYER_KEY.getPubKey());
        when(processModel.getMediatedPayoutTxSignature()).thenReturn(acceptedSignature);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        when(btcWalletService.getOrCreateAddressEntry(TRADE_ID, AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(payoutEntry);
        when(btcWalletService.getOrCreateAddressEntry(TRADE_ID, AddressEntry.Context.MULTI_SIG))
                .thenReturn(multisigEntry);
        when(payoutEntry.getAddressString()).thenReturn(BUYER_ADDRESS);
        when(multisigEntry.getPubKey()).thenReturn(BUYER_KEY.getPubKey());
    }

    @SuppressWarnings("unchecked")
    private void runTask() {
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage::set);
        taskRunner.addTasks(SignMediatedPayoutTx.class);
        taskRunner.run();
    }

    @Test
    void reusesSignatureForTheAcceptedPayout() throws Exception {
        when(processModel.getBuyerPayoutAmountFromMediation()).thenReturn(80_000L);
        when(processModel.getSellerPayoutAmountFromMediation()).thenReturn(30_000L);

        runTask();

        assertTrue(completed.get());
        assertNull(errorMessage.get());
        verify(processModel, never()).setMediatedPayoutTxSignature(any());
        verify(tradeWalletService, never()).signMediatedPayoutTx(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsSignatureFromEarlierPayoutWithoutSigningTheReplacement() throws Exception {
        when(processModel.getBuyerPayoutAmountFromMediation()).thenReturn(70_000L);
        when(processModel.getSellerPayoutAmountFromMediation()).thenReturn(40_000L);

        runTask();

        assertFalse(completed.get());
        assertTrue(errorMessage.get().contains("mediatedPayoutTxSignature is not valid for the expected payout tx"));
        verify(processModel, never()).setMediatedPayoutTxSignature(any());
        verify(tradeWalletService, never()).signMediatedPayoutTx(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
