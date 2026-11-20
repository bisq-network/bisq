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
package bisq.core.payment.stablecoin;

import static bisq.core.payment.stablecoin.StableCoin.Network.BNB_SMART_CHAIN;
import static bisq.core.payment.stablecoin.StableCoin.Network.ETHEREUM;
import static bisq.core.payment.stablecoin.StableCoin.Network.SOLANA;
import static bisq.core.payment.stablecoin.StableCoin.Network.TRON;
import static bisq.core.payment.stablecoin.StableCoin.TokenStandard.BEP20;
import static bisq.core.payment.stablecoin.StableCoin.TokenStandard.ERC20;
import static bisq.core.payment.stablecoin.StableCoin.TokenStandard.SPL;
import static bisq.core.payment.stablecoin.StableCoin.TokenStandard.TRC20;

/**
 * The listed stablecoins. Ported from Bisq 2 (bisq.common.asset.StableCoinRepository), without the lookups by
 * code, which no Bisq 1 code uses: a coin on a network is found by its payment method (see
 * {@link StableCoinPaymentRail}).
 * <p>
 * The coins and networks follow docs/specifications/payment/stablecoin-payment-methods.md. The
 * token contracts are taken from two public token lists and must be checked against the
 * documentation of the issuer before a release.
 */
public class StableCoinRepository {
    public static final StableCoin USDT_ERC20 = new StableCoin("USDT", "Tether USD", "USD", ETHEREUM, ERC20,
            "0xdAC17F958D2ee523a2206206994597C13D831ec7");
    public static final StableCoin USDT_TRC20 = new StableCoin("USDT", "Tether USD", "USD", TRON, TRC20,
            "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
    // Bridged by Binance (Binance-Peg), not issued by Tether
    public static final StableCoin USDT_BEP20 = new StableCoin("USDT", "Tether USD", "USD", BNB_SMART_CHAIN, BEP20,
            "0x55d398326f99059fF775485246999027B3197955");
    public static final StableCoin USDT_SPL = new StableCoin("USDT", "Tether USD", "USD", SOLANA, SPL,
            "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB");

    public static final StableCoin USDC_ERC20 = new StableCoin("USDC", "USD Coin", "USD", ETHEREUM, ERC20,
            "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48");
    public static final StableCoin USDC_SPL = new StableCoin("USDC", "USD Coin", "USD", SOLANA, SPL,
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
    // Bridged by Binance (Binance-Peg), not issued by Circle
    public static final StableCoin USDC_BEP20 = new StableCoin("USDC", "USD Coin", "USD", BNB_SMART_CHAIN, BEP20,
            "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d");

    public static final StableCoin USDS_ERC20 = new StableCoin("USDS", "Sky Dollar", "USD", ETHEREUM, ERC20,
            "0xdC035D45d973E3EC169d2276DDab16f1e407384F");

    public static final StableCoin USDE_ERC20 = new StableCoin("USDe", "Ethena USDe", "USD", ETHEREUM, ERC20,
            "0x4c9EDD5852cd905f086C759E8383e09bff1E68B3");

    public static final StableCoin DAI_ERC20 = new StableCoin("DAI", "Dai", "USD", ETHEREUM, ERC20,
            "0x6B175474E89094C44Da98b954EedeAC495271d0F");

    public static final StableCoin RLUSD_ERC20 = new StableCoin("RLUSD", "Ripple USD", "USD", ETHEREUM, ERC20,
            "0x8292Bb45bf1Ee4d140127049757C2E0fF06317eD");

    public static final StableCoin USDG_ERC20 = new StableCoin("USDG", "Global Dollar", "USD", ETHEREUM, ERC20,
            "0xe343167631d89B6Ffc58B88d6b7fB0228795491D");
    public static final StableCoin USDG_SPL = new StableCoin("USDG", "Global Dollar", "USD", SOLANA, SPL,
            "2u1tszSeqZ3qBWF3uNGPFc8TzMk2tdiwknnRMWGWjGWH");

    public static final StableCoin PYUSD_ERC20 = new StableCoin("PYUSD", "PayPal USD", "USD", ETHEREUM, ERC20,
            "0x6c3ea9036406852006290770BEdFcAbA0e23A0e8");
    public static final StableCoin PYUSD_SPL = new StableCoin("PYUSD", "PayPal USD", "USD", SOLANA, SPL,
            "2b1kV6DkPAnxd5ixfnxCpjxmKwqjjaYmCZfHsFu24GXo");

    public static final StableCoin U_ERC20 = new StableCoin("U", "United Stables", "USD", ETHEREUM, ERC20,
            "0xcE24439F2D9C6a2289F741120FE202248B666666");
    public static final StableCoin U_BEP20 = new StableCoin("U", "United Stables", "USD", BNB_SMART_CHAIN, BEP20,
            "0xcE24439F2D9C6a2289F741120FE202248B666666");

    public static final StableCoin USDF_ERC20 = new StableCoin("USDf", "Falcon USD", "USD", ETHEREUM, ERC20,
            "0xFa2B947eEc368f42195f24F36d2aF29f7c24CeC2");
}
