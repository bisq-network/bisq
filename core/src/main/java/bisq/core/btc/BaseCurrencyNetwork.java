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

package bisq.core.btc;

import org.libdohj.params.DashMainNetParams;
import org.libdohj.params.DashRegTestParams;
import org.libdohj.params.DashTestNet3Params;
import org.libdohj.params.LitecoinMainNetParams;
import org.libdohj.params.LitecoinRegTestParams;
import org.libdohj.params.LitecoinTestNet3Params;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

import lombok.Getter;

public enum BaseCurrencyNetwork {
    BTC_MAINNET(MainNetParams.get(), "BTC", "MAINNET", "Bitcoin"),
    BTC_TESTNET(TestNet3Params.get(), "BTC", "TESTNET", "Bitcoin"),
    BTC_REGTEST(RegTestParams.get(), "BTC", "REGTEST", "Bitcoin"),

    LTC_MAINNET(LitecoinMainNetParams.get(), "LTC", "MAINNET", "Litecoin"),
    LTC_TESTNET(LitecoinTestNet3Params.get(), "LTC", "TESTNET", "Litecoin"),
    LTC_REGTEST(LitecoinRegTestParams.get(), "LTC", "REGTEST", "Litecoin"),

    DASH_MAINNET(DashMainNetParams.get(), "DASH", "MAINNET", "Dash"),
    DASH_TESTNET(DashTestNet3Params.get(), "DASH", "TESTNET", "Dash"),
    DASH_REGTEST(DashRegTestParams.get(), "DASH", "REGTEST", "Dash");

    @Getter
    private final NetworkParameters parameters;
    @Getter
    private final String currencyCode;
    @Getter
    private final String network;
    @Getter
    private String currencyName;

    BaseCurrencyNetwork(NetworkParameters parameters, String currencyCode, String network, String currencyName) {
        this.parameters = parameters;
        this.currencyCode = currencyCode;
        this.network = network;
        this.currencyName = currencyName;
    }

    public boolean isMainnet() {
        return "MAINNET".equals(network);
    }

    public boolean isTestnet() {
        return "TESTNET".equals(network);
    }

    public boolean isRegtest() {
        return "REGTEST".equals(network);
    }

    public boolean isBitcoin() {
        return "BTC".equals(currencyCode);
    }

    public boolean isLitecoin() {
        return "LTC".equals(currencyCode);
    }

    public boolean isDash() {
        return "DASH".equals(currencyCode);
    }

    public long getDefaultMinFeePerByte() {
        return 1;
    }
}
