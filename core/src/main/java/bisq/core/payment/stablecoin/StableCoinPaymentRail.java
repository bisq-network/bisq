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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;

/**
 * A stablecoin on one network as a way to pay. Ported from Bisq 2
 * (bisq.account.payment_method.stable_coin.StableCoinPaymentRail).
 * <p>
 * The name of a rail is the ID of its payment method. It is stored in accounts, offers, trades
 * and trade statistics, so a rail must not be renamed or removed once it is released. A new rail
 * is added at the end, as in the payment method list of the trade statistics.
 */
public enum StableCoinPaymentRail {
    USDT_ERC20(StableCoinRepository.USDT_ERC20),
    USDT_TRC20(StableCoinRepository.USDT_TRC20),
    USDT_BEP20(StableCoinRepository.USDT_BEP20),
    USDT_SPL(StableCoinRepository.USDT_SPL),
    USDC_ERC20(StableCoinRepository.USDC_ERC20),
    USDC_SPL(StableCoinRepository.USDC_SPL),
    USDC_BEP20(StableCoinRepository.USDC_BEP20),
    USDS_ERC20(StableCoinRepository.USDS_ERC20),
    USDE_ERC20(StableCoinRepository.USDE_ERC20),
    DAI_ERC20(StableCoinRepository.DAI_ERC20),
    RLUSD_ERC20(StableCoinRepository.RLUSD_ERC20),
    USDG_ERC20(StableCoinRepository.USDG_ERC20),
    USDG_SPL(StableCoinRepository.USDG_SPL),
    PYUSD_ERC20(StableCoinRepository.PYUSD_ERC20),
    PYUSD_SPL(StableCoinRepository.PYUSD_SPL),
    U_ERC20(StableCoinRepository.U_ERC20),
    U_BEP20(StableCoinRepository.U_BEP20),
    USDF_ERC20(StableCoinRepository.USDF_ERC20);

    private static final Map<String, StableCoinPaymentRail> RAIL_BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    @Getter
    private final StableCoin stableCoin;

    StableCoinPaymentRail(StableCoin stableCoin) {
        this.stableCoin = stableCoin;
    }

    public static Optional<StableCoinPaymentRail> find(String name) {
        // An unmodifiable map does not accept a null key
        return name == null ? Optional.empty() : Optional.ofNullable(RAIL_BY_NAME.get(name));
    }
}
