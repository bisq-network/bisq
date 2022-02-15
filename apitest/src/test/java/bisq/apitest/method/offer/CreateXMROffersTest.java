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

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

@SuppressWarnings("ConstantConditions")
@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateXMROffersTest extends AbstractOfferTest {

    private static final String MAKER_FEE_CURRENCY_CODE = BSQ;

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
        createXmrPaymentAccounts();
    }

    @Test
    @Order(1)
    public void testCreateFixedPriceBuy1BTCFor200KXMROffer() {
        // Remember alt coin trades are BTC trades.  When placing an offer, you are
        // offering to buy or sell BTC, not BSQ, XMR, etc.  In this test case,
        // Alice places an offer to BUY BTC with BSQ.
        var newOffer = aliceClient.createFixedPricedOffer(BUY.name(),
                XMR,
                100_000_000L,
                75_000_000L,
                "0.005",   // FIXED PRICE IN BTC (satoshis) FOR 1 XMR
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesXmrAcct.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.debug("Sell XMR (Buy BTC) offer:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(500_000L, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(75_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getOffer(newOfferId);
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(500_000L, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(75_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(2)
    public void testCreateFixedPriceSell1BTCFor200KXMROffer() {
        // Alice places an offer to SELL BTC for XMR.
        var newOffer = aliceClient.createFixedPricedOffer(SELL.name(),
                XMR,
                100_000_000L,
                50_000_000L,
                "0.005",   // FIXED PRICE IN BTC (satoshis) FOR 1 XMR
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesXmrAcct.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.debug("Buy XMR (Sell BTC) offer:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(500_000L, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(50_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getOffer(newOfferId);
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(500_000L, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(50_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(3)
    public void testCreatePriceMarginBasedBuy1BTCOfferWithTriggerPrice() {
        double priceMarginPctInput = 1.00;
        double mktPriceAsDouble = aliceClient.getBtcPrice(XMR);
        long triggerPriceAsLong = calcAltcoinTriggerPriceAsLong.apply(mktPriceAsDouble, -0.001);
        var newOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                XMR,
                100_000_000L,
                75_000_000L,
                priceMarginPctInput,
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesXmrAcct.getId(),
                MAKER_FEE_CURRENCY_CODE,
                triggerPriceAsLong);
        log.debug("Pending Sell XMR (Buy BTC) offer:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());

        // There is no trigger price while offer is pending.
        assertEquals(0, newOffer.getTriggerPrice());

        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(75_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getOffer(newOfferId);
        log.debug("Available Sell XMR (Buy BTC) offer:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());

        // The trigger price should exist on the prepared offer.
        assertEquals(triggerPriceAsLong, newOffer.getTriggerPrice());

        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(75_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(4)
    public void testCreatePriceMarginBasedSell1BTCOffer() {
        // Alice places an offer to SELL BTC for XMR.
        double priceMarginPctInput = 0.50;
        var newOffer = aliceClient.createMarketBasedPricedOffer(SELL.name(),
                XMR,
                100_000_000L,
                50_000_000L,
                priceMarginPctInput,
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesXmrAcct.getId(),
                MAKER_FEE_CURRENCY_CODE,
                NO_TRIGGER_PRICE);
        log.debug("Buy XMR (Sell BTC) offer:\n{}", toOfferTable.apply(newOffer));
        assertTrue(newOffer.getIsMyOffer());
        assertTrue(newOffer.getIsMyPendingOffer());

        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(50_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getOffer(newOfferId);
        assertTrue(newOffer.getIsMyOffer());
        assertFalse(newOffer.getIsMyPendingOffer());
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(50_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesXmrAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(XMR, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(5)
    public void testGetAllMyXMROffers() {
        List<OfferInfo> offers = aliceClient.getMyCryptoCurrencyOffersSortedByDate(XMR);
        log.debug("All of Alice's XMR offers:\n{}", toOffersTable.apply(offers));
        assertEquals(4, offers.size());
        log.debug("Alice's balances\n{}", formatBalancesTbls(aliceClient.getBalances()));
    }

    @Test
    @Order(6)
    public void testGetAvailableXMROffers() {
        List<OfferInfo> offers = bobClient.getCryptoCurrencyOffersSortedByDate(XMR);
        log.debug("All of Bob's available XMR offers:\n{}", toOffersTable.apply(offers));
        assertEquals(4, offers.size());
        log.debug("Bob's balances\n{}", formatBalancesTbls(bobClient.getBalances()));
    }

    private void genBtcBlockAndWaitForOfferPreparation() {
        // Extra time is needed for the OfferUtils#isBsqForMakerFeeAvailable, which
        // can sometimes return an incorrect false value if the BsqWallet's
        // available confirmed balance is temporarily = zero during BSQ offer prep.
        genBtcBlocksThenWait(1, 5000);
    }
}
