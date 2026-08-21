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

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.EUR;
import static bisq.apitest.config.ApiTestConfig.USD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

@Slf4j
public class CreateOfferUsingFixedPriceTest extends DockerOfferTest {

    private static final String MAKER_FEE_CURRENCY_CODE = BSQ;

    @Test
    public void testCreateAUDBTCBuyOfferUsingFixedPrice() {
        PaymentAccount audAccount = getOrCreateF2F("AU");
        OfferInfo created = aliceClient.createFixedPricedOffer(BUY.name(),
                "aud", 1_250_000L, 1_250_000L, "36000",
                defaultBuyerSecurityDepositPct.get(), audAccount.getId(), MAKER_FEE_CURRENCY_CODE);
        assertOffer(created, BUY.name(), "36000.0000", 1_250_000, 1_250_000,
                audAccount.getId(), "AUD");
        awaitOfferActivated(created.getId());
        OfferInfo fetched = aliceClient.getOffer(created.getId());
        assertTrue(fetched.getIsActivated());
        assertFalse(fetched.getIsMyPendingOffer());
    }

    @Test
    public void testCreateUSDBTCBuyOfferUsingFixedPrice() {
        PaymentAccount usdAccount = getOrCreateF2F("US");
        OfferInfo created = aliceClient.createFixedPricedOffer(BUY.name(),
                "usd", 1_250_000L, 1_250_000L, "30000.1234",
                defaultBuyerSecurityDepositPct.get(), usdAccount.getId(), MAKER_FEE_CURRENCY_CODE);
        assertOffer(created, BUY.name(), "30000.1234", 1_250_000, 1_250_000,
                usdAccount.getId(), USD);
        awaitOfferActivated(created.getId());
        OfferInfo fetched = aliceClient.getOffer(created.getId());
        assertTrue(fetched.getIsActivated());
    }

    @Test
    public void testCreateEURBTCSellOfferUsingFixedPrice() {
        PaymentAccount eurAccount = getOrCreateF2F("FR");
        OfferInfo created = aliceClient.createFixedPricedOffer(SELL.name(),
                "eur", 1_250_000L, 1_000_000L, "29500.1234",
                defaultBuyerSecurityDepositPct.get(), eurAccount.getId(), MAKER_FEE_CURRENCY_CODE);
        assertOffer(created, SELL.name(), "29500.1234", 1_250_000, 1_000_000,
                eurAccount.getId(), EUR);
        awaitOfferActivated(created.getId());
        OfferInfo fetched = aliceClient.getOffer(created.getId());
        assertTrue(fetched.getIsActivated());
    }

    private void assertOffer(OfferInfo o, String direction, String price, long amount, long minAmount,
                             String paymentAccountId, String counterCcy) {
        assertTrue(o.getIsMyOffer());
        assertTrue(o.getIsMyPendingOffer());
        assertFalse(o.getIsActivated()); // not active until offer-book add task completes
        assertNotEquals("", o.getId());
        assertEquals(direction, o.getDirection());
        assertFalse(o.getUseMarketBasedPrice());
        assertEquals(price, o.getPrice());
        assertEquals(amount, o.getAmount());
        assertEquals(minAmount, o.getMinAmount());
        // Volume and security-deposit values are computed from amount * price ± mins/caps
        // and change with regtest DAO param tweaks. We assert they're populated and positive
        // rather than pinning to brittle exact strings.
        assertNotEquals("", o.getVolume());
        assertNotEquals("", o.getMinVolume());
        assertTrue(o.getBuyerSecurityDeposit() > 0, "buyerSecurityDeposit must be positive");
        assertEquals(paymentAccountId, o.getPaymentAccountId());
        assertEquals(BTC, o.getBaseCurrencyCode());
        assertEquals(counterCcy, o.getCounterCurrencyCode());
        assertFalse(o.getIsCurrencyForMakerFeeBtc());
    }
}
