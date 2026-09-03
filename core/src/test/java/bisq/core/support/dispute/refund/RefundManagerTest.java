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

package bisq.core.support.dispute.refund;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.utils.DepositTransactionUtils;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.mempool.MempoolTxStatus;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeValidation;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.util.Hex;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundManagerTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final Coin TRADE_AMOUNT = Coin.valueOf(20_000);
    private static final Coin BUYER_SECURITY_DEPOSIT = Coin.valueOf(2_000);
    private static final Coin SELLER_SECURITY_DEPOSIT = Coin.valueOf(3_000);
    private static final Coin ESCROW_VALUE = Coin.valueOf(30_000);
    private static final int SELECTION_HEIGHT = 800_000;
    private static final long TRADE_TX_FEE = 5_000;
    private static final int ADDRESS_LIST_VERSION = 1;
    private static final int TRADE_START_HEIGHT = 800_005;
    private static final long LOCK_TIME = TRADE_START_HEIGHT + Restrictions.getLockTime(false);
    private static final int DEPOSIT_BLOCK_HEIGHT = 800_010;
    private static final int DELAYED_PAYOUT_BLOCK_HEIGHT = Math.toIntExact(LOCK_TIME + 1);
    private static final int LOCAL_CHAIN_HEIGHT = DELAYED_PAYOUT_BLOCK_HEIGHT + 10;
    private static final ECKey BUYER_MULTISIG_KEY = new ECKey();
    private static final ECKey SELLER_MULTISIG_KEY = new ECKey();

    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);
    private final DaoFacade daoFacade = mock(DaoFacade.class);
    private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService =
            mock(DelayedPayoutTxReceiverService.class);
    private final RefundManager refundManager = refundManager(btcWalletService,
            daoFacade,
            delayedPayoutTxReceiverService);

    @BeforeEach
    void setUp() {
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        when(daoFacade.getChainHeight()).thenReturn(LOCAL_CHAIN_HEIGHT);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight(TRADE_START_HEIGHT))
                .thenReturn(SELECTION_HEIGHT);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight(DEPOSIT_BLOCK_HEIGHT))
                .thenReturn(SELECTION_HEIGHT);
    }


    /* --------------------------------------------------------------------- */
    // verifyTradeTxChain
    /* --------------------------------------------------------------------- */

    @Test
    void verifyTradeTxChainAcceptsDelayedPayoutTxSpendingDepositEscrowOutput() {
        List<Transaction> transactions = tradeTxChain(0);

        assertDoesNotThrow(() -> refundManager.verifyTradeTxChain(transactions));
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutTxSpendingDepositChangeOutput() {
        List<Transaction> transactions = tradeTxChain(1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(transactions));
        assertTrue(exception.getMessage().contains("output 0 of deposit tx"), exception.getMessage());
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutTxWithSecondInput() {
        List<Transaction> transactions = tradeTxChain(0);
        transactions.get(3).addInput(transactionWithOutput(Coin.valueOf(1_000)).getOutput(0));

        assertThrows(IllegalArgumentException.class, () -> refundManager.verifyTradeTxChain(transactions));
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutTxSpendingOtherDepositTx() {
        List<Transaction> transactions = tradeTxChain(0);
        Transaction otherDepositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(otherDepositTx, List.of(new Tuple2<>(29_000L, newAddress())));

        assertThrows(IllegalArgumentException.class, () -> refundManager.verifyTradeTxChain(
                List.of(transactions.get(0), transactions.get(1), transactions.get(2), delayedPayoutTx)));
    }

    @Test
    void verifyTradeTxChainRejectsDepositTxNotFundedByTakerFeeTx() {
        List<Transaction> transactions = tradeTxChain(0);
        Transaction unrelatedTakerFeeTx = transactionWithOutput(Coin.valueOf(20_000));

        assertThrows(IllegalArgumentException.class, () -> refundManager.verifyTradeTxChain(
                List.of(transactions.get(0), unrelatedTakerFeeTx, transactions.get(2), transactions.get(3))));
    }

    @Test
    void parseRequestedTransactionAcceptsTransactionMatchingRequestedId() {
        Transaction transaction = transactionWithOutput(Coin.valueOf(1_000));

        assertDoesNotThrow(() -> RefundManager.parseRequestedTransaction(PARAMS,
                transaction.getTxId().toString(),
                Hex.encode(transaction.bitcoinSerialize())));
    }

    @Test
    void parseRequestedTransactionRejectsTransactionNotMatchingRequestedId() {
        Transaction transaction = transactionWithOutput(Coin.valueOf(1_000));
        Transaction requestedTransaction = transactionWithOutput(Coin.valueOf(2_000));

        assertThrows(IllegalArgumentException.class,
                () -> RefundManager.parseRequestedTransaction(PARAMS,
                        requestedTransaction.getTxId().toString(),
                        Hex.encode(transaction.bitcoinSerialize())));
    }


    /* --------------------------------------------------------------------- */
    // verifyTradeTxChain with confirmation and contract context
    /* --------------------------------------------------------------------- */

    @Test
    void verifyTradeTxChainAcceptsConfirmedCanonicalTransactionsAtContractDerivedSnapshot() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);

        assertDoesNotThrow(() -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsUnconfirmedDepositTransaction() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                false,
                0,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsUnconfirmedDelayedPayoutTransaction() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                false,
                0);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutConfirmingAtLockTime() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                LOCK_TIME);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutWithWrongLockTime() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME - 1,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutWithFinalSequence() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsSelectionHeightOutsideOneSnapshotGrid() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);
        Dispute dispute = disputeFor(txChain);
        when(dispute.getBurningManSelectionHeight()).thenReturn(SELECTION_HEIGHT - 20);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, dispute));
    }

    @Test
    void verifyTradeTxChainAcceptsAdjacentSnapshotSelectedBeforeDepositConfirmation() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);
        Dispute dispute = disputeFor(txChain);
        when(dispute.getBurningManSelectionHeight()).thenReturn(
                SELECTION_HEIGHT + DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight(DEPOSIT_BLOCK_HEIGHT))
                .thenReturn(SELECTION_HEIGHT + DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE);

        assertDoesNotThrow(() -> refundManager.verifyTradeTxChain(txChain, dispute));
    }

    @Test
    void verifyTradeTxChainRejectsDepositConfirmedAfterContractLockTime() {
        long depositBlockHeight = LOCK_TIME + 1;
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                depositBlockHeight,
                true,
                LOCK_TIME + 2);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, disputeFor(txChain)));
    }

    @Test
    void verifyTradeTxChainRejectsLegacyReceiverRuleForModernTrade() {
        RefundTransactionChain txChain = refundTransactionChain(LOCK_TIME,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                DEPOSIT_BLOCK_HEIGHT,
                true,
                DELAYED_PAYOUT_BLOCK_HEIGHT);
        Dispute dispute = disputeFor(txChain);
        when(dispute.isUsingLegacyBurningMan()).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(txChain, dispute));
    }

    @Test
    void verifyTradeTxChainAcceptsLegacyReceiverRuleForPreSnapshotTrade() {
        int oldTradeStartHeight = DelayedPayoutTxReceiverService.MIN_SNAPSHOT_HEIGHT - 100;
        long oldLockTime = oldTradeStartHeight + Restrictions.getLockTime(false);
        RefundTransactionChain txChain = refundTransactionChain(oldLockTime,
                TransactionInput.NO_SEQUENCE - 1,
                true,
                oldTradeStartHeight + 10,
                true,
                oldLockTime + 1);
        Dispute dispute = disputeFor(txChain);
        when(dispute.getContract().getLockTime()).thenReturn(oldLockTime);
        when(dispute.isUsingLegacyBurningMan()).thenReturn(true);

        assertDoesNotThrow(() -> refundManager.verifyTradeTxChain(txChain, dispute));
    }


    /* --------------------------------------------------------------------- */
    // verifyDelayedPayoutTxReceivers
    /* --------------------------------------------------------------------- */

    @Test
    void verifyDelayedPayoutTxReceiversAcceptsOutputsMatchingScheduleDerivedFromEscrowOutput() {
        Transaction depositTx = tradeTxChain(0).get(2);
        List<Tuple2<Long, String>> receivers = List.of(new Tuple2<>(20_000L, newAddress()),
                new Tuple2<>(9_000L, newAddress()));
        Dispute dispute = burningManDispute(depositTx, receivers);

        assertDoesNotThrow(() -> refundManager.verifyDelayedPayoutTxReceivers(
                depositTx, delayedPayoutTx(depositTx, receivers), dispute));
    }

    @Test
    void verifyDelayedPayoutTxReceiversRejectsOutputValueMismatch() {
        Transaction depositTx = tradeTxChain(0).get(2);
        List<Tuple2<Long, String>> receivers = List.of(new Tuple2<>(20_000L, newAddress()),
                new Tuple2<>(9_000L, newAddress()));
        Dispute dispute = burningManDispute(depositTx, receivers);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, List.of(receivers.get(0),
                new Tuple2<>(8_000L, receivers.get(1).second)));

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDelayedPayoutTxReceivers(depositTx, delayedPayoutTx, dispute));
    }

    @Test
    void verifyDelayedPayoutTxReceiversRejectsOutputAddressMismatch() {
        Transaction depositTx = tradeTxChain(0).get(2);
        List<Tuple2<Long, String>> receivers = List.of(new Tuple2<>(20_000L, newAddress()),
                new Tuple2<>(9_000L, newAddress()));
        Dispute dispute = burningManDispute(depositTx, receivers);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, List.of(receivers.get(0),
                new Tuple2<>(9_000L, newAddress())));

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDelayedPayoutTxReceivers(depositTx, delayedPayoutTx, dispute));
    }

    @Test
    void verifyDelayedPayoutTxReceiversRejectsOutputCountMismatch() {
        Transaction depositTx = tradeTxChain(0).get(2);
        List<Tuple2<Long, String>> receivers = List.of(new Tuple2<>(20_000L, newAddress()),
                new Tuple2<>(9_000L, newAddress()));
        Dispute dispute = burningManDispute(depositTx, receivers);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, List.of(receivers.get(0)));

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDelayedPayoutTxReceivers(depositTx, delayedPayoutTx, dispute));
    }


    /* --------------------------------------------------------------------- */
    // verifyDepositTx and verifyRefundPayoutAmount
    /* --------------------------------------------------------------------- */

    @Test
    void verifyDepositTxAcceptsContractBoundEscrowScriptAndValue() {
        Transaction depositTx = tradeTxChain(0).get(2);

        assertDoesNotThrow(() -> refundManager.verifyDepositTx(depositTx,
                burningManDispute(depositTx, List.of())));
    }

    @Test
    void verifyDepositTxRejectsSyntheticUnderfundedEscrow() {
        Transaction depositTx = depositTx(Coin.valueOf(10_000), expectedEscrowScript());

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDepositTx(depositTx, burningManDispute(depositTx, List.of())));
    }

    @Test
    void verifyDepositTxRejectsEscrowUsingWrongScript() {
        Transaction depositTx = depositTx(ESCROW_VALUE, ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDepositTx(depositTx, burningManDispute(depositTx, List.of())));
    }

    @Test
    void verifyDepositTxRejectsClaimedTradeFeeNotDerivedFromEscrowValue() {
        Transaction depositTx = tradeTxChain(0).get(2);
        Dispute dispute = burningManDispute(depositTx, List.of(), TRADE_TX_FEE - 1);

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyDepositTx(depositTx, dispute));
    }

    @Test
    void verifyRefundPayoutAmountAcceptsAmountAtVerifiedReceiverValue() {
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                List.of(new Tuple2<>(24_000L, newAddress())));
        Dispute dispute = burningManDispute(depositTx, List.of());
        when(dispute.getDelayedPayoutTxId()).thenReturn(delayedPayoutTx.getTxId().toString());

        assertDoesNotThrow(() -> refundManager.verifyRefundPayoutAmount(depositTx,
                delayedPayoutTx,
                dispute,
                Coin.valueOf(20_000),
                Coin.valueOf(4_000)));
    }

    @Test
    void verifyRefundPayoutAmountRejectsAmountAboveVerifiedReceiverValue() {
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                List.of(new Tuple2<>(24_000L, newAddress())));
        Dispute dispute = burningManDispute(depositTx, List.of());
        when(dispute.getDelayedPayoutTxId()).thenReturn(delayedPayoutTx.getTxId().toString());

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyRefundPayoutAmount(depositTx,
                        delayedPayoutTx,
                        dispute,
                        Coin.valueOf(20_000),
                        Coin.valueOf(5_000)));
    }

    @Test
    void verifyRefundPayoutAmountRejectsAmountAboveDeclaredPot() {
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                List.of(new Tuple2<>(29_000L, newAddress())));
        Dispute dispute = burningManDispute(depositTx, List.of());
        when(dispute.getDelayedPayoutTxId()).thenReturn(delayedPayoutTx.getTxId().toString());

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyRefundPayoutAmount(depositTx,
                        delayedPayoutTx,
                        dispute,
                        Coin.valueOf(20_000),
                        Coin.valueOf(6_000)));
    }

    @Test
    void refundValidationResultRejectsPayoutChangedAfterValidation() {
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                List.of(new Tuple2<>(24_000L, newAddress())));
        Dispute dispute = burningManDispute(depositTx, List.of());
        when(dispute.getDelayedPayoutTxId()).thenReturn(delayedPayoutTx.getTxId().toString());
        RefundValidationResult result = refundManager.verifyRefundPayoutAmount(depositTx,
                delayedPayoutTx,
                dispute,
                Coin.valueOf(20_000),
                Coin.valueOf(4_000));

        assertThrows(IllegalArgumentException.class,
                () -> result.verifyMatches(dispute, Coin.valueOf(20_001), Coin.valueOf(3_999)));
    }

    @Test
    void refundValidationResultRejectsReceiverInputsChangedAfterValidation() {
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                List.of(new Tuple2<>(24_000L, newAddress())));
        Dispute dispute = burningManDispute(depositTx, List.of());
        when(dispute.getDelayedPayoutTxId()).thenReturn(delayedPayoutTx.getTxId().toString());
        RefundValidationResult result = refundManager.verifyRefundPayoutAmount(depositTx,
                delayedPayoutTx,
                dispute,
                Coin.valueOf(20_000),
                Coin.valueOf(4_000));

        when(dispute.getTradeTxFee()).thenReturn(TRADE_TX_FEE + 1);
        assertThrows(IllegalArgumentException.class,
                () -> result.verifyMatches(dispute, Coin.valueOf(20_000), Coin.valueOf(4_000)));

        when(dispute.getTradeTxFee()).thenReturn(TRADE_TX_FEE);
        when(dispute.getBurningManSelectionHeight()).thenReturn(SELECTION_HEIGHT + 10);
        assertThrows(IllegalArgumentException.class,
                () -> result.verifyMatches(dispute, Coin.valueOf(20_000), Coin.valueOf(4_000)));
    }


    /* --------------------------------------------------------------------- */
    // verifyLegacyDelayedPayoutTx
    /* --------------------------------------------------------------------- */

    @Test
    void verifyLegacyDelayedPayoutTxAcceptsSingleOutputToDaoDonationAddress() {
        String donationAddress = newAddress();
        when(daoFacade.getAllDonationAddresses()).thenReturn(Set.of(donationAddress));
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, List.of(new Tuple2<>(29_000L, donationAddress)));

        assertDoesNotThrow(() -> refundManager.verifyLegacyDelayedPayoutTx(delayedPayoutTx,
                legacyDispute(donationAddress)));
    }

    @Test
    void verifyLegacyDelayedPayoutTxRejectsOutputToOtherAddress() {
        // Colluding traders spend the escrow output to an address they control while the dispute carries a
        // valid donation address string.
        String donationAddress = newAddress();
        when(daoFacade.getAllDonationAddresses()).thenReturn(Set.of(donationAddress));
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, List.of(new Tuple2<>(29_000L, newAddress())));

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyLegacyDelayedPayoutTx(delayedPayoutTx, legacyDispute(donationAddress)));
    }

    @Test
    void verifyLegacyDelayedPayoutTxRejectsSecondOutput() {
        String donationAddress = newAddress();
        when(daoFacade.getAllDonationAddresses()).thenReturn(Set.of(donationAddress));
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, List.of(new Tuple2<>(1_000L, donationAddress),
                new Tuple2<>(28_000L, newAddress())));

        assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyLegacyDelayedPayoutTx(delayedPayoutTx, legacyDispute(donationAddress)));
    }

    @Test
    void verifyLegacyDelayedPayoutTxRejectsAddressUnknownToDao() {
        String claimedDonationAddress = newAddress();
        when(daoFacade.getAllDonationAddresses()).thenReturn(Set.of(newAddress()));
        Transaction depositTx = tradeTxChain(0).get(2);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx,
                List.of(new Tuple2<>(29_000L, claimedDonationAddress)));

        assertThrows(DisputeValidation.AddressException.class,
                () -> refundManager.verifyLegacyDelayedPayoutTx(delayedPayoutTx,
                        legacyDispute(claimedDonationAddress)));
    }


    /* --------------------------------------------------------------------- */
    // Fixtures
    /* --------------------------------------------------------------------- */

    private RefundTransactionChain refundTransactionChain(long delayedPayoutLockTime,
                                                           long delayedPayoutSequence,
                                                           boolean depositConfirmed,
                                                           long depositBlockHeight,
                                                           boolean delayedPayoutConfirmed,
                                                           long delayedPayoutBlockHeight) {
        List<Transaction> transactions = tradeTxChain(0);
        Transaction delayedPayoutTx = transactions.get(3);
        delayedPayoutTx.getInput(0).setSequenceNumber(delayedPayoutSequence);
        delayedPayoutTx.setLockTime(delayedPayoutLockTime);
        return new RefundTransactionChain(
                transactions.get(0),
                transactions.get(1),
                transactions.get(2),
                delayedPayoutTx,
                new MempoolTxStatus(transactions.get(2).getTxId().toString(),
                        depositConfirmed,
                        depositBlockHeight),
                new MempoolTxStatus(delayedPayoutTx.getTxId().toString(),
                        delayedPayoutConfirmed,
                        delayedPayoutBlockHeight));
    }

    private Dispute disputeFor(RefundTransactionChain txChain) {
        Dispute dispute = burningManDispute(txChain.depositTx(), List.of());
        Contract contract = dispute.getContract();
        when(contract.getOfferPayload().getOfferFeePaymentTxId())
                .thenReturn(txChain.makerFeeTx().getTxId().toString());
        when(contract.getTakerFeeTxID()).thenReturn(txChain.takerFeeTx().getTxId().toString());
        when(contract.getLockTime()).thenReturn(LOCK_TIME);
        when(contract.getPaymentMethodId()).thenReturn(PaymentMethod.SEPA_ID);
        when(dispute.getDelayedPayoutTxId()).thenReturn(txChain.delayedPayoutTx().getTxId().toString());
        return dispute;
    }

    // Maker fee tx and taker fee tx fund the deposit tx, which has the escrow at output 0 and a change output at
    // index 1. The delayed payout tx spends the given deposit output.
    private static List<Transaction> tradeTxChain(int delayedPayoutInputIndex) {
        Transaction makerFeeTx = transactionWithOutput(Coin.valueOf(20_000));
        Transaction takerFeeTx = transactionWithOutput(Coin.valueOf(20_000));

        Transaction depositTx = depositTx(makerFeeTx, takerFeeTx, ESCROW_VALUE, expectedEscrowScript());

        Transaction delayedPayoutTx = new Transaction(PARAMS);
        delayedPayoutTx.addInput(depositTx.getOutput(delayedPayoutInputIndex));
        delayedPayoutTx.addOutput(Coin.valueOf(29_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        return List.of(makerFeeTx, takerFeeTx, depositTx, delayedPayoutTx);
    }

    private static Transaction transactionWithOutput(Coin value) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        transaction.addOutput(value, ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return transaction;
    }

    private static Transaction depositTx(Coin escrowValue, Script escrowScript) {
        return depositTx(transactionWithOutput(Coin.valueOf(20_000)),
                transactionWithOutput(Coin.valueOf(20_000)),
                escrowValue,
                escrowScript);
    }

    private static Transaction depositTx(Transaction makerFeeTx,
                                         Transaction takerFeeTx,
                                         Coin escrowValue,
                                         Script escrowScript) {
        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(makerFeeTx.getOutput(0));
        depositTx.addInput(takerFeeTx.getOutput(0));
        depositTx.addOutput(escrowValue, escrowScript);
        depositTx.addOutput(Coin.valueOf(9_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return depositTx;
    }

    private static Script expectedEscrowScript() {
        return DepositTransactionUtils.get2of2MultiSigOutputScript(
                BUYER_MULTISIG_KEY.getPubKey(),
                SELLER_MULTISIG_KEY.getPubKey());
    }

    // Same shape as TradeWalletService.createDelayedUnsignedPayoutTx: spends deposit output 0 and pays the receivers.
    private static Transaction delayedPayoutTx(Transaction depositTx, List<Tuple2<Long, String>> receivers) {
        Transaction delayedPayoutTx = new Transaction(PARAMS);
        delayedPayoutTx.addInput(depositTx.getOutput(0));
        receivers.forEach(receiver -> delayedPayoutTx.addOutput(Coin.valueOf(receiver.first),
                Address.fromString(PARAMS, receiver.second)));
        return delayedPayoutTx;
    }

    private static String newAddress() {
        return SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    }

    // The receiver service is stubbed for exactly the escrow output value, so the schedule is only found when the
    // manager derives the input amount from deposit output 0.
    private Dispute burningManDispute(Transaction depositTx, List<Tuple2<Long, String>> receivers) {
        return burningManDispute(depositTx, receivers, TRADE_TX_FEE);
    }

    private Dispute burningManDispute(Transaction depositTx,
                                      List<Tuple2<Long, String>> receivers,
                                      long tradeTxFee) {
        Contract contract = mock(Contract.class);
        OfferPayload offerPayload = mock(OfferPayload.class);
        when(offerPayload.getBuyerSecurityDeposit()).thenReturn(BUYER_SECURITY_DEPOSIT.value);
        when(offerPayload.getSellerSecurityDeposit()).thenReturn(SELLER_SECURITY_DEPOSIT.value);
        when(contract.getOfferPayload()).thenReturn(offerPayload);
        when(contract.getTradeAmount()).thenReturn(TRADE_AMOUNT);
        when(contract.getBuyerMultiSigPubKey()).thenReturn(BUYER_MULTISIG_KEY.getPubKey());
        when(contract.getSellerMultiSigPubKey()).thenReturn(SELLER_MULTISIG_KEY.getPubKey());
        when(contract.getBurningManAddressListVersion()).thenReturn(ADDRESS_LIST_VERSION);
        Dispute dispute = mock(Dispute.class);
        when(dispute.findDepositTx(btcWalletService)).thenReturn(Optional.of(depositTx));
        when(dispute.getDepositTxId()).thenReturn(depositTx.getTxId().toString());
        when(dispute.getContractHash()).thenReturn(new byte[]{1});
        when(dispute.getBurningManSelectionHeight()).thenReturn(SELECTION_HEIGHT);
        when(dispute.getTradeTxFee()).thenReturn(tradeTxFee);
        when(dispute.getContract()).thenReturn(contract);
        when(delayedPayoutTxReceiverService.getReceivers(SELECTION_HEIGHT,
                ESCROW_VALUE.value,
                tradeTxFee,
                ADDRESS_LIST_VERSION)).thenReturn(receivers);
        return dispute;
    }

    private static Dispute legacyDispute(String donationAddress) {
        Dispute dispute = mock(Dispute.class);
        when(dispute.getDonationAddressOfDelayedPayoutTx()).thenReturn(donationAddress);
        return dispute;
    }

    private static RefundManager refundManager(BtcWalletService btcWalletService,
                                               DaoFacade daoFacade,
                                               DelayedPayoutTxReceiverService delayedPayoutTxReceiverService) {
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
                daoFacade,
                delayedPayoutTxReceiverService,
                mock(KeyRing.class),
                mock(RefundDisputeListService.class),
                mock(Config.class),
                mock(PriceFeedService.class),
                mock(MempoolService.class),
                mock(RefundPayoutReceiptService.class));
    }
}
