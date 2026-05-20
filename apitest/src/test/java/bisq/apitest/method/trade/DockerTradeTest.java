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

import bisq.apitest.dao.DaoTestUtils;
import bisq.apitest.method.DockerMethodTest;
import bisq.cli.GrpcClient;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;
import protobuf.PaymentAccount;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh-stack trade test base. Each test class that extends this runs in its own
 * JVM against a freshly reset docker stack — so balance assertions can rely on
 * dao-setup baselines (Alice 10 BTC / 1,000,000 BSQ, Bob 10 BTC / 1,500,000 BSQ).
 *
 * <p>Exposes:
 *  - {@link #ensureF2FAccounts(String)}: create matching F2F accounts on both sides
 *  - {@link #ensureLegacyBsqAccounts()} / {@link #ensureXmrAccounts()}: matching crypto accounts
 *  - {@link #runV1Trade}: drive the full v1 trade protocol (offer → take → pay → close)
 */
public abstract class DockerTradeTest extends DockerMethodTest {

    protected static final String NO_TRIGGER_PRICE = "0";

    /** Block until bob's daemon sees alice's offer in its offer book. P2P propagation
     *  latency depends on the seednode connection, but the predicate is deterministic. */
    protected static void awaitBobSeesOffer(String offerId, String currencyCode) {
        awaitCond(() -> {
            try {
                return offerId.equals(bobClient.getOffer(offerId).getId());
            } catch (RuntimeException ex) {
                return false;
            }
        }, "bob sees offer " + offerId + " (" + currencyCode + ")");
    }

    protected static PaymentAccount alicesF2F;
    protected static PaymentAccount bobsF2F;
    private static String f2fCountry;
    protected static PaymentAccount alicesLegacyBsqAcct;
    protected static PaymentAccount bobsLegacyBsqAcct;
    protected static PaymentAccount alicesXmrAcct;
    protected static PaymentAccount bobsXmrAcct;

    /**
     * Ensure matching F2F payment accounts for {@code countryCode} exist on both
     * sides. If a different country was cached previously, recreate the accounts
     * so the requested country is honored instead of silently reusing the stale pair.
     */
    protected static void ensureF2FAccounts(String countryCode) {
        if (alicesF2F == null || bobsF2F == null
                || f2fCountry == null || !f2fCountry.equalsIgnoreCase(countryCode)) {
            alicesF2F = createF2F(aliceClient, countryCode, "alice");
            bobsF2F = createF2F(bobClient, countryCode, "bob");
            f2fCountry = countryCode;
        }
    }

    @SuppressWarnings("ConstantConditions")
    protected static void ensureLegacyBsqAccounts() {
        if (alicesLegacyBsqAcct == null) {
            alicesLegacyBsqAcct = aliceClient.createCryptoCurrencyPaymentAccount(
                    "Alice's Legacy BSQ Account", BSQ, aliceClient.getUnusedBsqAddress(), false);
        }
        if (bobsLegacyBsqAcct == null) {
            bobsLegacyBsqAcct = bobClient.createCryptoCurrencyPaymentAccount(
                    "Bob's Legacy BSQ Account", BSQ, bobClient.getUnusedBsqAddress(), false);
        }
    }

    @SuppressWarnings("ConstantConditions")
    protected static void ensureXmrAccounts() {
        if (alicesXmrAcct == null) {
            alicesXmrAcct = aliceClient.createCryptoCurrencyPaymentAccount(
                    "Alice's XMR Account", XMR,
                    "44G4jWmSvTEfifSUZzTDnJVLPvYATmq9XhhtDqUof1BGCLceG82EQsVYG9Q9GN4bJcjbAJEc1JD1m5G7iK4UPZqACubV4Mq",
                    false);
        }
        if (bobsXmrAcct == null) {
            bobsXmrAcct = bobClient.createCryptoCurrencyPaymentAccount(
                    "Bob's XMR Account", XMR,
                    "4BDRhdSBKZqAXs3PuNTbMtaXBNqFj5idC2yMVnQj8Rm61AyKY8AxLTt9vGRJ8pwcG4EtpyD8YpGqdZWCZ2VZj6yVBN2RVKs",
                    false);
        }
    }

    private static PaymentAccount createF2F(GrpcClient c, String country, String label) {
        String json = format("{\"_COMMENTS_\":\"trade-test\","
                        + "\"paymentMethodId\":\"F2F\","
                        + "\"accountName\":\"%s-f2f-%s-%d\","
                        + "\"city\":\"Anytown\","
                        + "\"contact\":\"Morse\","
                        + "\"country\":\"%s\","
                        + "\"extraInfo\":\"x\"}",
                label, country, System.nanoTime(), country.toUpperCase());
        return c.createPaymentAccount(json);
    }

    /**
     * Drive a full v1 trade flow alice (maker) → bob (taker). Mirrors
     * {@code TradeScenarioTest.runV1Trade} so balance/state expectations match the
     * existing DAO suite. Returns the executed trade for follow-up asserts.
     */
    protected TradeInfo runV1Trade(OfferInfo offer, boolean bobIsBtcBuyer,
                                   String takerPaymentAccountId, String currency, long btcAmount) {
        long aliceBtcBefore = totalBtc(aliceClient);
        long bobBtcBefore = totalBtc(bobClient);

        if (!offer.getOfferFeePaymentTxId().isEmpty()) {
            chain.confirmTx(aliceClient, offer.getOfferFeePaymentTxId());
        }
        DaoTestUtils.await(() -> bobClient.getOffers("BUY", currency).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId()))
                        || bobClient.getOffers("SELL", currency).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId())),
                60_000, "bob sees offer " + offer.getId());

        TradeInfo trade = bobClient.takeOffer(offer.getId(), takerPaymentAccountId, "BTC", btcAmount);
        assertNotNull(trade);
        DaoTestUtils.await(() -> !bobClient.getTrade(trade.getTradeId()).getTakerFeeTxId().isEmpty(),
                30_000, "bob has taker_fee_tx_id");
        String takerFeeTxId = bobClient.getTrade(trade.getTradeId()).getTakerFeeTxId();
        chain.confirmTx(bobClient, takerFeeTxId);
        DaoTestUtils.await(() -> !bobClient.getTrade(trade.getTradeId()).getDepositTxId().isEmpty(),
                30_000, "bob has deposit_tx_id");
        String depositTxId = bobClient.getTrade(trade.getTradeId()).getDepositTxId();
        chain.confirmTx(bobClient, depositTxId);
        DaoTestUtils.await(() -> bobClient.getTrade(trade.getTradeId()).getIsDepositConfirmed(),
                30_000, "deposit confirmed");

        GrpcClient btcBuyer = bobIsBtcBuyer ? bobClient : aliceClient;
        GrpcClient btcSeller = bobIsBtcBuyer ? aliceClient : bobClient;

        btcBuyer.confirmPaymentStarted(trade.getTradeId());
        DaoTestUtils.await(() -> btcSeller.getTrade(trade.getTradeId()).getIsPaymentStartedMessageSent(),
                60_000, "seller sees payment-started");
        btcSeller.confirmPaymentReceived(trade.getTradeId());
        DaoTestUtils.await(() -> !btcSeller.getTrade(trade.getTradeId()).getPayoutTxId().isEmpty(),
                30_000, "seller has payout_tx_id");
        chain.confirmTx(btcSeller, btcSeller.getTrade(trade.getTradeId()).getPayoutTxId());
        DaoTestUtils.await(() -> btcBuyer.getTrade(trade.getTradeId()).getIsPayoutPublished(),
                30_000, "payout published");

        btcBuyer.closeTrade(trade.getTradeId());
        chain.generateBlocks(1);

        long aliceBtcAfter = totalBtc(aliceClient);
        long bobBtcAfter = totalBtc(bobClient);
        if (bobIsBtcBuyer) {
            assertTrue(bobBtcAfter > bobBtcBefore,
                    "bob (btc buyer) total BTC must increase: " + bobBtcBefore + " → " + bobBtcAfter);
            assertTrue(aliceBtcAfter < aliceBtcBefore,
                    "alice (btc seller) total BTC must decrease: " + aliceBtcBefore + " → " + aliceBtcAfter);
        } else {
            assertTrue(aliceBtcAfter > aliceBtcBefore,
                    "alice (btc buyer) total BTC must increase: " + aliceBtcBefore + " → " + aliceBtcAfter);
            assertTrue(bobBtcAfter < bobBtcBefore,
                    "bob (btc seller) total BTC must decrease: " + bobBtcBefore + " → " + bobBtcAfter);
        }
        return trade;
    }

    protected static long totalBtc(GrpcClient c) {
        var b = c.getBalances().getBtc();
        return b.getAvailableBalance() + b.getReservedBalance() + b.getLockedBalance();
    }
}
