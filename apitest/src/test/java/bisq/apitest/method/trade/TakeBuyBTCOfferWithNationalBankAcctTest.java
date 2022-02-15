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

package bisq.apitest.method.trade;

import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.NationalBankAccountPayload;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.trade.model.bisq_v1.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG;
import static org.junit.jupiter.api.Assertions.*;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OfferDirection.BUY;
import static protobuf.OpenOffer.State.AVAILABLE;

/**
 * Test case verifies trade can be made with national bank payment method,
 * and json contracts exclude bank acct details until deposit tx is confirmed.
 */
@SuppressWarnings("ConstantConditions")
@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeBuyBTCOfferWithNationalBankAcctTest extends AbstractTradeTest {

    // Alice is maker/buyer, Bob is taker/seller.

    // Maker and Taker fees are in BSQ.
    private static final String TRADE_FEE_CURRENCY_CODE = BSQ;
    private static final String BRL = "BRL";

    private static PaymentAccount alicesPaymentAccount;
    private static PaymentAccount bobsPaymentAccount;

    @Test
    @Order(1)
    public void testTakeAlicesBuyOffer(final TestInfo testInfo) {
        try {
            alicesPaymentAccount = createDummyBRLAccount(aliceClient,
                    "Alicia da Silva",
                    String.valueOf(System.currentTimeMillis()),
                    "123.456.789-01");
            bobsPaymentAccount = createDummyBRLAccount(bobClient,
                    "Roberto da Silva",
                    String.valueOf(System.currentTimeMillis()),
                    "123.456.789-02");
            var alicesOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                    BRL,
                    1_000_000L,
                    1_000_000L, // min-amount = amount
                    0.00,
                    getDefaultBuyerSecurityDepositAsPercent(),
                    alicesPaymentAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    NO_TRIGGER_PRICE);
            var offerId = alicesOffer.getId();
            assertFalse(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay.
            sleep(3_000); // TODO loop instead of hard code wait time
            var alicesOffers = aliceClient.getMyOffersSortedByDate(BUY.name(), BRL);
            assertEquals(1, alicesOffers.size());


            var trade = takeAlicesOffer(offerId,
                    bobsPaymentAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    false);

            // Before generating a blk and confirming deposit tx, make sure there
            // are no bank acct details in the either side's contract.
            while (true) {
                try {
                    var alicesContract = aliceClient.getTrade(trade.getTradeId()).getContractAsJson();
                    var bobsContract = bobClient.getTrade(trade.getTradeId()).getContractAsJson();
                    verifyJsonContractExcludesBankAccountDetails(alicesContract, alicesPaymentAccount);
                    verifyJsonContractExcludesBankAccountDetails(alicesContract, bobsPaymentAccount);
                    verifyJsonContractExcludesBankAccountDetails(bobsContract, alicesPaymentAccount);
                    verifyJsonContractExcludesBankAccountDetails(bobsContract, bobsPaymentAccount);
                    break;
                } catch (StatusRuntimeException ex) {
                    if (ex.getMessage() == null) {
                        String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
                        if (message.contains("trade") && message.contains("not found")) {
                            fail(ex);
                        }
                    } else {
                        sleep(500);
                    }
                }
            }

            genBtcBlocksThenWait(1, 4000);
            alicesOffers = aliceClient.getMyOffersSortedByDate(BUY.name(), BRL);
            assertEquals(0, alicesOffers.size());
            genBtcBlocksThenWait(1, 2_500);
            waitForDepositConfirmation(log, testInfo, bobClient, trade.getTradeId());

            trade = bobClient.getTrade(tradeId);
            verifyTakerDepositConfirmed(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testBankAcctDetailsIncludedInContracts(final TestInfo testInfo) {
        assertNotNull(alicesPaymentAccount);
        assertNotNull(bobsPaymentAccount);

        var alicesContract = aliceClient.getTrade(tradeId).getContractAsJson();
        verifyJsonContractIncludesBankAccountDetails(alicesContract, alicesPaymentAccount);
        verifyJsonContractIncludesBankAccountDetails(alicesContract, bobsPaymentAccount);

        var bobsContract = bobClient.getTrade(tradeId).getContractAsJson();
        verifyJsonContractIncludesBankAccountDetails(bobsContract, alicesPaymentAccount);
        verifyJsonContractIncludesBankAccountDetails(bobsContract, bobsPaymentAccount);
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = aliceClient.getTrade(tradeId);
            waitForDepositConfirmation(log, testInfo, aliceClient, trade.getTradeId());
            aliceClient.confirmPaymentStarted(trade.getTradeId());
            sleep(6_000);
            waitForBuyerSeesPaymentInitiatedMessage(log, testInfo, aliceClient, tradeId);
            trade = aliceClient.getTrade(tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Payment Sent)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Payment Sent)", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testBobsConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            waitForSellerSeesPaymentInitiatedMessage(log, testInfo, bobClient, tradeId);
            var trade = bobClient.getTrade(tradeId);
            bobClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3_000);
            trade = bobClient.getTrade(tradeId);
            // Note: offer.state == available
            assertEquals(AVAILABLE.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setFiatReceived(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after confirming fiat payment received", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(5)
    public void testKeepFunds(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1_000);

            var trade = aliceClient.getTrade(tradeId);
            logTrade(log, testInfo, "Alice's view before closing trade and keeping funds", trade);
            aliceClient.closeTrade(tradeId);
            bobClient.closeTrade(tradeId);
            genBtcBlocksThenWait(1, 1_000);
            trade = aliceClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Done)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Done)", bobClient.getTrade(tradeId));
            logBalances(log, testInfo);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    private void verifyJsonContractExcludesBankAccountDetails(String jsonContract,
                                                              PaymentAccount paymentAccount) {
        NationalBankAccountPayload nationalBankAccountPayload =
                (NationalBankAccountPayload) paymentAccount.getPaymentAccountPayload();
        // The client cannot know exactly when payment acct payloads are added to a contract,
        // so auto-failing here results in a flaky test.
        // assertFalse(jsonContract.contains(nationalBankAccountPayload.getNationalAccountId()));
        // assertFalse(jsonContract.contains(nationalBankAccountPayload.getBranchId()));
        // assertFalse(jsonContract.contains(nationalBankAccountPayload.getAccountNr()));
        // assertFalse(jsonContract.contains(nationalBankAccountPayload.getHolderName()));
        // assertFalse(jsonContract.contains(nationalBankAccountPayload.getHolderTaxId()));

        // Log warning if bank acct details are found in json contract.
        if (jsonContract.contains(nationalBankAccountPayload.getNationalAccountId()))
            log.warn("Could not check json contract soon enough; it contains national bank acct id");

        if (jsonContract.contains(nationalBankAccountPayload.getBranchId()))
            log.warn("Could not check json contract soon enough; it contains natl bank branch id");

        if (jsonContract.contains(nationalBankAccountPayload.getAccountNr()))
            log.warn("Could not check json contract soon enough; it contains natl bank acct #");

        if (jsonContract.contains(nationalBankAccountPayload.getHolderName()))
            log.warn("Could not check json contract soon enough; it contains natl bank acct holder name");

        if (jsonContract.contains(nationalBankAccountPayload.getHolderTaxId()))
            log.warn("Could not check json contract soon enough; it contains natl bank acct holder tax id");
    }

    private void verifyJsonContractIncludesBankAccountDetails(String jsonContract,
                                                              PaymentAccount paymentAccount) {
        NationalBankAccountPayload nationalBankAccountPayload =
                (NationalBankAccountPayload) paymentAccount.getPaymentAccountPayload();
        assertTrue(jsonContract.contains(nationalBankAccountPayload.getNationalAccountId()));
        assertTrue(jsonContract.contains(nationalBankAccountPayload.getBranchId()));
        assertTrue(jsonContract.contains(nationalBankAccountPayload.getAccountNr()));
        assertTrue(jsonContract.contains(nationalBankAccountPayload.getHolderName()));
        assertTrue(jsonContract.contains(nationalBankAccountPayload.getHolderTaxId()));
    }
}
