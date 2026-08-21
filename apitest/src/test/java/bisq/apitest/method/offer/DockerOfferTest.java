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

package bisq.apitest.method.offer;

import bisq.apitest.method.DockerMethodTest;
import bisq.cli.GrpcClient;
import bisq.proto.grpc.OfferInfo;
import protobuf.PaymentAccount;

import java.util.function.Predicate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;

import java.util.Locale;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static java.lang.String.format;

/**
 * Shared base for the docker-stack-ported offer tests. Provides:
 *  - F2F payment-account cache (per country code, lazily created on Alice).
 *  - On-demand XMR + legacy BSQ payment-account creation.
 *  - {@code @AfterEach} hook that cancels every open offer + bsq-swap offer the test
 *    left behind on Alice and Bob, so the next test starts from a clean offer book.
 *  - Convenience constants (ACTIVATE_OFFER, DEACTIVATE_OFFER, NO_TRIGGER_PRICE)
 *    and the {@code calcPriceAsString} helper.
 */
public abstract class DockerOfferTest extends DockerMethodTest {

    protected static final int ACTIVATE_OFFER = 1;
    protected static final int DEACTIVATE_OFFER = 0;
    protected static final String NO_TRIGGER_PRICE = "0";

    /** Block until {@code aliceClient.getOffer(id)} matches the predicate. */
    protected static void awaitOffer(String id, Predicate<OfferInfo> pred, String label) {
        awaitCond(() -> pred.test(aliceClient.getOffer(id)), label);
    }

    protected static final Map<String, PaymentAccount> f2fCache = new HashMap<>();

    protected static PaymentAccount alicesXmrAcct;
    protected static PaymentAccount bobsXmrAcct;
    protected static PaymentAccount alicesLegacyBsqAcct;
    protected static PaymentAccount bobsLegacyBsqAcct;

    protected PaymentAccount getOrCreateF2F(String countryCode) {
        return f2fCache.computeIfAbsent(countryCode, cc -> createDummyF2FAccount(aliceClient, cc));
    }

    // Use proto types directly. PaymentAccount.fromProto pulls in FiatCurrency which
    // depends on JavaFX, not available on this test classpath. Tests only need .getId().

    protected static void ensureXmrAccounts() {
        if (alicesXmrAcct == null) {
            alicesXmrAcct = aliceClient.createCryptoCurrencyPaymentAccount(
                    "Alice's XMR Account",
                    XMR,
                    "44G4jWmSvTEfifSUZzTDnJVLPvYATmq9XhhtDqUof1BGCLceG82EQsVYG9Q9GN4bJcjbAJEc1JD1m5G7iK4UPZqACubV4Mq",
                    false);
        }
        if (bobsXmrAcct == null) {
            bobsXmrAcct = bobClient.createCryptoCurrencyPaymentAccount(
                    "Bob's XMR Account",
                    XMR,
                    "4BDRhdSBKZqAXs3PuNTbMtaXBNqFj5idC2yMVnQj8Rm61AyKY8AxLTt9vGRJ8pwcG4EtpyD8YpGqdZWCZ2VZj6yVBN2RVKs",
                    false);
        }
    }

    protected static void ensureLegacyBsqAccounts() {
        if (alicesLegacyBsqAcct == null) {
            alicesLegacyBsqAcct = aliceClient.createCryptoCurrencyPaymentAccount(
                    "Alice's Legacy BSQ Account",
                    BSQ,
                    aliceClient.getUnusedBsqAddress(),
                    false);
        }
        if (bobsLegacyBsqAcct == null) {
            bobsLegacyBsqAcct = bobClient.createCryptoCurrencyPaymentAccount(
                    "Bob's Legacy BSQ Account",
                    BSQ,
                    bobClient.getUnusedBsqAddress(),
                    false);
        }
    }

    /**
     * Currencies whose offer book is swept in {@link #cancelAllOffers()}. Subclasses
     * that exercise an exotic ccy should add it here in a {@code @BeforeAll} hook to
     * make sure cleanup catches any stranded offer for that ccy.
     */
    protected static final Set<String> CCYS_TO_SWEEP = new HashSet<>();

    static {
        // Covers every ccy/asset used by ported offer + trade tests. Inexpensive to query;
        // missing one only means a stranded offer lingers into the next test, so we err
        // on the side of being permissive.
        CCYS_TO_SWEEP.add("USD"); CCYS_TO_SWEEP.add("EUR"); CCYS_TO_SWEEP.add("GBP");
        CCYS_TO_SWEEP.add("CAD"); CCYS_TO_SWEEP.add("RUB"); CCYS_TO_SWEEP.add("MXN");
        CCYS_TO_SWEEP.add("CHF"); CCYS_TO_SWEEP.add("AUD"); CCYS_TO_SWEEP.add("BRL");
        CCYS_TO_SWEEP.add(BSQ);   CCYS_TO_SWEEP.add(XMR);
    }

    @AfterEach
    public void cancelAllOffers() {
        for (GrpcClient c : new GrpcClient[]{aliceClient, bobClient}) {
            List<String> ids = new ArrayList<>();
            try {
                for (String ccy : CCYS_TO_SWEEP) {
                    for (OfferInfo o : c.getMyOffersSortedByDate(ccy)) ids.add(o.getId());
                }
                for (OfferInfo o : c.getMyBsqSwapBsqOffersSortedByDate()) ids.add(o.getId());
            } catch (RuntimeException ignored) {
                // Best effort — don't mask a real test failure with cleanup noise.
            }
            for (String id : ids) {
                try { c.cancelOffer(id); } catch (RuntimeException ignored) { }
            }
        }
        // Confirm any pending cancel/maker-fee refund txs so the next test sees freed
        // BTC immediately. One block is enough on regtest.
        try { mineBlocks(1); } catch (RuntimeException ignored) { }
    }

    /**
     * Add {@code delta} to {@code base} and format with {@code precision} decimal
     * places. Scale-based rounding (HALF_UP) — significant-digit rounding via
     * {@code MathContext} would produce different values than the format string
     * "%.<precision>f" expects.
     */
    protected static String calcPriceAsString(double base, double delta, int precision) {
        var p = BigDecimal.valueOf(base)
                .add(BigDecimal.valueOf(delta))
                .setScale(precision, RoundingMode.HALF_UP);
        return format(Locale.ROOT, "%." + precision + "f", p);
    }
}
