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

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.common.util.MathUtils.roundDouble;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.method.MethodTest;
import bisq.cli.GrpcStubs;

@Slf4j
public abstract class AbstractOfferTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                true,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);
    }

    @BeforeEach
    public void initDummyPaymentAccount() {
        super.initAlicesDummyPaymentAccount();
    }

    protected final OfferInfo createAliceOffer(PaymentAccount paymentAccount,
                                               String direction,
                                               String currencyCode,
                                               long amount) {
        return createMarketBasedPricedOffer(aliceStubs, paymentAccount, direction, currencyCode, amount);
    }

    protected final OfferInfo createBobOffer(PaymentAccount paymentAccount,
                                             String direction,
                                             String currencyCode,
                                             long amount) {
        return createMarketBasedPricedOffer(bobStubs, paymentAccount, direction, currencyCode, amount);
    }

    protected final OfferInfo createMarketBasedPricedOffer(GrpcStubs grpcStubs,
                                                           PaymentAccount paymentAccount,
                                                           String direction,
                                                           String currencyCode,
                                                           long amount) {
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection(direction)
                .setCurrencyCode(currencyCode)
                .setAmount(amount)
                .setMinAmount(amount)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(0.00)
                .setPrice("0")
                .setBuyerSecurityDeposit(getDefaultBuyerSecurityDepositAsPercent())
                .build();
        return grpcStubs.offersService.createOffer(req).getOffer();
    }

    protected final OfferInfo getOffer(String offerId) {
        return aliceStubs.offersService.getOffer(createGetOfferRequest(offerId)).getOffer();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected final void cancelOffer(GrpcStubs grpcStubs, String offerId) {
        grpcStubs.offersService.cancelOffer(createCancelOfferRequest(offerId));
    }

    protected final OfferInfo getMostRecentOffer(GrpcStubs grpcStubs, String direction, String currencyCode) {
        List<OfferInfo> offerInfoList = getOffersSortedByDate(grpcStubs, direction, currencyCode);
        if (offerInfoList.isEmpty())
            fail(format("No %s offers found for currency %s", direction, currencyCode));

        return offerInfoList.get(offerInfoList.size() - 1);
    }

    protected final int getOpenOffersCount(GrpcStubs grpcStubs, String direction, String currencyCode) {
        return getOffersSortedByDate(grpcStubs, direction, currencyCode).size();
    }

    protected final List<OfferInfo> getOffersSortedByDate(GrpcStubs grpcStubs, String direction, String currencyCode) {
        var req = GetOffersRequest.newBuilder()
                .setDirection(direction)
                .setCurrencyCode(currencyCode).build();
        var reply = grpcStubs.offersService.getOffers(req);
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
