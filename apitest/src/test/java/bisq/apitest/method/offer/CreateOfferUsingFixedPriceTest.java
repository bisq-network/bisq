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

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.EUR;
import static bisq.apitest.config.ApiTestConfig.USD;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingFixedPriceTest extends AbstractOfferTest {

    private static final String MAKER_FEE_CURRENCY_CODE = BSQ;

    @Test
    @Order(1)
    public void testCreateAUDBTCBuyOfferUsingFixedPrice16000() {
        PaymentAccount audAccount = createDummyF2FAccount(aliceClient, "AU");
        var newOffer = aliceClient.createFixedPricedOffer(BUY.name(),
                "aud",
                10_000_000L,
                10_000_000L,
                "36000",
                getDefaultBuyerSecurityDepositAsPercent(),
                audAccount.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.debug("OFFER #1:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(360_000_000, newOffer.getPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(audAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("AUD", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getOffer(newOfferId);
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(360_000_000, newOffer.getPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(audAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("AUD", newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(2)
    public void testCreateUSDBTCBuyOfferUsingFixedPrice100001234() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");
        var newOffer = aliceClient.createFixedPricedOffer(BUY.name(),
                "usd",
                10_000_000L,
                10_000_000L,
                "30000.1234",
                getDefaultBuyerSecurityDepositAsPercent(),
                usdAccount.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.debug("OFFER #2:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(300_001_234, newOffer.getPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(usdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals(USD, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getOffer(newOfferId);
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(300_001_234, newOffer.getPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(usdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals(USD, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(3)
    public void testCreateEURBTCSellOfferUsingFixedPrice95001234() {
        PaymentAccount eurAccount = createDummyF2FAccount(aliceClient, "FR");
        var newOffer = aliceClient.createFixedPricedOffer(SELL.name(),
                "eur",
                10_000_000L,
                5_000_000L,
                "29500.1234",
                getDefaultBuyerSecurityDepositAsPercent(),
                eurAccount.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.debug("OFFER #3:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(295_001_234, newOffer.getPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(5_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(eurAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals(EUR, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getOffer(newOfferId);
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(295_001_234, newOffer.getPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(5_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(eurAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals(EUR, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }
}
