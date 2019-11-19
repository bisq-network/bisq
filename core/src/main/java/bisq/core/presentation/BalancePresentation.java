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

package bisq.core.presentation;

import bisq.core.btc.Balances;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BalancePresentation {
    @Getter
    private final StringProperty availableBalance = new SimpleStringProperty();
    @Getter
    private final StringProperty reservedBalance = new SimpleStringProperty();
    @Getter
    private final StringProperty lockedBalance = new SimpleStringProperty();

    @Inject
    public BalancePresentation(Balances balances, @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
        balances.getAvailableBalance().addListener((observable, oldValue, newValue) -> {
            String value = formatter.formatCoinWithCode(newValue);
            // If we get full precision the BTC postfix breaks layout so we omit it
            if (value.length() > 11)
                value = formatter.formatCoin(newValue);
            availableBalance.set(value);
        });

        balances.getReservedBalance().addListener((observable, oldValue, newValue) -> {
            reservedBalance.set(formatter.formatCoinWithCode(newValue));
        });
        balances.getLockedBalance().addListener((observable, oldValue, newValue) -> {
            lockedBalance.set(formatter.formatCoinWithCode(newValue));
        });
    }
}
