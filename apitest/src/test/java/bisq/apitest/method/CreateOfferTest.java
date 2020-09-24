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

package bisq.apitest.method;

import bisq.core.btc.wallet.Restrictions;

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OfferInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;


@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class CreateOfferTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        try {
            // setUpScaffold(new String[]{"--supportingApps", "bitcoind,seednode,arbdaemon,alicedaemon", "--enableBisqDebugging", "true"});
            setUpScaffold(bitcoind, seednode, arbdaemon, alicedaemon);

            // Generate 1 regtest block for alice's wallet to show 10 BTC balance,
            // and give alicedaemon time to parse the new block.
            bitcoinCli.generateBlocks(1);
            MILLISECONDS.sleep(1500);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testCreateBuyOffer() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setCurrencyCode("usd")
                .setDirection("buy")
                .setPrice(0)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(0.00)
                .setAmount(10000000)
                .setMinAmount(10000000)
                .setBuyerSecurityDeposit(Restrictions.getDefaultBuyerSecurityDepositAsPercent())
                .setPaymentAccountId(paymentAccount.getId())
                .build();
        var newOffer = grpcStubs(alicedaemon).offersService.createOffer(req).getOffer();
        assertEquals("BUY", newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10000000, newOffer.getAmount());
        assertEquals(10000000, newOffer.getMinAmount());
        assertEquals(1500000, newOffer.getBuyerSecurityDeposit());
        assertEquals(paymentAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals("BTC", newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());
    }

    @Test
    @Order(2)
    public void testGetNewBuyOffer() {
        var req = GetOffersRequest.newBuilder().setDirection("BUY").setCurrencyCode("USD").build();
        var reply = grpcStubs(alicedaemon).offersService.getOffers(req);

        assertEquals(1, reply.getOffersCount());
        OfferInfo offer = reply.getOffersList().get(0);
        assertEquals("BUY", offer.getDirection());
        assertTrue(offer.getUseMarketBasedPrice());
        assertEquals(10000000, offer.getAmount());
        assertEquals(10000000, offer.getMinAmount());
        assertEquals(1500000, offer.getBuyerSecurityDeposit());
        assertEquals("", offer.getPaymentAccountId());
        assertEquals("BTC", offer.getBaseCurrencyCode());
        assertEquals("USD", offer.getCounterCurrencyCode());
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
