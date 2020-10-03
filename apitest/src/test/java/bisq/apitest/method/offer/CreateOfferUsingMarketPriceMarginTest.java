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

import bisq.core.btc.wallet.Restrictions;

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.OfferInfo;

import java.text.DecimalFormat;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferPayload.Direction.BUY;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingMarketPriceMarginTest extends AbstractCreateOfferTest {

    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("##0.00");
    private static final double MKT_PRICE_MARGIN_ERROR_TOLERANCE = 0.0050;      // 0.50%
    private static final double MKT_PRICE_MARGIN_WARNING_TOLERANCE = 0.0001;    // 0.01%

    @Test
    @Order(1)
    public void testCreateUSDBTCBuyOffer5PctPriceMargin() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        double priceMarginPctInput = 5.00;
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("usd")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(priceMarginPctInput)
                .setPrice("0")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("BUY", newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("buy", "usd");
        assertEquals(newOfferId, offer.getId());
        assertEquals("BUY", offer.getDirection());
        assertTrue(offer.getUseMarketBasedPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("USD", offer.getCounterCurrencyCode());

        assertCalculatedPriceIsCorrect(offer, priceMarginPctInput);
    }

    @Test
    @Order(2)
    public void testCreateNZDBTCBuyOfferMinus2PctPriceMargin() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        double priceMarginPctInput = -2.00;
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("nzd")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(priceMarginPctInput)
                .setPrice("0")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("BUY", newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("NZD", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("buy", "nzd");
        assertEquals(newOfferId, offer.getId());
        assertEquals("BUY", offer.getDirection());
        assertTrue(offer.getUseMarketBasedPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("NZD", offer.getCounterCurrencyCode());

        assertCalculatedPriceIsCorrect(offer, priceMarginPctInput);
    }

    @Test
    @Order(3)
    public void testCreateGBPBTCSellOfferMinus1Point5PctPriceMargin() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        double priceMarginPctInput = -1.5;
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("sell")
                .setCurrencyCode("gbp")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(priceMarginPctInput)
                .setPrice("0")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("SELL", newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("GBP", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("sell", "gbp");
        assertEquals(newOfferId, offer.getId());
        assertEquals("SELL", offer.getDirection());
        assertTrue(offer.getUseMarketBasedPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("GBP", offer.getCounterCurrencyCode());

        assertCalculatedPriceIsCorrect(offer, priceMarginPctInput);
    }

    @Test
    @Order(4)
    public void testCreateBRLBTCSellOffer6Point55PctPriceMargin() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        double priceMarginPctInput = 6.55;
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("sell")
                .setCurrencyCode("brl")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(priceMarginPctInput)
                .setPrice("0")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("SELL", newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("BRL", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("sell", "brl");
        assertEquals(newOfferId, offer.getId());
        assertEquals("SELL", offer.getDirection());
        assertTrue(offer.getUseMarketBasedPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("BRL", offer.getCounterCurrencyCode());

        assertCalculatedPriceIsCorrect(offer, priceMarginPctInput);
    }

    private void assertCalculatedPriceIsCorrect(OfferInfo offer, double priceMarginPctInput) {
        assertTrue(() -> {
            String counterCurrencyCode = offer.getCounterCurrencyCode();
            double mktPrice = getMarketPrice(counterCurrencyCode);
            double scaledOfferPrice = getScaledOfferPrice(offer.getPrice(), counterCurrencyCode);
            double expectedDiffPct = scaleDownByPowerOf10(priceMarginPctInput, 2);
            double actualDiffPct = offer.getDirection().equals(BUY.name())
                    ? getPercentageDifference(scaledOfferPrice, mktPrice)
                    : getPercentageDifference(mktPrice, scaledOfferPrice);
            double pctDiffDelta = abs(expectedDiffPct) - abs(actualDiffPct);
            return isCalculatedPriceWithinErrorTolerance(pctDiffDelta,
                    expectedDiffPct,
                    actualDiffPct,
                    mktPrice,
                    scaledOfferPrice,
                    offer);
        });
    }

    private boolean isCalculatedPriceWithinErrorTolerance(double delta,
                                                          double expectedDiffPct,
                                                          double actualDiffPct,
                                                          double mktPrice,
                                                          double scaledOfferPrice,
                                                          OfferInfo offer) {
        if (abs(delta) > MKT_PRICE_MARGIN_ERROR_TOLERANCE) {
            logCalculatedPricePoppedErrorTolerance(expectedDiffPct,
                    actualDiffPct,
                    mktPrice,
                    scaledOfferPrice);
            log.error(offer.toString());
            return false;
        }

        if (abs(delta) >= MKT_PRICE_MARGIN_WARNING_TOLERANCE) {
            logCalculatedPricePoppedWarningTolerance(expectedDiffPct,
                    actualDiffPct,
                    mktPrice,
                    scaledOfferPrice);
            log.warn(offer.toString());
        }

        return true;
    }

    private void logCalculatedPricePoppedWarningTolerance(double expectedDiffPct,
                                                          double actualDiffPct,
                                                          double mktPrice,
                                                          double scaledOfferPrice) {
        log.warn(format("Calculated price %.4f & mkt price %.4f differ by ~ %s%s,"
                        + " not by %s%s, outside the %s%s warning tolerance,"
                        + " but within the %s%s error tolerance.",
                scaledOfferPrice, mktPrice,
                PCT_FORMAT.format(scaleUpByPowerOf10(actualDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(expectedDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(MKT_PRICE_MARGIN_WARNING_TOLERANCE, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(MKT_PRICE_MARGIN_ERROR_TOLERANCE, 2)), "%"));
    }

    private void logCalculatedPricePoppedErrorTolerance(double expectedDiffPct,
                                                        double actualDiffPct,
                                                        double mktPrice,
                                                        double scaledOfferPrice) {
        log.error(format("Calculated price %.4f & mkt price %.4f differ by ~ %s%s,"
                        + " not by %s%s, outside the %s%s error tolerance.",
                scaledOfferPrice, mktPrice,
                PCT_FORMAT.format(scaleUpByPowerOf10(actualDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(expectedDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(MKT_PRICE_MARGIN_ERROR_TOLERANCE, 2)), "%"));
    }
}
