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

package bisq.core.locale;

import bisq.core.btc.BaseCurrencyNetwork;

import bisq.asset.Asset;
import bisq.asset.AssetRegistry;
import bisq.asset.Coin;
import bisq.asset.coins.Ether;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CurrencyUtilTest {

    @Before
    public void setup() {

        Locale.setDefault(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testGetTradeCurrency() {
        Optional<TradeCurrency> euro = CurrencyUtil.getTradeCurrency("EUR");
        Optional<TradeCurrency> naira = CurrencyUtil.getTradeCurrency("NGN");
        Optional<TradeCurrency> fake = CurrencyUtil.getTradeCurrency("FAK");

        assertTrue(euro.isPresent());
        assertTrue(naira.isPresent());
        assertFalse("Fake currency shouldn't exist", fake.isPresent());
    }

    @Test
    public void testFindAsset() {
        MockAssetRegistry assetRegistry = new MockAssetRegistry();

        // test if code is matching
        boolean daoTradingActivated = false;
        // Test if BSQ on mainnet is failing
        Assert.assertFalse(CurrencyUtil.findAsset(assetRegistry, "BSQ",
                BaseCurrencyNetwork.BTC_MAINNET, daoTradingActivated).isPresent());

        // on testnet/regtest it is allowed
        assertEquals(CurrencyUtil.findAsset(assetRegistry, "BSQ",
                BaseCurrencyNetwork.BTC_TESTNET, daoTradingActivated).get().getTickerSymbol(), "BSQ");


        daoTradingActivated = true;
        // With daoTradingActivated we can request BSQ
        assertEquals(CurrencyUtil.findAsset(assetRegistry, "BSQ",
                BaseCurrencyNetwork.BTC_MAINNET, daoTradingActivated).get().getTickerSymbol(), "BSQ");

        // Test if not matching ticker is failing
        Assert.assertFalse(CurrencyUtil.findAsset(assetRegistry, "BSQ1",
                BaseCurrencyNetwork.BTC_MAINNET, daoTradingActivated).isPresent());

        // Add a mock coin which has no mainnet version, needs to fail if we are on mainnet
        MockTestnetCoin.Testnet mockTestnetCoin = new MockTestnetCoin.Testnet();
        try {
            assetRegistry.addAsset(mockTestnetCoin);
            CurrencyUtil.findAsset(assetRegistry, "MOCK_COIN",
                    BaseCurrencyNetwork.BTC_MAINNET, daoTradingActivated);
            Assert.fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String wantMessage = "We are on mainnet and we could not find an asset with network type mainnet";
            Assert.assertTrue("Unexpected exception, want message starting with " +
                    "'" + wantMessage + "', got '" + e.getMessage() + "'", e.getMessage().startsWith(wantMessage));
        }

        // For testnet its ok
        assertEquals(CurrencyUtil.findAsset(assetRegistry, "MOCK_COIN",
                BaseCurrencyNetwork.BTC_TESTNET, daoTradingActivated).get().getTickerSymbol(), "MOCK_COIN");
        assertEquals(Coin.Network.TESTNET, mockTestnetCoin.getNetwork());

        // For regtest its still found
        assertEquals(CurrencyUtil.findAsset(assetRegistry, "MOCK_COIN",
                BaseCurrencyNetwork.BTC_REGTEST, daoTradingActivated).get().getTickerSymbol(), "MOCK_COIN");


        // We test if we are not on mainnet to get the mainnet coin
        Coin ether = new Ether();
        assertEquals(CurrencyUtil.findAsset(assetRegistry, "ETH",
                BaseCurrencyNetwork.BTC_TESTNET, daoTradingActivated).get().getTickerSymbol(), "ETH");
        assertEquals(CurrencyUtil.findAsset(assetRegistry, "ETH",
                BaseCurrencyNetwork.BTC_REGTEST, daoTradingActivated).get().getTickerSymbol(), "ETH");
        assertEquals(Coin.Network.MAINNET, ether.getNetwork());

        // We test if network matches exactly if there are distinct network types defined like with BSQ
        Coin bsq = (Coin) CurrencyUtil.findAsset(assetRegistry, "BSQ", BaseCurrencyNetwork.BTC_MAINNET, daoTradingActivated).get();
        assertEquals("BSQ", bsq.getTickerSymbol());
        assertEquals(Coin.Network.MAINNET, bsq.getNetwork());

        bsq = (Coin) CurrencyUtil.findAsset(assetRegistry, "BSQ", BaseCurrencyNetwork.BTC_TESTNET, daoTradingActivated).get();
        assertEquals("BSQ", bsq.getTickerSymbol());
        assertEquals(Coin.Network.TESTNET, bsq.getNetwork());

        bsq = (Coin) CurrencyUtil.findAsset(assetRegistry, "BSQ", BaseCurrencyNetwork.BTC_REGTEST, daoTradingActivated).get();
        assertEquals("BSQ", bsq.getTickerSymbol());
        assertEquals(Coin.Network.REGTEST, bsq.getNetwork());
    }

    @Test
    public void testGetNameAndCodeOfRemovedAsset() {
        assertEquals("Bitcoin Cash (BCH)", CurrencyUtil.getNameAndCode("BCH"));
        assertEquals("N/A (XYZ)", CurrencyUtil.getNameAndCode("XYZ"));
    }

    class MockAssetRegistry extends AssetRegistry {
        private List<Asset> registeredAssets = new ArrayList<>();

        MockAssetRegistry() {
            for (Asset asset : ServiceLoader.load(Asset.class)) {
                registeredAssets.add(asset);
            }
        }

        void addAsset(Asset asset) {
            registeredAssets.add(asset);
        }

        public Stream<Asset> stream() {
            return registeredAssets.stream();
        }
    }
}
