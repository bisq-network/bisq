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

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingFixedPriceTest extends AbstractCreateOfferTest {

    @Test
    @Order(1)
    public void testCreateAUDBTCBuyOfferUsingFixedPrice16000() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("aud")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("16000")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("BUY", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(160000000, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("AUD", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("buy", "aud");
        assertEquals(newOfferId, offer.getId());
        assertEquals("BUY", offer.getDirection());
        assertFalse(offer.getUseMarketBasedPrice());
        assertEquals(160000000, offer.getPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("AUD", offer.getCounterCurrencyCode());
    }

    @Test
    @Order(2)
    public void testCreateUSDBTCBuyOfferUsingFixedPrice100001234() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("usd")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("10000.1234")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("BUY", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(100001234, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("buy", "usd");
        assertEquals(newOfferId, offer.getId());
        assertEquals("BUY", offer.getDirection());
        assertFalse(offer.getUseMarketBasedPrice());
        assertEquals(100001234, offer.getPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("USD", offer.getCounterCurrencyCode());
    }

    @Test
    @Order(3)
    public void testCreateEURBTCSellOfferUsingFixedPrice95001234() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("sell")
                .setCurrencyCode("eur")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("9500.1234")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("SELL", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(95001234, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("EUR", newOffer.getCounterCurrencyCode());

        OfferInfo offer = getMostRecentOffer("sell", "eur");
        assertEquals(newOfferId, offer.getId());
        assertEquals("SELL", offer.getDirection());
        assertFalse(offer.getUseMarketBasedPrice());
        assertEquals(95001234, offer.getPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("EUR", offer.getCounterCurrencyCode());
    }
}
