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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingMarketPriceMarginTest extends AbstractCreateOfferTest {

    @Test
    @Order(1)
    public void testCreateUSDBTCBuyOffer5PctPriceMargin() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("usd")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(5.00)
                .setPrice("0")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = alice.offersService.createOffer(req).getOffer();
        log.info(newOffer.toString());

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
    }

    @Test
    @Order(2)
    public void testCreateNZDBTCBuyOfferMinus2PctPriceMargin() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("nzd")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(-2.00)
                .setPrice("0")
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .build();
        var newOffer = alice.offersService.createOffer(req).getOffer();
        log.info(newOffer.toString());

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
    }
}
