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

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.CreateOfferRequest;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingFixedPriceTest extends AbstractOfferTest {

    private static final String MAKER_FEE_CURRENCY_CODE = "bsq";

    @Test
    @Order(1)
    public void testCreateAUDBTCBuyOfferUsingFixedPrice16000() {
        PaymentAccount audAccount = createDummyF2FAccount(alicedaemon, "AU");
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(audAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("aud")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("36000")
                .setBuyerSecurityDeposit(getDefaultBuyerSecurityDepositAsPercent())
                .setMakerFeeCurrencyCode(MAKER_FEE_CURRENCY_CODE)
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("BUY", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(360000000, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(audAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("AUD", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals("BUY", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(360000000, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(audAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("AUD", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(2)
    public void testCreateUSDBTCBuyOfferUsingFixedPrice100001234() {
        PaymentAccount usdAccount = createDummyF2FAccount(alicedaemon, "US");
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(usdAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("usd")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("30000.1234")
                .setBuyerSecurityDeposit(getDefaultBuyerSecurityDepositAsPercent())
                .setMakerFeeCurrencyCode(MAKER_FEE_CURRENCY_CODE)
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("BUY", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(300001234, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(usdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals("BUY", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(300001234, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(usdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(3)
    public void testCreateEURBTCSellOfferUsingFixedPrice95001234() {
        PaymentAccount eurAccount = createDummyF2FAccount(alicedaemon, "FR");
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(eurAccount.getId())
                .setDirection("sell")
                .setCurrencyCode("eur")
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("29500.1234")
                .setBuyerSecurityDeposit(getDefaultBuyerSecurityDepositAsPercent())
                .setMakerFeeCurrencyCode(MAKER_FEE_CURRENCY_CODE)
                .build();
        var newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals("SELL", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(295001234, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(eurAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("EUR", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals("SELL", newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(295001234, newOffer.getPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(eurAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("EUR", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }
}
