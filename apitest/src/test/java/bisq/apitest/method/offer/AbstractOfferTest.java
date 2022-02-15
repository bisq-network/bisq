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

import bisq.core.offer.OfferDirection;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.cli.table.builder.TableType.OFFER_TBL;
import static bisq.common.util.MathUtils.exactMultiply;
import static java.lang.String.format;
import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.method.MethodTest;
import bisq.cli.CliMain;
import bisq.cli.GrpcClient;
import bisq.cli.table.builder.TableBuilder;

@Slf4j
public abstract class AbstractOfferTest extends MethodTest {

    protected static final int ACTIVATE_OFFER = 1;
    protected static final int DEACTIVATE_OFFER = 0;
    protected static final long NO_TRIGGER_PRICE = 0;

    @Setter
    protected static boolean isLongRunningTest;

    protected static PaymentAccount alicesBsqSwapAcct;
    protected static PaymentAccount bobsBsqSwapAcct;
    // TODO Deprecate legacy BSQ accounts when no longer in use.
    protected static PaymentAccount alicesLegacyBsqAcct;
    protected static PaymentAccount bobsLegacyBsqAcct;

    protected static PaymentAccount alicesXmrAcct;
    protected static PaymentAccount bobsXmrAcct;

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                false,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);

        initSwapPaymentAccounts();
        createLegacyBsqPaymentAccounts();
    }

    // Mkt Price Margin value of offer returned from server is scaled down by 10^-2.
    protected final Function<Double, Double> scaledDownMktPriceMargin = (mktPriceMargin) ->
            exactMultiply(mktPriceMargin, 0.01);

    // Price value of fiat offer returned from server will be scaled up by 10^4.
    protected final Function<BigDecimal, Long> scaledUpFiatOfferPrice = (price) -> {
        BigDecimal factor = new BigDecimal(10).pow(4);
        return price.multiply(factor).longValue();
    };

    // Price value of altcoin offer returned from server will be scaled up by 10^8.
    protected final Function<String, Long> scaledUpAltcoinOfferPrice = (altcoinPriceAsString) -> {
        BigDecimal factor = new BigDecimal(10).pow(8);
        BigDecimal priceAsBigDecimal = new BigDecimal(altcoinPriceAsString);
        return priceAsBigDecimal.multiply(factor).longValue();
    };

    protected final BiFunction<Double, Double, Long> calcFiatTriggerPriceAsLong = (base, delta) -> {
        var priceAsDouble = new BigDecimal(base).add(new BigDecimal(delta)).doubleValue();
        return Double.valueOf(exactMultiply(priceAsDouble, 10_000)).longValue();
    };

    protected final BiFunction<Double, Double, Long> calcAltcoinTriggerPriceAsLong = (base, delta) -> {
        var priceAsDouble = new BigDecimal(base).add(new BigDecimal(delta)).doubleValue();
        return Double.valueOf(exactMultiply(priceAsDouble, 100_000_000)).longValue();
    };

    protected final BiFunction<Double, Double, String> calcPriceAsString = (base, delta) -> {
        var priceAsBigDecimal = new BigDecimal(Double.toString(base))
                .add(new BigDecimal(Double.toString(delta)));
        return priceAsBigDecimal.toPlainString();
    };

    protected final Function<OfferInfo, String> toOfferTable = (offer) ->
            new TableBuilder(OFFER_TBL, offer).build().toString();

    protected final Function<List<OfferInfo>, String> toOffersTable = (offers) ->
            new TableBuilder(OFFER_TBL, offers).build().toString();

    protected OfferInfo getAvailableBsqSwapOffer(GrpcClient client,
                                                 OfferDirection direction,
                                                 boolean checkForLoggedExceptions) {
        List<OfferInfo> bsqSwapOffers = new ArrayList<>();
        int numFetchAttempts = 0;
        while (bsqSwapOffers.size() == 0) {
            bsqSwapOffers.addAll(client.getBsqSwapOffers(direction.name()));
            numFetchAttempts++;
            if (bsqSwapOffers.size() == 0) {
                log.warn("No available bsq swap offers found after {} fetch attempts.", numFetchAttempts);
                if (numFetchAttempts > 9) {
                    if (checkForLoggedExceptions) {
                        printNodeExceptionMessages(log);
                    }
                    fail(format("Bob gave up on fetching available bsq swap offers after %d attempts.", numFetchAttempts));
                }
                sleep(1_000);
            } else {
                assertEquals(1, bsqSwapOffers.size());
                log.debug("Bob found new available bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            }
        }
        var bsqSwapOffer = bobClient.getBsqSwapOffer(bsqSwapOffers.get(0).getId());
        assertEquals(bsqSwapOffers.get(0).getId(), bsqSwapOffer.getId());
        return bsqSwapOffer;
    }

    @SuppressWarnings("ConstantConditions")
    public static void initSwapPaymentAccounts() {
        // A bot may not know what the default 'BSQ Swap' account name is,
        // but API test cases do:  the value of the i18n property 'BSQ_SWAP'.
        alicesBsqSwapAcct = aliceClient.getPaymentAccount("BSQ Swap");
        bobsBsqSwapAcct = bobClient.getPaymentAccount("BSQ Swap");
    }

    @SuppressWarnings("ConstantConditions")
    public static void createLegacyBsqPaymentAccounts() {
        alicesLegacyBsqAcct = aliceClient.createCryptoCurrencyPaymentAccount("Alice's Legacy BSQ Account",
                BSQ,
                aliceClient.getUnusedBsqAddress(),
                false);
        bobsLegacyBsqAcct = bobClient.createCryptoCurrencyPaymentAccount("Bob's Legacy BSQ Account",
                BSQ,
                bobClient.getUnusedBsqAddress(),
                false);
    }

    @SuppressWarnings("ConstantConditions")
    public static void createXmrPaymentAccounts() {
        alicesXmrAcct = aliceClient.createCryptoCurrencyPaymentAccount("Alice's XMR Account",
                XMR,
                "44G4jWmSvTEfifSUZzTDnJVLPvYATmq9XhhtDqUof1BGCLceG82EQsVYG9Q9GN4bJcjbAJEc1JD1m5G7iK4UPZqACubV4Mq",
                false);
        log.debug("Alices XMR Account: {}", alicesXmrAcct);
        bobsXmrAcct = bobClient.createCryptoCurrencyPaymentAccount("Bob's XMR Account",
                XMR,
                "4BDRhdSBKZqAXs3PuNTbMtaXBNqFj5idC2yMVnQj8Rm61AyKY8AxLTt9vGRJ8pwcG4EtpyD8YpGqdZWCZ2VZj6yVBN2RVKs",
                false);
        log.debug("Bob's XMR Account: {}", bobsXmrAcct);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }

    protected static void runCliGetOffer(String offerId) {
        out.println("Alice's CLI 'getmyoffer' response:");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getmyoffer", "--offer-id=" + offerId});
        out.println("Bob's CLI 'getoffer' response:");
        CliMain.main(new String[]{"--password=xyz", "--port=9999", "getoffer", "--offer-id=" + offerId});
    }
}
