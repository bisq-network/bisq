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

import protobuf.PaymentAccount;

import java.math.BigDecimal;

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
import static bisq.common.util.MathUtils.exactMultiply;



import bisq.apitest.method.MethodTest;

@Slf4j
public abstract class AbstractOfferTest extends MethodTest {

    protected static final int ACTIVATE_OFFER = 1;
    protected static final int DEACTIVATE_OFFER = 0;
    protected static final long NO_TRIGGER_PRICE = 0;

    @Setter
    protected static boolean isLongRunningTest;

    protected static PaymentAccount alicesBsqAcct;
    protected static PaymentAccount bobsBsqAcct;

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                false,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);
    }

    public static void createBsqSwapBsqPaymentAccounts() {
        alicesBsqAcct = aliceClient.createCryptoCurrencyPaymentAccount("Alice's BsqSwap Account",
                BSQ,
                aliceClient.getUnusedBsqAddress(), // TODO refactor, bsq address not needed for atom acct
                false);
        bobsBsqAcct = bobClient.createCryptoCurrencyPaymentAccount("Bob's BsqSwap Account",
                BSQ,
                bobClient.getUnusedBsqAddress(),   // TODO refactor, bsq address not needed for atom acct
                false);
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

    @SuppressWarnings("ConstantConditions")
    public static void createBsqPaymentAccounts() {
        alicesBsqAcct = aliceClient.createCryptoCurrencyPaymentAccount("Alice's BSQ Account",
                BSQ,
                aliceClient.getUnusedBsqAddress(),
                false);
        bobsBsqAcct = bobClient.createCryptoCurrencyPaymentAccount("Bob's BSQ Account",
                BSQ,
                bobClient.getUnusedBsqAddress(),
                false);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
