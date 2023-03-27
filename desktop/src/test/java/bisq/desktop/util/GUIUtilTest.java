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

package bisq.desktop.util;

import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.CoinMaker;

import javafx.util.StringConverter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.desktop.maker.TradeCurrencyMakers.bitcoin;
import static bisq.desktop.maker.TradeCurrencyMakers.euro;
import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.bitcoinj.core.CoinMaker.oneBitcoin;
import static org.bitcoinj.core.CoinMaker.satoshis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class GUIUtilTest {

    private Preferences preferences;
    private String explorerName;
    private String bsqExplorerName;
    private String txUrlPrefix;
    private String bsqTxUrlPrefix;
    private String addressUrlPrefix;
    private String bsqAddressUrlPrefix;

    @BeforeEach
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
        GlobalSettings.setLocale(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
        preferences = mock(Preferences.class);
        explorerName = "Blockstream.info";
        bsqExplorerName = "mempool.space (@wiz)";
        txUrlPrefix = "https://blockstream.info/tx/";
        bsqTxUrlPrefix = "https://mempool.space/bisq/tx/";
        addressUrlPrefix = "https://blockstream.info/address/";
        bsqAddressUrlPrefix = "https://mempool.space/bisq/address/";
    }

    @Test
    public void testTradeCurrencyConverter() {
        Map<String, Integer> offerCounts = new HashMap<>() {{
            put("BTC", 11);
            put("EUR", 10);
        }};
        StringConverter<TradeCurrency> tradeCurrencyConverter = GUIUtil.getTradeCurrencyConverter(
                Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"),
                offerCounts
        );

        assertEquals("✦ Bitcoin (BTC) - 11 offers", tradeCurrencyConverter.toString(bitcoin));
        assertEquals("★ Euro (EUR) - 10 offers", tradeCurrencyConverter.toString(euro));
    }

    @Test
    public void testOpenURLWithCampaignParameters() {
        DontShowAgainLookup.setPreferences(preferences);
        GUIUtil.setPreferences(preferences);
        when(preferences.showAgain("warnOpenURLWhenTorEnabled")).thenReturn(false);
        when(preferences.getUserLanguage()).thenReturn("en");

/*        PowerMockito.mockStatic(Utilities.class);
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        PowerMockito.doNothing().when(Utilities.class, "openURI", captor.capture());
        GUIUtil.openWebPage("https://bisq.network");

        assertEquals("https://bisq.network?utm_source=desktop-client&utm_medium=in-app-link&utm_campaign=language_en", captor.getValue().toString());

        GUIUtil.openWebPage("https://docs.bisq.network/trading-rules.html#f2f-trading");

        assertEquals("https://docs.bisq.network/trading-rules.html?utm_source=desktop-client&utm_medium=in-app-link&utm_campaign=language_en#f2f-trading", captor.getValue().toString());
*/
    }

    @Test
    public void testOpenURLWithoutCampaignParameters() {
        DontShowAgainLookup.setPreferences(preferences);
        GUIUtil.setPreferences(preferences);
        when(preferences.showAgain("warnOpenURLWhenTorEnabled")).thenReturn(false);
/*
        PowerMockito.mockStatic(Utilities.class);
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        PowerMockito.doNothing().when(Utilities.class, "openURI", captor.capture());
        GUIUtil.openWebPage("https://www.github.com");

        assertEquals("https://www.github.com", captor.getValue().toString());
*/
    }

    @Test
    public void testGetAddressUrl() {
        GUIUtil.setPreferences(preferences);
        when(preferences.getBlockChainExplorer()).thenReturn(new BlockChainExplorer(
                explorerName, txUrlPrefix, addressUrlPrefix));
        String address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        assertEquals(addressUrlPrefix + address, GUIUtil.getAddressUrl(address));
    }

    @Test
    public void testGetBsqAddressUrl() {
        GUIUtil.setPreferences(preferences);
        when(preferences.getBsqBlockChainExplorer()).thenReturn(new BlockChainExplorer(
                bsqExplorerName, bsqTxUrlPrefix, bsqAddressUrlPrefix));
        String bsqAddress = "B17Q6zA7LbEt5je4mtkBtYBfvDfvEwkzde";
        assertEquals(bsqAddressUrlPrefix + bsqAddress, GUIUtil.getBsqAddressUrl(bsqAddress));
    }

    @Test
    public void testGetTxUrl() {
        GUIUtil.setPreferences(preferences);
        when(preferences.getBlockChainExplorer()).thenReturn(new BlockChainExplorer(
                explorerName, txUrlPrefix, addressUrlPrefix));
        String txId = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b";
        assertEquals(txUrlPrefix + txId, GUIUtil.getTxUrl(txId));
    }

    @Test
    public void testGetBsqTxUrl() {
        GUIUtil.setPreferences(preferences);
        when(preferences.getBsqBlockChainExplorer()).thenReturn(new BlockChainExplorer(
                bsqExplorerName, bsqTxUrlPrefix, bsqAddressUrlPrefix));
        String bsqTxId = "4b5417ec5ab6112bedf539c3b4f5a806ed539542d8b717e1c4470aa3180edce5";
        assertEquals(bsqTxUrlPrefix + bsqTxId, GUIUtil.getBsqTxUrl(bsqTxId));
    }

    @Test
    public void testGetBsqInUsd() {
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        when(priceFeedService.getMarketPrice("USD"))
                .thenReturn(new MarketPrice("USD", 12345.6789, 0, true));

        Coin oneBsq = Coin.valueOf(100);
        Price avgPrice = Price.valueOf("BSQ", 10000);

        assertEquals("1.23 USD", GUIUtil.getBsqInUsd(avgPrice, oneBsq, priceFeedService, new BsqFormatter()));
    }

    @Test
    public void percentageOfTradeAmount_higherFeeAsMin() {

        Coin fee = make(a(CoinMaker.Coin).but(with(satoshis, 20000L)));
        Coin min = make(a(CoinMaker.Coin).but(with(satoshis, 10000L)));

        assertEquals(" (0.02% of trade amount)", GUIUtil.getPercentageOfTradeAmount(fee, oneBitcoin, min));
    }

    @Test
    public void percentageOfTradeAmount_minFee() {

        Coin fee = make(a(CoinMaker.Coin).but(with(satoshis, 10000L)));
        Coin min = make(a(CoinMaker.Coin).but(with(satoshis, 10000L)));

        assertEquals(" (required minimum)",
                GUIUtil.getPercentageOfTradeAmount(fee, oneBitcoin, min));
    }

    @Test
    public void percentageOfTradeAmount_minFeeZERO() {

        Coin fee = make(a(CoinMaker.Coin).but(with(satoshis, 10000L)));

        assertEquals(" (0.01% of trade amount)",
                GUIUtil.getPercentageOfTradeAmount(fee, oneBitcoin, Coin.ZERO));
    }
}
