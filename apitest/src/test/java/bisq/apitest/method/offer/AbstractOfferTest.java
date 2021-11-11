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

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import java.math.BigDecimal;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.cli.table.builder.TableType.OFFER_TBL;
import static bisq.common.util.MathUtils.exactMultiply;



import bisq.apitest.method.MethodTest;
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

    protected final BiFunction<Double, Double, Long> calcPriceAsLong = (base, delta) -> {
        var priceAsDouble = new BigDecimal(base).add(new BigDecimal(delta)).doubleValue();
        return Double.valueOf(exactMultiply(priceAsDouble, 10_000)).longValue();
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

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
