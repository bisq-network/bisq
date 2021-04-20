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

import bisq.proto.grpc.TradeInfo;

import io.grpc.StatusRuntimeException;

import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.FIAT_SENT;
import static bisq.core.trade.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG;
import static bisq.core.trade.Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN;
import static bisq.core.trade.Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.method.offer.AbstractOfferTest;

// https://github.com/ghubstan/bisq/blob/master/cli/src/main/java/bisq/cli/TradeFormat.java

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeBuyBSQOfferTest extends AbstractTradeTest {

    // Alice is maker / bsq buyer (btc seller), Bob is taker / bsq seller (btc buyer).

    // Maker and Taker fees are in BSQ.
    private static final String TRADE_FEE_CURRENCY_CODE = BSQ;

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
        createBsqPaymentAccounts();
        EXPECTED_PROTOCOL_STATUS.init();
    }

    @Test
    @Order(1)
    public void testTakeAlicesSellBTCForBSQOffer(final TestInfo testInfo) {
        try {
            // Alice is going to BUY BSQ, but the Offer direction = SELL because it is a
            // BTC trade;  Alice will SELL BTC for BSQ.  Bob will send Alice BSQ.
            // Confused me, but just need to remember there are only BTC offers.
            var btcTradeDirection = SELL.name();
            var alicesOffer = aliceClient.createFixedPricedOffer(btcTradeDirection,
                    BSQ,
                    15_000_000L,
                    7_500_000L,
                    "0.000035",   // FIXED PRICE IN BTC (satoshis) FOR 1 BSQ
                    getDefaultBuyerSecurityDepositAsPercent(),
                    alicesBsqAcct.getId(),
                    TRADE_FEE_CURRENCY_CODE);
            log.info("ALICE'S BUY BSQ (SELL BTC) OFFER:\n{}", formatOfferTable(singletonList(alicesOffer), BSQ));
            genBtcBlocksThenWait(1, 5000);
            var offerId = alicesOffer.getId();
            assertFalse(alicesOffer.getIsCurrencyForMakerFeeBtc());

            var alicesBsqOffers = aliceClient.getMyCryptoCurrencyOffers(btcTradeDirection, BSQ);
            assertEquals(1, alicesBsqOffers.size());

            var trade = takeAlicesOffer(offerId, bobsBsqAcct.getId(), TRADE_FEE_CURRENCY_CODE);
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            assertFalse(trade.getIsCurrencyForTakerFeeBtc());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 6000);
            alicesBsqOffers = aliceClient.getMyBsqOffersSortedByDate();
            assertEquals(0, alicesBsqOffers.size());

            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                trade = bobClient.getTrade(trade.getTradeId());

                if (!trade.getIsDepositConfirmed()) {
                    log.warn("Bob still waiting on trade {} tx {}: DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN, attempt # {}",
                            trade.getShortId(),
                            trade.getDepositTxId(),
                            i);
                    genBtcBlocksThenWait(1, 4000);
                    continue;
                } else {
                    EXPECTED_PROTOCOL_STATUS.setState(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN)
                            .setPhase(DEPOSIT_CONFIRMED)
                            .setDepositPublished(true)
                            .setDepositConfirmed(true);
                    verifyExpectedProtocolStatus(trade);
                    logTrade(log, testInfo, "Bob's view after taking offer and deposit confirmed", trade);
                    break;
                }
            }

            genBtcBlocksThenWait(1, 2500);

            if (!trade.getIsDepositConfirmed()) {
                fail(format("INVALID_PHASE for Bob's trade %s in STATE=%s PHASE=%s, deposit tx was never confirmed.",
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase()));
            }

            logTrade(log, testInfo, "Alice's Maker/Buyer View", aliceClient.getTrade(tradeId), true);
            logTrade(log, testInfo, "Bob's Taker/Seller View", bobClient.getTrade(tradeId), true);

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testBobsConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = bobClient.getTrade(tradeId);

            Predicate<TradeInfo> tradeStateAndPhaseCorrect = (t) ->
                    t.getState().equals(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.name())
                            && t.getPhase().equals(DEPOSIT_CONFIRMED.name());

            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                if (!tradeStateAndPhaseCorrect.test(trade)) {
                    log.warn("INVALID_PHASE for Bob's trade {} in STATE={} PHASE={}, cannot send payment started msg yet.",
                            trade.getShortId(),
                            trade.getState(),
                            trade.getPhase());
                    sleep(10_000);
                    trade = bobClient.getTrade(tradeId);
                    continue;
                } else {
                    break;
                }
            }

            if (!tradeStateAndPhaseCorrect.test(trade)) {
                fail(format("INVALID_PHASE for Bob's trade %s in STATE=%s PHASE=%s, could not send payment started msg.",
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase()));
            }

            sendBsqPayment(log, bobClient, trade);
            genBtcBlocksThenWait(1, 2500);
            bobClient.confirmPaymentStarted(trade.getTradeId());
            sleep(6000);

            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                trade = aliceClient.getTrade(tradeId);

                if (!trade.getIsFiatSent()) {
                    log.warn("Alice still waiting for trade {} SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG, attempt # {}",
                            trade.getShortId(),
                            i);
                    sleep(5000);
                    continue;
                } else {
                    // Warning:  trade.getOffer().getState() might be AVAILABLE, not OFFER_FEE_PAID.
                    EXPECTED_PROTOCOL_STATUS.setState(SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG)
                            .setPhase(FIAT_SENT)
                            .setFiatSent(true);
                    verifyExpectedProtocolStatus(trade);
                    logTrade(log, testInfo, "Alice's view after confirming fiat payment received", trade);
                    break;
                }
            }

            logTrade(log, testInfo, "Alice's Maker/Buyer View (Payment Sent)", aliceClient.getTrade(tradeId), true);
            logTrade(log, testInfo, "Bob's Taker/Seller View (Payment Sent)", bobClient.getTrade(tradeId), true);

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            var trade = aliceClient.getTrade(tradeId);

            Predicate<TradeInfo> tradeStateAndPhaseCorrect = (t) ->
                    t.getState().equals(SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.name())
                            && (t.getPhase().equals(PAYOUT_PUBLISHED.name()) || t.getPhase().equals(FIAT_SENT.name()));

            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                if (!tradeStateAndPhaseCorrect.test(trade)) {
                    log.warn("INVALID_PHASE for Alice's trade {} in STATE={} PHASE={}, cannot confirm payment received yet.",
                            trade.getShortId(),
                            trade.getState(),
                            trade.getPhase());
                    sleep(1000 * 10);
                    trade = aliceClient.getTrade(tradeId);
                    continue;
                } else {
                    break;
                }
            }

            if (!tradeStateAndPhaseCorrect.test(trade)) {
                fail(format("INVALID_PHASE for Alice's trade %s in STATE=%s PHASE=%s, cannot confirm payment received.",
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase()));
            }

            sleep(2000);
            verifyBsqPaymentHasBeenReceived(log, aliceClient, trade);

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

            logTrade(log, testInfo, "Alice's Maker/Buyer View (Payment Received)", aliceClient.getTrade(tradeId), true);
            logTrade(log, testInfo, "Bob's Taker/Seller View (Payment Received)", bobClient.getTrade(tradeId), true);

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testBobsKeepFunds(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1000);

            var trade = bobClient.getTrade(tradeId);
            logTrade(log, testInfo, "Alice's view before keeping funds", trade);

            bobClient.keepFunds(tradeId);
            genBtcBlocksThenWait(1, 1000);

            trade = bobClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's view after keeping funds", trade);

            logTrade(log, testInfo, "Alice's Maker/Buyer View (Done)", aliceClient.getTrade(tradeId), true);
            logTrade(log, testInfo, "Bob's Taker/Seller View (Done)", bobClient.getTrade(tradeId), true);

            var alicesBalances = aliceClient.getBalances();
            log.info("{} Alice's Current Balance:\n{}",
                    testName(testInfo),
                    formatBalancesTbls(alicesBalances));
            var bobsBalances = bobClient.getBalances();
            log.info("{} Bob's Current Balance:\n{}",
                    testName(testInfo),
                    formatBalancesTbls(bobsBalances));

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
