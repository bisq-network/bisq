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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.dispute.refund.RefundClaimSignature;
import bisq.core.support.dispute.refund.RefundValidationResult;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class DisputeSummaryWindowRefundAuthorizationTest {
    private final RefundManager manager = mock(RefundManager.class);
    private final Dispute dispute = mock(Dispute.class);
    private final DisputeResult result = new DisputeResult("trade-id", 1);
    private final RefundValidationResult evidence = mock(RefundValidationResult.class);
    private final TxFeeEstimationService feeEstimator = mock(TxFeeEstimationService.class);
    private final BtcWalletService wallet = mock(BtcWalletService.class);
    private final TradeWalletService tradeWallet = mock(TradeWalletService.class);
    private DisputeSummaryWindow window;

    @BeforeEach
    void setUp() throws Exception {
        Res.setup();
        when(dispute.getSupportType()).thenReturn(SupportType.REFUND);
        Contract contract = mock(Contract.class);
        when(dispute.getContract()).thenReturn(contract);
        when(contract.getBuyerPayoutAddressString()).thenReturn("buyer-address");
        when(contract.getSellerPayoutAddressString()).thenReturn("seller-address");
        result.setBuyerPayoutAmount(Coin.valueOf(1_000));
        result.setSellerPayoutAmount(Coin.ZERO);
        window = new DisputeSummaryWindow(mock(CoinFormatter.class), mock(MediationManager.class),
                manager, tradeWallet, wallet,
                feeEstimator, mock(MempoolService.class), mock(DaoFacade.class));
        // Set only dialog state, without constructing JavaFX controls or widening production visibility.
        setField("dispute", dispute);
        setField("disputeResult", result);
        setField("refundValidationResult", evidence);
        setField("peersDisputeOptional", Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(value = BaseCurrencyNetwork.class, names = {"BTC_MAINNET", "BTC_REGTEST"})
    void invalidClaimStopsCloseBeforeExplorerRequestOnBothNetworks(BaseCurrencyNetwork network) throws Exception {
        doThrow(new IllegalArgumentException("missing claim"))
                .when(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.ZERO);
        try (MockedStatic<Config> config = mockStatic(Config.class, CALLS_REAL_METHODS);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            config.when(Config::baseCurrencyNetwork).thenReturn(network);
            CompletableFuture<?> outcome = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            assertTrue(outcome.isDone());
            assertEquals(false, outcome.join());
            verify(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.ZERO);
            verify(manager, never()).requestBlockchainTransactions(any(), any(), any(), any());
            assertEquals(1, popups.constructed().size());
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(popups.constructed().get(0)).warning(warning.capture());
            assertTrue(warning.getValue().startsWith("Refund authorization failed."));
            assertTrue(warning.getValue().contains("missing claim"));
        }
    }

    @Test
    void unchangedAuthorizationCommitsAfterDurableReservation() throws Exception {
        Transaction transaction = mock(Transaction.class);
        CompletableFuture<Boolean> outcome = new CompletableFuture<>();
        Runnable persisted = beginPayoutReservation(transaction, outcome);
        verify(wallet, never()).commitTx(any());
        verify(tradeWallet, never()).broadcastTx(any(), any());

        persisted.run();

        verify(wallet).commitTx(transaction);
        verify(tradeWallet).broadcastTx(eq(transaction), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired claim", "changed allocation", "replaced dialog row"})
    void authorizationChangesDuringPersistencePreventWalletCommit(String change) throws Exception {
        Transaction transaction = mock(Transaction.class);
        CompletableFuture<Boolean> outcome = new CompletableFuture<>();
        Runnable persisted = beginPayoutReservation(transaction, outcome);
        switch (change) {
            case "expired claim" -> doThrow(new IllegalArgumentException("grace period expired"))
                    .when(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.ZERO);
            case "changed allocation" -> {
                result.setBuyerPayoutAmount(Coin.valueOf(900));
                doThrow(new IllegalArgumentException("Buyer payout amount changed after refund validation"))
                        .when(evidence).verifyMatches(dispute, result);
            }
            case "replaced dialog row" -> setField("dispute", mock(Dispute.class));
            default -> throw new AssertionError(change);
        }
        try (MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            persisted.run();

            verify(wallet, never()).commitTx(any());
            verify(tradeWallet, never()).broadcastTx(any(), any());
            // The consumed reservation is reported once, naming the reserved transaction, and the close attempt
            // ends only when the operator dismisses that report.
            assertEquals(1, popups.constructed().size());
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(popups.constructed().get(0)).warning(warning.capture());
            assertTrue(warning.getValue().startsWith("Refund authorization failed after the payout reservation was saved."));
            assertTrue(warning.getValue().contains(Sha256Hash.ZERO_HASH.toString()));
            assertTrue(warning.getValue().contains("No transaction was committed or broadcast"));
            assertFalse(outcome.isDone());
            ArgumentCaptor<Runnable> closeHandler = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(0)).onClose(closeHandler.capture());
            closeHandler.getValue().run();
            assertEquals(false, outcome.join());
        }
    }

    private Runnable beginPayoutReservation(Transaction transaction, CompletableFuture<Boolean> outcome) throws Exception {
        when(transaction.getTxId()).thenReturn(Sha256Hash.ZERO_HASH);
        Contract contract = dispute.getContract();
        when(contract.getOfferPayload()).thenReturn(mock(OfferPayload.class));
        when(contract.getTradeAmount()).thenReturn(Coin.valueOf(1_000));
        when(manager.getMaximumRefundPayoutAmount(dispute)).thenReturn(Coin.valueOf(1_000));
        when(wallet.createRefundPayoutTx(any(), any(), any(), any(), any(), any())).thenReturn(transaction);
        Method payout = DisputeSummaryWindow.class.getDeclaredMethod("doPayout", Coin.class, Coin.class,
                Coin.class, String.class, String.class, CompletableFuture.class);
        payout.setAccessible(true);
        payout.invoke(window, Coin.valueOf(1_000), Coin.ZERO, Coin.valueOf(100),
                "buyer-address", "seller-address", outcome);
        ArgumentCaptor<Runnable> persisted = ArgumentCaptor.forClass(Runnable.class);
        verify(manager).persistRefundPayoutReservation(eq(dispute), eq(transaction), persisted.capture(), any());
        return persisted.getValue();
    }

    @Test
    void cachedEvidenceDoesNotAuthorizePayoutOrResultAfterClaimVerificationFails() throws Exception {
        assertTrue((boolean) invoke("isRefundValidationCurrent"));
        doThrow(new IllegalArgumentException("invalid claim"))
                .when(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.ZERO);
        try (MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            Method payoutCheck = DisputeSummaryWindow.class.getDeclaredMethod("isRefundValidationCurrent",
                    Coin.class, Coin.class);
            payoutCheck.setAccessible(true);
            assertFalse((boolean) payoutCheck.invoke(window, Coin.valueOf(1_000), Coin.ZERO));
            verify(evidence).verifyMatches(dispute, result);
            verify(evidence, never()).verifyMatches(dispute, Coin.valueOf(1_000), Coin.ZERO);
            assertEquals(2, popups.constructed().size());
            for (Popup popup : popups.constructed()) {
                ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
                verify(popup).warning(warning.capture());
                assertTrue(warning.getValue().startsWith("Refund authorization failed."));
                assertTrue(warning.getValue().contains("invalid claim"));
            }
        }
    }

    @Test
    void staleEvidenceUsesAuthorizationDiagnosticAtBothBoundaries() throws Exception {
        doThrow(new IllegalArgumentException("changed transaction evidence"))
                .when(evidence).verifyMatches(dispute, result);
        doThrow(new IllegalArgumentException("changed transaction evidence"))
                .when(evidence).verifyMatches(dispute, Coin.valueOf(1_000), Coin.ZERO);
        try (MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            Method payoutCheck = DisputeSummaryWindow.class.getDeclaredMethod("isRefundValidationCurrent",
                    Coin.class, Coin.class);
            payoutCheck.setAccessible(true);
            assertFalse((boolean) payoutCheck.invoke(window, Coin.valueOf(1_000), Coin.ZERO));
            assertEquals(2, popups.constructed().size());
            for (Popup popup : popups.constructed()) {
                ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
                verify(popup).warning(warning.capture());
                assertTrue(warning.getValue().startsWith("Refund authorization failed."));
                assertTrue(warning.getValue().contains("changed transaction evidence"));
            }
        }
    }

    @Test
    void closedPeerDoesNotBypassTwoRecipientVerification() throws Exception {
        result.setSellerPayoutAmount(Coin.valueOf(500));
        when(manager.isRefundEvidenceValidationSkipped()).thenReturn(true);
        Dispute peer = mock(Dispute.class);
        when(peer.isClosed()).thenReturn(true);
        setField("peersDisputeOptional", Optional.of(peer));
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            CompletableFuture<?> payout = (CompletableFuture<?>) invoke("maybeMakePayout");
            assertEquals(false, payout.join());

            CompletableFuture<?> validation = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            assertFalse(validation.isDone());
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(popups.constructed().get(1)).warning(warning.capture());
            assertTrue(warning.getValue().contains("buyer-address"));
            assertTrue(warning.getValue().contains("seller-address"));
            ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(1)).onAction(action.capture());
            action.getValue().run();
            assertEquals(true, validation.join());
            assertTrue((boolean) invoke("isRefundValidationCurrent"));
            assertEquals(true, ((CompletableFuture<?>) invoke("maybeMakePayout")).join());

            Dispute replacement = mock(Dispute.class);
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(replacement)).thenReturn("subject");
            setField("dispute", replacement);
            Method approvalCheck = DisputeSummaryWindow.class.getDeclaredMethod("hasRefundClaimApproval",
                    Coin.class, Coin.class, boolean.class);
            approvalCheck.setAccessible(true);
            assertFalse((boolean) approvalCheck.invoke(window, Coin.valueOf(1_000), Coin.valueOf(500), false));
            setField("dispute", dispute);

            result.setSellerPayoutAmount(Coin.valueOf(501));
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            result.setSellerPayoutAmount(Coin.valueOf(500));
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("changed");
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
        }
    }

    @Test
    void cancellingTwoRecipientConfirmationStopsTheCloseAttempt() throws Exception {
        result.setSellerPayoutAmount(Coin.valueOf(500));
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            CompletableFuture<?> outcome = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            assertFalse(outcome.isDone());
            ArgumentCaptor<Runnable> close = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(0)).onClose(close.capture());
            close.getValue().run();
            assertEquals(false, outcome.join());
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            verify(manager, never()).requestBlockchainTransactions(any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "false, allocation", "false, subject", "false, malformed", "false, singleRecipient",
            "true, allocation", "true, subject", "true, malformed",
            "false, row", "true, row", "false, eligibility", "true, eligibility"
    })
    void changedAndRestoredClaimRequiresFreshManualApproval(boolean legacyClaim, String change) throws Exception {
        result.setSellerPayoutAmount(Coin.valueOf(500));
        when(manager.requiresLegacyRefundClaimVerification(dispute)).thenReturn(legacyClaim);
        when(manager.isRefundEvidenceValidationSkipped()).thenReturn(true);
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            CompletableFuture<?> first = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            ArgumentCaptor<Runnable> confirm = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(0)).onAction(confirm.capture());
            confirm.getValue().run();
            assertEquals(true, first.join());
            assertTrue((boolean) invoke("isRefundValidationCurrent"));

            switch (change) {
                case "allocation" -> result.setSellerPayoutAmount(Coin.valueOf(501));
                case "subject" -> signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute))
                        .thenReturn("changed");
                case "malformed" -> signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute))
                        .thenThrow(new IllegalArgumentException("Malformed subject"));
                case "singleRecipient" -> result.setSellerPayoutAmount(Coin.ZERO);
                case "row" -> {
                    Dispute replacement = mock(Dispute.class);
                    when(replacement.getSupportType()).thenReturn(SupportType.REFUND);
                    setField("dispute", replacement);
                }
                case "eligibility" -> when(manager.requiresLegacyRefundClaimVerification(dispute))
                        .thenReturn(!legacyClaim);
                default -> throw new AssertionError(change);
            }
            assertEquals(change.equals("singleRecipient"), invoke("isRefundValidationCurrent"));

            setField("dispute", dispute);
            when(manager.requiresLegacyRefundClaimVerification(dispute)).thenReturn(legacyClaim);
            result.setSellerPayoutAmount(Coin.valueOf(500));
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            Method payoutCheck = DisputeSummaryWindow.class.getDeclaredMethod("isRefundValidationCurrent",
                    Coin.class, Coin.class);
            payoutCheck.setAccessible(true);
            assertFalse((boolean) payoutCheck.invoke(window, Coin.valueOf(1_000), Coin.valueOf(500)));

            CompletableFuture<?> retry = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            assertFalse(retry.isDone());
            ArgumentCaptor<Runnable> freshConfirm = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(popups.constructed().size() - 1)).onAction(freshConfirm.capture());
            freshConfirm.getValue().run();
            assertEquals(true, retry.join());
            assertTrue((boolean) invoke("isRefundValidationCurrent"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"close", "result", "payout"})
    void malformedSubjectDiscardsApprovalBeforeClaimVerificationCanFail(String boundary) throws Exception {
        result.setSellerPayoutAmount(Coin.valueOf(500));
        when(manager.isRefundEvidenceValidationSkipped()).thenReturn(true);
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            confirmRefundClaim(popups);
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute))
                    .thenThrow(new IllegalArgumentException("Malformed subject"));
            doThrow(new IllegalArgumentException("Invalid claim"))
                    .when(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.valueOf(500));

            switch (boundary) {
                case "close" -> assertEquals(false, ((CompletableFuture<?>) invoke("maybeCheckTransactions")).join());
                case "result" -> assertFalse((boolean) invoke("isRefundValidationCurrent"));
                case "payout" -> {
                    Method payoutCheck = DisputeSummaryWindow.class.getDeclaredMethod("isRefundValidationCurrent",
                            Coin.class, Coin.class);
                    payoutCheck.setAccessible(true);
                    assertFalse((boolean) payoutCheck.invoke(window, Coin.valueOf(1_000), Coin.valueOf(500)));
                }
                default -> throw new AssertionError(boundary);
            }

            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            doNothing().when(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.valueOf(500));
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
        }
    }

    @Test
    void subjectChangeAfterReservationDiscardsManualApprovalAndPreventsCommit() throws Exception {
        when(manager.requiresLegacyRefundClaimVerification(dispute)).thenReturn(true);
        when(manager.isRefundEvidenceValidationSkipped()).thenReturn(true);
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            confirmRefundClaim(popups);
            CompletableFuture<Boolean> outcome = new CompletableFuture<>();
            Runnable persisted = beginPayoutReservation(mock(Transaction.class), outcome);
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("changed");

            persisted.run();

            verify(wallet, never()).commitTx(any());
            verify(tradeWallet, never()).broadcastTx(any(), any());
            assertFalse(outcome.isDone());
            ArgumentCaptor<Runnable> dismiss = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(1)).onClose(dismiss.capture());
            dismiss.getValue().run();
            assertEquals(false, outcome.join());
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
        }
    }

    private void confirmRefundClaim(MockedConstruction<Popup> popups) throws Exception {
        CompletableFuture<?> validation = (CompletableFuture<?>) invoke("maybeCheckTransactions");
        assertFalse(validation.isDone());
        ArgumentCaptor<Runnable> confirm = ArgumentCaptor.forClass(Runnable.class);
        verify(popups.constructed().get(popups.constructed().size() - 1)).onAction(confirm.capture());
        confirm.getValue().run();
        assertEquals(true, validation.join());
    }

    @Test
    void skippingPayoutDoesNotBlockTheNextPayoutPrompt() throws Exception {
        when(feeEstimator.getEstimatedFeeAndTxVsize(any(), any()))
                .thenReturn(new Tuple2<>(Coin.valueOf(100), 100));
        try (MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            CompletableFuture<?> first = (CompletableFuture<?>) invoke("maybeMakePayout");
            assertFalse(first.isDone());
            ArgumentCaptor<Runnable> skip = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(0)).onSecondaryAction(skip.capture());
            skip.getValue().run();
            assertEquals(true, first.join());

            CompletableFuture<?> next = (CompletableFuture<?>) invoke("maybeMakePayout");
            assertFalse(next.isDone());
            assertEquals(2, popups.constructed().size());
        }
    }

    @Test
    void legacyGraceRequiresConfirmationAndRejectsChangedAllocation() throws Exception {
        when(manager.requiresLegacyRefundClaimVerification(dispute)).thenReturn(true);
        when(manager.isRefundEvidenceValidationSkipped()).thenReturn(true);
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            CompletableFuture<?> outcome = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            assertFalse(outcome.isDone());
            verify(manager, never()).requestBlockchainTransactions(any(), any(), any(), any());
            ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(1)).onAction(action.capture());
            action.getValue().run();
            assertEquals(true, outcome.join());
            assertTrue((boolean) invoke("isRefundValidationCurrent"));

            result.setSellerPayoutAmount(Coin.valueOf(500));
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            result.setSellerPayoutAmount(Coin.ZERO);
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("changed");
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            when(manager.requiresLegacyRefundClaimVerification(dispute)).thenReturn(false);
            doThrow(new IllegalArgumentException("grace period expired"))
                    .when(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.ZERO);
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
        }
    }

    @Test
    void cancellingLegacyConfirmationStopsTheCloseAttempt() throws Exception {
        when(manager.requiresLegacyRefundClaimVerification(dispute)).thenReturn(true);
        try (MockedStatic<RefundClaimSignature> signature = mockStatic(RefundClaimSignature.class);
             MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            signature.when(() -> RefundClaimSignature.getClaimSubjectHash(dispute)).thenReturn("subject");
            CompletableFuture<?> outcome = (CompletableFuture<?>) invoke("maybeCheckTransactions");
            ArgumentCaptor<Runnable> close = ArgumentCaptor.forClass(Runnable.class);
            verify(popups.constructed().get(0)).onClose(close.capture());
            close.getValue().run();
            assertEquals(false, outcome.join());
            verify(manager, never()).requestBlockchainTransactions(any(), any(), any(), any());
        }
    }

    @Test
    void validClaimWithoutValidatedTransactionsStillCannotAuthorizeResult() throws Exception {
        setField("refundValidationResult", null);
        try (MockedConstruction<Popup> popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            assertFalse((boolean) invoke("isRefundValidationCurrent"));
            verify(manager).verifyRefundClaimForPayout(dispute, Coin.valueOf(1_000), Coin.ZERO);
            assertEquals(1, popups.constructed().size());
        }
    }

    private Object invoke(String methodName) throws Exception {
        Method method = DisputeSummaryWindow.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(window);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DisputeSummaryWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(window, value);
    }
}
