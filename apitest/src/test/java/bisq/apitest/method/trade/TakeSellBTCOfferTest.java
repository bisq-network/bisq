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

import bisq.proto.grpc.BtcBalanceInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.trade.Trade.Phase.*;
import static bisq.core.trade.Trade.State.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OpenOffer.State.AVAILABLE;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeSellBTCOfferTest extends AbstractTradeTest {

    // Alice is seller, Bob is buyer.

    // Maker and Taker fees are in BTC.
    private static final String TRADE_FEE_CURRENCY_CODE = "btc";

    private static final String WITHDRAWAL_TX_MEMO = "Bob's trade withdrawal";

    @Test
    @Order(1)
    public void testTakeAlicesSellOffer(final TestInfo testInfo) {
        try {
            PaymentAccount alicesUsdAccount = createDummyF2FAccount(aliceClient, "US");
            var alicesOffer = aliceClient.createMarketBasedPricedOffer("sell",
                    "usd",
                    12500000L,
                    12500000L, // min-amount = amount
                    0.00,
                    getDefaultBuyerSecurityDepositAsPercent(),
                    alicesUsdAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE);
            var offerId = alicesOffer.getId();
            assertTrue(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay, but taking sell offers
            // seems to require more time to prepare.
            sleep(3000); // TODO loop instead of hard code wait time
            var alicesUsdOffers = aliceClient.getMyOffersSortedByDate("sell", "usd");
            assertEquals(1, alicesUsdOffers.size());

            PaymentAccount bobsUsdAccount = createDummyF2FAccount(bobClient, "US");
            var trade = takeAlicesOffer(offerId, bobsUsdAccount.getId(), TRADE_FEE_CURRENCY_CODE);
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            assertTrue(trade.getIsCurrencyForTakerFeeBtc());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 4000);
            var takeableUsdOffers = bobClient.getOffersSortedByDate("sell", "usd");
            assertEquals(0, takeableUsdOffers.size());

            trade = bobClient.getTrade(trade.getTradeId());
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG)
                    .setPhase(DEPOSIT_PUBLISHED)
                    .setDepositPublished(true);
            verifyExpectedProtocolStatus(trade);

            logTrade(log, testInfo, "Bob's view after taking offer and sending deposit", trade);

            genBtcBlocksThenWait(1, 1000);
            trade = bobClient.getTrade(trade.getTradeId());
            EXPECTED_PROTOCOL_STATUS.setState(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN)
                    .setPhase(DEPOSIT_CONFIRMED)
                    .setDepositConfirmed(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after deposit is confirmed", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testBobsConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = bobClient.getTrade(tradeId);
            bobClient.confirmPaymentStarted(tradeId);
            sleep(3000);

            trade = bobClient.getTrade(tradeId);
            // Note: offer.state == available
            assertEquals(AVAILABLE.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG)
                    .setPhase(FIAT_SENT)
                    .setFiatSent(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after confirming fiat payment sent", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            var trade = aliceClient.getTrade(tradeId);
            aliceClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3000);

            trade = aliceClient.getTrade(tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setFiatReceived(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's view after confirming fiat payment received", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testBobsBtcWithdrawalToExternalAddress(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1000);

            var trade = bobClient.getTrade(tradeId);
            logTrade(log, testInfo, "Bob's view before withdrawing funds to external wallet", trade);

            String toAddress = bitcoinCli.getNewBtcAddress();
            bobClient.withdrawFunds(tradeId, toAddress, WITHDRAWAL_TX_MEMO);

            genBtcBlocksThenWait(1, 1000);

            trade = bobClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(WITHDRAW_COMPLETED)
                    .setPhase(WITHDRAWN)
                    .setWithdrawn(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after withdrawing funds to external wallet", trade);
            BtcBalanceInfo currentBalance = bobClient.getBtcBalances();
            log.debug("{} Bob's current available balance: {} BTC",
                    testName(testInfo),
                    formatSatoshis(currentBalance.getAvailableBalance()));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
