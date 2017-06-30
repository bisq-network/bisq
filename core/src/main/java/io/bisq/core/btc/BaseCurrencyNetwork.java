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

package io.bisq.core.btc;

import io.bisq.core.app.BisqEnvironment;
import lombok.Getter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.*;

public enum BaseCurrencyNetwork {
    BTC_MAINNET(MainNetParams.get(), "BTC", "MAINNET", "Bitcoin"),
    BTC_TESTNET(TestNet3Params.get(), "BTC", "TESTNET", "Bitcoin"),
    BTC_REGTEST(RegTestParams.get(), "BTC", "REGTEST", "Bitcoin"),

    LTC_MAINNET(LitecoinMainNetParams.get(), "LTC", "MAINNET", "Litecoin"),
    LTC_TESTNET(LitecoinTestNet3Params.get(), "LTC", "TESTNET", "Litecoin"),
    LTC_REGTEST(LitecoinRegTestParams.get(), "LTC", "REGTEST", "Litecoin"),

    DOGE_MAINNET(DogecoinMainNetParams.get(), "DOGE", "MAINNET", "Dogecoin"),
    DOGE_TESTNET(DogecoinTestNet3Params.get(), "DOGE", "TESTNET", "Dogecoin"),
    DOGE_REGTEST(DogecoinRegTestParams.get(), "DOGE", "REGTEST", "Dogecoin");

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

    public boolean isRegtest() {
        return "REGTEST".equals(network);
    }

    public boolean isBitcoin() {
        return "BTC".equals(currencyCode);
    }

    public boolean isLitecoin() {
        return "LTC".equals(currencyCode);
    }

    public Coin getDefaultMinFee() {
        switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
            case "BTC":
                return org.bitcoinj.core.Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
            case "LTC":
            case "DOGE":
            default:
                // TODO check what is the right fee at DOGE
                return Coin.valueOf(100000);
        }
    }
}
