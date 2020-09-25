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
import bisq.core.monetary.Altcoin;

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.OfferInfo;

import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.common.util.MathUtils.roundDouble;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferPayload.Direction.BUY;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingMarketPriceMarginTest extends AbstractCreateOfferTest {

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
        assertEquals("", offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("USD", offer.getCounterCurrencyCode());

        assertMarketBasedPriceDiff(offer, priceMarginPctInput);
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
        assertEquals("", offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("NZD", offer.getCounterCurrencyCode());

        assertMarketBasedPriceDiff(offer, priceMarginPctInput);
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
        log.info(newOffer.toString());

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
        assertEquals("", offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("GBP", offer.getCounterCurrencyCode());

        assertMarketBasedPriceDiff(offer, priceMarginPctInput);
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
        log.info(newOffer.toString());

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
        assertEquals("", offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("BRL", offer.getCounterCurrencyCode());

        assertMarketBasedPriceDiff(offer, priceMarginPctInput);
    }

    private void assertMarketBasedPriceDiff(OfferInfo offer, double priceMarginPctInput) {
        // Assert the mkt price margin difference ( %) is < 1% from the expected difference.
        String counterCurrencyCode = offer.getCounterCurrencyCode();
        double lastPrice = getPrice(counterCurrencyCode);
        int precision = isCryptoCurrency(counterCurrencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
        double scaledOfferPrice = scaleDownByPowerOf10(offer.getPrice(), precision);
        assertTrue(() -> {
            double expectedPriceMarginPct = scaleDownByPowerOf10(priceMarginPctInput, 2);
            double actualPriceMarginPct = offer.getDirection().equals(BUY.name())
                    ? getPercentageDifference(scaledOfferPrice, lastPrice)
                    : getPercentageDifference(lastPrice, scaledOfferPrice);
            double diff = expectedPriceMarginPct - actualPriceMarginPct;
            if (diff > 0.0001) {
                String priceCalculationWarning = format("The calculated price was %.2f%s off"
                                + " mkt price, not the expected %.2f%s off mkt price.%n"
                                + "Offer %s",
                        scaleUpByPowerOf10(actualPriceMarginPct, 2), "%",
                        priceMarginPctInput, "%",
                        offer);
                double onePercent = 0.01;
                if (diff > Math.abs(onePercent)) {
                    log.error(priceCalculationWarning);
                    return false;
                } else {
                    log.warn(priceCalculationWarning);
                    return true;
                }
            } else {
                return true;
            }
        });
    }

    private double getPercentageDifference(double price1, double price2) {
        return new BigDecimal(roundDouble((1 - (price1 / price2)), 5))
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
