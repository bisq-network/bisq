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

package bisq.core.trade.protocol.bisq_v1.tasks.maker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.PreparedDepositTxAndMakerInputs;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker.SellerAsMakerCreatesUnsignedDepositTx;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MakerCreatesDepositTxTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String OFFER_ID = "offer-id";
    private static final String MAKER_FEE_TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Coin BUYER_SECURITY_DEPOSIT = Coin.valueOf(60_000);
    private static final Coin SELLER_SECURITY_DEPOSIT = Coin.valueOf(60_000);
    private static final Coin OFFER_AMOUNT = Coin.valueOf(300_000);
    private static final Coin TRADE_AMOUNT = Coin.valueOf(200_000);
    private static final Coin TRADE_TX_FEE = Coin.valueOf(2_700);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void makerCompletesWithItsReservedFundsAsInputs(boolean makerIsBuyer) throws Exception {
        Fixture fixture = new Fixture(makerIsBuyer, reservedFunds(makerIsBuyer));

        TaskResult result = fixture.run();

        assertTrue(result.completed(), result.errorMessage());
        fixture.verifyDepositTxIsCreatedFromMakerFeeTx();
        verify(fixture.processModel).setPreparedDepositTx(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void makerFailsBeforeSendingInputsWhichAreNotItsReservedFunds(boolean makerIsBuyer) throws Exception {
        Fixture fixture = new Fixture(makerIsBuyer, reservedFunds(makerIsBuyer).add(Coin.valueOf(5_321_500)));

        TaskResult result = fixture.run();

        assertFalse(result.completed());
        assertTrue(result.errorMessage().contains("Maker input value mismatch"), result.errorMessage());
        verify(fixture.processModel, never()).setPreparedDepositTx(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void makerFailsWithoutItsMakerFeeTx(boolean makerIsBuyer) throws Exception {
        Fixture fixture = new Fixture(makerIsBuyer, reservedFunds(makerIsBuyer));
        when(fixture.walletService.getTransaction(MAKER_FEE_TX_ID)).thenReturn(null);

        TaskResult result = fixture.run();

        assertFalse(result.completed());
        assertTrue(result.errorMessage().contains("makerFeeTx must not be null"), result.errorMessage());
        verify(fixture.processModel, never()).setPreparedDepositTx(any());
    }

    // The taker expects the inputs of the maker to be the reserved funds of the offer
    private static Coin reservedFunds(boolean makerIsBuyer) {
        return makerIsBuyer ? BUYER_SECURITY_DEPOSIT : SELLER_SECURITY_DEPOSIT.add(OFFER_AMOUNT);
    }

    private static List<RawTransactionInput> rawInputs(Coin value) {
        Transaction parentTx = new Transaction(PARAMS);
        parentTx.addInput(Sha256Hash.of(new ECKey().getPubKey()), 0, ScriptBuilder.createEmpty());
        parentTx.addOutput(value, SegwitAddress.fromKey(PARAMS, new ECKey()));
        return List.of(new RawTransactionInput(new Transaction(PARAMS).addInput(parentTx.getOutput(0))));
    }

    private static class Fixture {
        private final boolean makerIsBuyer;
        private final ProcessModel processModel = mock(ProcessModel.class);
        private final BtcWalletService walletService = mock(BtcWalletService.class);
        private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        private final Transaction makerFeeTx = new Transaction(PARAMS);
        private final Trade trade = mock(Trade.class);

        private Fixture(boolean makerIsBuyer, Coin makerInputValue) throws Exception {
            this.makerIsBuyer = makerIsBuyer;

            Offer offer = mock(Offer.class);
            when(offer.getId()).thenReturn(OFFER_ID);
            when(offer.getOfferFeePaymentTxId()).thenReturn(MAKER_FEE_TX_ID);
            when(offer.isBuyOffer()).thenReturn(makerIsBuyer);
            when(offer.getBuyerSecurityDeposit()).thenReturn(BUYER_SECURITY_DEPOSIT);
            when(offer.getSellerSecurityDeposit()).thenReturn(SELLER_SECURITY_DEPOSIT);
            when(offer.getAmount()).thenReturn(OFFER_AMOUNT);

            when(walletService.getTransaction(MAKER_FEE_TX_ID)).thenReturn(makerFeeTx);
            when(walletService.getTxFromSerializedTx(any()))
                    .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));
            when(walletService.isP2WPKH(any())).thenReturn(true);
            byte[] myMultiSigPubKey = new ECKey().getPubKey();
            AddressEntry multiSigAddressEntry = mock(AddressEntry.class);
            when(multiSigAddressEntry.getPubKey()).thenReturn(myMultiSigPubKey);
            when(walletService.getAddressEntry(OFFER_ID, AddressEntry.Context.MULTI_SIG))
                    .thenReturn(Optional.of(multiSigAddressEntry));
            AddressEntry addressEntry = mock(AddressEntry.class);
            when(addressEntry.getAddress()).thenReturn(SegwitAddress.fromKey(PARAMS, new ECKey()));
            when(walletService.getOrCreateAddressEntry(OFFER_ID, AddressEntry.Context.RESERVED_FOR_TRADE))
                    .thenReturn(addressEntry);
            when(walletService.getFreshAddressEntry()).thenReturn(addressEntry);

            TradingPeer tradingPeer = new TradingPeer();
            tradingPeer.setMultiSigPubKey(new ECKey().getPubKey());
            Coin takersInputAmount = makerIsBuyer ?
                    SELLER_SECURITY_DEPOSIT.add(TRADE_AMOUNT).add(TRADE_TX_FEE.multiply(2)) :
                    BUYER_SECURITY_DEPOSIT.add(TRADE_TX_FEE.multiply(2));
            tradingPeer.setRawTransactionInputs(rawInputs(takersInputAmount));

            PreparedDepositTxAndMakerInputs depositTxAndMakerInputs = new PreparedDepositTxAndMakerInputs(
                    new ArrayList<>(rawInputs(makerInputValue)),
                    new byte[]{});
            when(tradeWalletService.buyerAsMakerCreatesAndSignsDepositTx(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(depositTxAndMakerInputs);
            when(tradeWalletService.sellerAsMakerCreatesDepositTx(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(depositTxAndMakerInputs);

            when(processModel.getOffer()).thenReturn(offer);
            when(processModel.getBtcWalletService()).thenReturn(walletService);
            when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
            when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
            when(processModel.getTradePeer()).thenReturn(tradingPeer);
            when(processModel.getMyMultiSigPubKey()).thenReturn(myMultiSigPubKey);

            when(trade.getProcessModel()).thenReturn(processModel);
            when(trade.getOffer()).thenReturn(offer);
            when(trade.getAmount()).thenReturn(TRADE_AMOUNT);
            when(trade.getTradeTxFee()).thenReturn(TRADE_TX_FEE);
            when(trade.getContractAsJson()).thenReturn("{}");
        }

        private void verifyDepositTxIsCreatedFromMakerFeeTx() throws Exception {
            if (makerIsBuyer) {
                verify(tradeWalletService).buyerAsMakerCreatesAndSignsDepositTx(same(makerFeeTx),
                        any(), any(), any(), any(), any(), any(), any());
            } else {
                verify(tradeWalletService).sellerAsMakerCreatesDepositTx(same(makerFeeTx),
                        any(), any(), any(), any(), any(), any(), any());
            }
        }

        @SuppressWarnings("unchecked")
        private TaskResult run() {
            AtomicBoolean completed = new AtomicBoolean();
            AtomicReference<String> errorMessage = new AtomicReference<>();
            TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                    (Class<TradeModel>) (Class<?>) Trade.class,
                    () -> completed.set(true),
                    errorMessage::set);
            if (makerIsBuyer) {
                taskRunner.addTasks(BuyerAsMakerCreatesAndSignsDepositTx.class);
            } else {
                taskRunner.addTasks(SellerAsMakerCreatesUnsignedDepositTx.class);
            }
            taskRunner.run();
            return new TaskResult(completed.get(), errorMessage.get());
        }
    }

    private record TaskResult(boolean completed, String errorMessage) {
    }
}
