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

import bisq.core.monetary.Altcoin;

import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OfferInfo;

import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.common.util.MathUtils.roundDouble;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.method.MethodTest;
import bisq.cli.GrpcStubs;


@Slf4j
abstract class AbstractCreateOfferTest extends MethodTest {

    protected static GrpcStubs aliceStubs;

    @BeforeAll
    public static void setUp() {
        startSupportingApps();
    }

    static void startSupportingApps() {
        try {
            // setUpScaffold(new String[]{"--supportingApps", "bitcoind,seednode,alicedaemon", "--enableBisqDebugging", "true"});
            setUpScaffold(bitcoind, seednode, alicedaemon);
            aliceStubs = grpcStubs(alicedaemon);

            // Generate 1 regtest block for alice's wallet to show 10 BTC balance,
            // and give alicedaemon time to parse the new block.
            bitcoinCli.generateBlocks(1);
            MILLISECONDS.sleep(1500);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    protected final OfferInfo getMostRecentOffer(String direction, String currencyCode) {
        List<OfferInfo> offerInfoList = getOffersSortedByDate(direction, currencyCode);
        if (offerInfoList.isEmpty())
            fail(format("No %s offers found for currency %s", direction, currencyCode));

        return offerInfoList.get(offerInfoList.size() - 1);
    }

    protected final List<OfferInfo> getOffersSortedByDate(String direction, String currencyCode) {
        var req = GetOffersRequest.newBuilder()
                .setDirection(direction)
                .setCurrencyCode(currencyCode).build();
        var reply = aliceStubs.offersService.getOffers(req);
        return sortOffersByDate(reply.getOffersList());
    }

    protected final List<OfferInfo> sortOffersByDate(List<OfferInfo> offerInfoList) {
        return offerInfoList.stream()
                .sorted(comparing(OfferInfo::getDate))
                .collect(Collectors.toList());
    }

    protected double getScaledOfferPrice(double offerPrice, String currencyCode) {
        int precision = isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
        return scaleDownByPowerOf10(offerPrice, precision);
    }

    protected final double getMarketPrice(String currencyCode) {
        return getMarketPrice(alicedaemon, currencyCode);
    }

    protected final double getPercentageDifference(double price1, double price2) {
        return BigDecimal.valueOf(roundDouble((1 - (price1 / price2)), 5))
                .setScale(4, HALF_UP)
                .doubleValue();
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
