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

package bisq.core.offer.bsq_swap;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoCheckpointTestFixture;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.filter.FilterPolicyService;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;

import com.google.common.collect.ImmutableList;

import javafx.beans.property.SimpleIntegerProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks that the seller of a BSQ swap does not count an unspent BSQ output as BTC: in the selection of its inputs, in
 * the missing funds of the take offer model and in the funded state of an open sell offer. It uses the real
 * BtcWalletService and coin selector over real transaction outputs, with the BSQ outputs held in a real DAO state. The
 * wallet is a mock which only returns the outputs as its spend candidates.
 */
class BsqSwapSellerInputSelectionTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final long TX_FEE_PER_VBYTE = 1;
    private static final long SELLERS_TRADE_FEE = 0;

    private final DaoCheckpointTestFixture dao = new DaoCheckpointTestFixture();

    @BeforeEach
    void setUp() {
        Context.propagate(new Context(PARAMS));
    }

    @Test
    void doesNotSelectAnUnspentBsqOutputInTheBtcWallet() throws Exception {
        // The BSQ output is older, so the coin selector would take it first
        TransactionOutput bsqOutput = confirmedTx(10, 200_000).getOutput(0);
        TransactionOutput btcOutput = confirmedTx(1, 300_000).getOutput(0);
        dao.addParsedTx(bsqOutput.getParentTransaction(), TxOutputType.BSQ_OUTPUT);

        List<TransactionOutPoint> selected = outpoints(select(100_000, bsqOutput, btcOutput));

        assertEquals(List.of(btcOutput.getOutPointFor()), selected);
    }

    @Test
    void selectsTheBtcOutputOfABsqTxButNotItsBsqOutput() throws Exception {
        // For example the BTC change of a tx which paid a trade fee in BSQ
        Transaction bsqTx = confirmedTx(10, 200_000, 150_000);
        dao.addParsedTx(bsqTx, TxOutputType.BSQ_OUTPUT, TxOutputType.BTC_OUTPUT);

        List<TransactionOutPoint> selected = outpoints(select(100_000, bsqTx.getOutput(0), bsqTx.getOutput(1)));

        assertEquals(List.of(bsqTx.getOutput(1).getOutPointFor()), selected);
    }

    @Test
    void selectsTheOlderOutputIfTheDaoStateDoesNotHoldItAsBsq() throws Exception {
        TransactionOutput olderOutput = confirmedTx(10, 200_000).getOutput(0);
        TransactionOutput newerOutput = confirmedTx(1, 300_000).getOutput(0);

        List<TransactionOutPoint> selected = outpoints(select(100_000, olderOutput, newerOutput));

        assertEquals(List.of(olderOutput.getOutPointFor()), selected);
    }

    @Test
    void reportsMissingFundsInsteadOfSelectingAnUnspentBsqOutput() {
        TransactionOutput bsqOutput = confirmedTx(10, 200_000).getOutput(0);
        TransactionOutput btcOutput = confirmedTx(1, 50_000).getOutput(0);
        dao.addParsedTx(bsqOutput.getParentTransaction(), TxOutputType.BSQ_OUTPUT);

        assertThrows(InsufficientMoneyException.class, () -> select(100_000, bsqOutput, btcOutput));
    }

    @Test
    void takeOfferModelReportsMissingFundsIfOnlyAnUnspentBsqOutputCoversTheAmount() {
        // The wallet balance of 250 000 would cover the trade amount, but 200 000 of it is BSQ
        TransactionOutput bsqOutput = confirmedTx(10, 200_000).getOutput(0);
        TransactionOutput btcOutput = confirmedTx(1, 50_000).getOutput(0);
        dao.addParsedTx(bsqOutput.getParentTransaction(), TxOutputType.BSQ_OUTPUT);

        BsqSwapTakeOfferModel model = sellersTakeOfferModel(btcWalletService(bsqOutput, btcOutput));

        assertTrue(model.hasMissingFunds());
        assertTrue(model.getMissingFundsAsCoin().isPositive());
    }

    @Test
    void takeOfferModelReportsNoMissingFundsIfTheDaoStateDoesNotHoldTheOutputAsBsq() {
        TransactionOutput olderOutput = confirmedTx(10, 200_000).getOutput(0);
        TransactionOutput newerOutput = confirmedTx(1, 50_000).getOutput(0);

        BsqSwapTakeOfferModel model = sellersTakeOfferModel(btcWalletService(olderOutput, newerOutput));

        assertFalse(model.hasMissingFunds());
    }

    @Test
    void openSellOfferGetsMissingFundsOnceTheDaoStateHoldsItsOutputAsBsq() {
        // The DAO state does not hold the BSQ output yet, so it still counts as BTC and covers the offer
        TransactionOutput bsqOutput = confirmedTx(10, 200_000).getOutput(0);
        TransactionOutput btcOutput = confirmedTx(1, 50_000).getOutput(0);
        DaoFacade daoFacade = spy(dao.facade);
        OpenBsqSwapOfferService openBsqSwapOfferService = mock(OpenBsqSwapOfferService.class);
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(TX_FEE_PER_VBYTE));
        when(feeService.feeUpdateCounterProperty()).thenReturn(new SimpleIntegerProperty(0));
        Offer offer = mock(Offer.class);
        when(offer.getMakerFee()).thenReturn(Coin.valueOf(10));
        when(offer.getAmount()).thenReturn(Coin.valueOf(100_000));
        OpenOffer openOffer = new OpenOffer(offer);
        OpenBsqSwapOffer openBsqSwapOffer = new OpenBsqSwapOffer(openOffer,
                openBsqSwapOfferService,
                feeService,
                btcWalletService(bsqOutput, btcOutput),
                mock(BsqWalletService.class),
                daoFacade);
        assertFalse(openOffer.isBsqSwapOfferHasMissingFunds());

        // The DAO parses the block with the BSQ output
        dao.addParsedTx(bsqOutput.getParentTransaction(), TxOutputType.BSQ_OUTPUT);
        dao.daoStateService.onParseBlockComplete(dao.daoStateService.getLastBlock().orElseThrow());

        assertTrue(openOffer.isBsqSwapOfferHasMissingFunds());
        verify(openBsqSwapOfferService).disableBsqSwapOffer(openOffer);

        ArgumentCaptor<DaoStateListener> daoStateListener = ArgumentCaptor.forClass(DaoStateListener.class);
        verify(daoFacade).addBsqStateListener(daoStateListener.capture());
        openBsqSwapOffer.removeListeners();
        verify(daoFacade).removeBsqStateListener(daoStateListener.getValue());
    }

    private Tuple2<List<RawTransactionInput>, Coin> select(long btcTradeAmount, TransactionOutput... walletOutputs)
            throws InsufficientMoneyException {
        return BsqSwapCalculation.getSellersBtcInputsAndChange(btcWalletService(walletOutputs),
                dao.facade,
                btcTradeAmount,
                TX_FEE_PER_VBYTE,
                SELLERS_TRADE_FEE);
    }

    // Takes a BUY offer, so the taker is the BTC seller
    private BsqSwapTakeOfferModel sellersTakeOfferModel(BtcWalletService btcWalletService) {
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(TX_FEE_PER_VBYTE));
        // A real OfferUtil, as the balance shortage of the model comes from it
        OfferUtil offerUtil = new OfferUtil(mock(AccountAgeWitnessService.class),
                mock(BsqWalletService.class),
                mock(FilterPolicyService.class),
                mock(Preferences.class),
                mock(PriceFeedService.class),
                mock(P2PService.class),
                mock(ReferralIdService.class),
                mock(TradeStatisticsManager.class));
        BsqSwapTakeOfferModel model = new BsqSwapTakeOfferModel(offerUtil,
                btcWalletService,
                mock(BsqWalletService.class),
                dao.facade,
                feeService,
                mock(TradeManager.class),
                mock(FilterPolicyService.class));

        Offer offer = mock(Offer.class);
        when(offer.getDirection()).thenReturn(OfferDirection.BUY);
        when(offer.getPrice()).thenReturn(Price.valueOf("BSQ", 2_000));
        when(offer.getAmount()).thenReturn(Coin.valueOf(100_000));
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(100_000));
        model.initWithData(offer);
        return model;
    }

    // A BtcWalletService whose wallet holds the given outputs as spend candidates, each at an available address
    private static BtcWalletService btcWalletService(TransactionOutput... walletOutputs) {
        Wallet wallet = mock(Wallet.class);
        when(wallet.calculateAllSpendCandidates()).thenReturn(List.of(walletOutputs));
        List<Address> walletAddresses = Arrays.stream(walletOutputs)
                .map(output -> output.getScriptPubKey().getToAddress(PARAMS))
                .distinct()
                .collect(Collectors.toList());
        ImmutableList<AddressEntry> addressEntries = walletAddresses.stream()
                .map(BsqSwapSellerInputSelectionTest::availableAddressEntry)
                .collect(ImmutableList.toImmutableList());
        AddressEntryList addressEntryList = mock(AddressEntryList.class);
        when(addressEntryList.getAddressEntriesAsListImmutable()).thenReturn(addressEntries);

        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getParams()).thenReturn(PARAMS);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getChain()).thenReturn(mock(BlockChain.class));
        when(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE))
                .thenReturn(Set.copyOf(walletAddresses));
        Preferences preferences = mock(Preferences.class);
        when(preferences.getIgnoreDustThreshold()).thenReturn(546);

        BtcWalletService btcWalletService = new BtcWalletService(walletsSetup,
                addressEntryList,
                preferences,
                mock(FeeService.class));
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
        return btcWalletService;
    }

    private static AddressEntry availableAddressEntry(Address address) {
        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getContext()).thenReturn(AddressEntry.Context.AVAILABLE);
        when(addressEntry.getAddress()).thenReturn(address);
        return addressEntry;
    }

    // A confirmed tx which pays the given values to our addresses
    private static Transaction confirmedTx(int depthInBlocks, long... values) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.of(new ECKey().getPubKey()), 0, ScriptBuilder.createEmpty());
        for (long value : values) {
            tx.addOutput(Coin.valueOf(value), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        Transaction confirmedTx = new Transaction(PARAMS, tx.bitcoinSerialize());
        TransactionConfidence confidence = confirmedTx.getConfidence();
        confidence.setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        confidence.setDepthInBlocks(depthInBlocks);
        return confirmedTx;
    }

    private static List<TransactionOutPoint> outpoints(Tuple2<List<RawTransactionInput>, Coin> inputsAndChange) {
        return inputsAndChange.first.stream()
                .map(input -> new TransactionOutPoint(PARAMS,
                        input.index,
                        new Transaction(PARAMS, input.parentTransaction).getTxId()))
                .collect(Collectors.toList());
    }
}
