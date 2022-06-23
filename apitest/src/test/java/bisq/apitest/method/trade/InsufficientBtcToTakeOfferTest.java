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
import bisq.proto.grpc.OfferInfo;

import io.grpc.StatusRuntimeException;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.USD;
import static bisq.cli.CurrencyFormat.formatBtc;
import static bisq.cli.table.builder.TableType.BTC_BALANCE_TBL;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.OfferDirection.BUY;



import bisq.cli.table.builder.TableBuilder;

/**
 * This test should not be @Disabled, nor run from the scenario package's TradeTest suite.
 * The risk of causing all test suites to fail due to insufficient funds is too great,
 * as of 22-May-2022.
 */
@SuppressWarnings("ConstantConditions")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InsufficientBtcToTakeOfferTest extends AbstractTradeTest {

    private static final String TRADE_FEE_CURRENCY_CODE = BSQ;

    // Bob's BTC wallet is nearly emptied in the test case:  most is sent to Alice,
    // then Bob tries to take an offer, resulting in a NotAvailableException.
    // Alice returns the exchanged BTC at the end of the test.
    private String sendAmount = "0";

    @Test
    @Order(1)
    public void testTakeOfferWithInsufficientBTC() {
        try {
            PaymentAccount alicesUsdAccount = createDummyF2FAccount(aliceClient, "US");
            PaymentAccount bobsUsdAccount = createDummyF2FAccount(bobClient, "US");

            // Empty Bob's BTC wallet; send almost all of it to Alice.
            long bobsAvailableSats = bobClient.getBtcBalances().getAvailableBalance();
            long satsToLeaveInBobsWallet = 2000000;
            long statsToSendToAlice = abs(satsToLeaveInBobsWallet - bobsAvailableSats);
            sendAmount = formatBtc(statsToSendToAlice);
            String aliceAddress = aliceClient.getUnusedBtcAddress();
            bobClient.sendBtc(aliceAddress, sendAmount, "", "");
            genBtcBlocksThenWait(1, 2_500);
            showBalances("after emptying Bob's BTC wallet");

            var alicesOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                    USD,
                    12_500_000L,
                    12_500_000L, // min-amount = amount
                    0.00,
                    defaultBuyerSecurityDepositPct.get(),
                    alicesUsdAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    NO_TRIGGER_PRICE);
            var offerId = alicesOffer.getId();
            assertFalse(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2-second delay.
            sleep(3_000); // TODO loop instead of hard code a wait time
            List<OfferInfo> alicesUsdOffers = aliceClient.getMyOffersSortedByDate(BUY.name(), USD);
            assertEquals(1, alicesUsdOffers.size());

            // Try to take the offer 5x, fail each time, assert offer remains available.
            for (int i = 0; i < 5; i++) {
                Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                        takeAlicesOffer(offerId,
                                bobsUsdAccount.getId(),
                                TRADE_FEE_CURRENCY_CODE,
                                false));
                String expectedExceptionMessage =
                        format("UNAVAILABLE: wallet has insufficient btc to take offer with id '%s'", offerId);
                log.debug(exception.getMessage());
                assertEquals(expectedExceptionMessage, exception.getMessage());

                // Alice's offer can still be looked up by Alice.
                alicesUsdOffers = aliceClient.getMyOffersSortedByDate(BUY.name(), USD);
                assertEquals(1, alicesUsdOffers.size());
                // Offer should still be available to Bob.
                var availableOffer = bobClient.getOffer(offerId);
                log.debug("Offer still available:\n{}", toOfferTable.apply(availableOffer));

                sleep(3_000);
            }

        } catch (StatusRuntimeException e) {
            fail(e);
        }

        showBalances("after failed take offer attempts");

        // Send Bob's BTC back to him.
        String bobsAddress = bobClient.getUnusedBtcAddress();
        aliceClient.sendBtc(bobsAddress, sendAmount, "", "");
        genBtcBlocksThenWait(1, 2_500);

        showBalances("after returning Bob's BTC");
    }

    private void showBalances(String msg) {
        if (log.isDebugEnabled()) {
            BtcBalanceInfo alicesBalances = aliceClient.getBtcBalances();
            log.debug("Alice's BTC Balances {}:\n{}",
                    msg,
                    new TableBuilder(BTC_BALANCE_TBL, alicesBalances).build());

            BtcBalanceInfo bobsBalances = bobClient.getBtcBalances();
            log.debug("Bob's BTC Balances {}:\n{}",
                    msg,
                    new TableBuilder(BTC_BALANCE_TBL, bobsBalances).build());
        }
    }
}
