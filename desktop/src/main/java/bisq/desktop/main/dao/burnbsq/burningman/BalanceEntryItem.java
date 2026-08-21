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

package bisq.desktop.main.dao.burnbsq.burningman;

import bisq.desktop.util.DisplayUtils;

import bisq.core.dao.burningman.accounting.balance.BalanceEntry;
import bisq.core.dao.burningman.accounting.balance.BaseBalanceEntry;
import bisq.core.dao.burningman.accounting.balance.BurnedBsqBalanceEntry;
import bisq.core.dao.burningman.accounting.balance.MonthlyBalanceEntry;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import com.google.common.base.Joiner;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j

@EqualsAndHashCode
class BalanceEntryItem {
    private final BalanceEntry balanceEntry;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    @Getter
    private final Date date;
    @Getter
    private final Date month;
    @Getter
    private final Optional<Price> price;
    @Getter
    private final Optional<BalanceEntry.Type> type;
    @Getter
    private final Optional<Long> receivedBtc;
    @Getter
    private final Optional<Long> receivedBtcAsBsq;
    @Getter
    private final Optional<Long> burnedBsq;
    @Getter
    private final long revenue;

    // We create the strings on demand and cache them. For large data sets it would be a bit slow otherwise.
    private String monthAsString, dateAsString, receivedBtcAsString, receivedBtcAsBsqAsString, burnedBsqAsString, revenueAsString,
            priceAsString, typeAsString;

    BalanceEntryItem(BalanceEntry balanceEntry,
                     Map<Date, Price> averageBsqPriceByMonth,
                     BsqFormatter bsqFormatter,
                     CoinFormatter btcFormatter) {
        this.balanceEntry = balanceEntry;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;

        date = balanceEntry.getDate();
        month = balanceEntry.getMonth();
        price = Optional.ofNullable(averageBsqPriceByMonth.get(month));
        if (balanceEntry instanceof MonthlyBalanceEntry) {
            MonthlyBalanceEntry monthlyBalanceEntry = (MonthlyBalanceEntry) balanceEntry;
            receivedBtc = Optional.of(monthlyBalanceEntry.getReceivedBtc());
            burnedBsq = Optional.of(-monthlyBalanceEntry.getBurnedBsq());
            type = monthlyBalanceEntry.getTypes().size() == 1 ?
                    Optional.of(monthlyBalanceEntry.getTypes().iterator().next()) :
                    Optional.empty();
        } else if (balanceEntry instanceof BurnedBsqBalanceEntry) {
            BurnedBsqBalanceEntry burnedBsqBalanceEntry = (BurnedBsqBalanceEntry) balanceEntry;
            receivedBtc = Optional.empty();
            burnedBsq = Optional.of(-burnedBsqBalanceEntry.getAmount());
            type = Optional.of(burnedBsqBalanceEntry.getType());
        } else {
            BaseBalanceEntry baseBalanceEntry = (BaseBalanceEntry) balanceEntry;
            receivedBtc = Optional.of(baseBalanceEntry.getAmount());
            burnedBsq = Optional.empty();
            type = Optional.of(baseBalanceEntry.getType());
        }

        if (price.isEmpty() || price.get().getValue() == 0 || receivedBtc.isEmpty()) {
            receivedBtcAsBsq = Optional.empty();
        } else {
            long volume = price.get().getVolumeByAmount(Coin.valueOf(receivedBtc.get())).getValue();
            receivedBtcAsBsq = Optional.of(MathUtils.roundDoubleToLong(MathUtils.scaleDownByPowerOf10(volume, 6)));
        }

        revenue = receivedBtcAsBsq.orElse(0L) + burnedBsq.orElse(0L);
    }

    String getMonthAsString() {
        if (monthAsString != null) {
            return monthAsString;
        }

        monthAsString = new SimpleDateFormat("MMM-yyyy").format(month);
        return monthAsString;
    }

    String getDateAsString() {
        if (dateAsString != null) {
            return dateAsString;
        }

        dateAsString = DisplayUtils.formatDateTime(date);
        return dateAsString;
    }

    String getReceivedBtcAsString() {
        if (receivedBtcAsString != null) {
            return receivedBtcAsString;
        }

        receivedBtcAsString = receivedBtc.filter(e -> e != 0).map(btcFormatter::formatCoin).orElse("");
        return receivedBtcAsString;
    }

    String getReceivedBtcAsBsqAsString() {
        if (receivedBtcAsBsqAsString != null) {
            return receivedBtcAsBsqAsString;
        }

        receivedBtcAsBsqAsString = receivedBtcAsBsq.filter(e -> e != 0).map(bsqFormatter::formatCoin).orElse("");
        return receivedBtcAsBsqAsString;
    }

    String getBurnedBsqAsString() {
        if (burnedBsqAsString != null) {
            return burnedBsqAsString;
        }

        burnedBsqAsString = burnedBsq.filter(e -> e != 0).map(bsqFormatter::formatCoin).orElse("");
        return burnedBsqAsString;
    }

    String getRevenueAsString() {
        if (revenueAsString != null) {
            return revenueAsString;
        }

        revenueAsString = balanceEntry instanceof MonthlyBalanceEntry ?
                bsqFormatter.formatCoin(revenue) : "";
        return revenueAsString;
    }

    String getPriceAsString() {
        if (priceAsString != null) {
            return priceAsString;
        }

        priceAsString = price.map(Price::toString).orElse("");
        return priceAsString;
    }

    String getTypeAsString() {
        if (typeAsString != null) {
            return typeAsString;
        }

        if (balanceEntry instanceof MonthlyBalanceEntry) {
            MonthlyBalanceEntry monthlyBalanceEntry = (MonthlyBalanceEntry) balanceEntry;
            typeAsString = type.map(type -> Res.get("dao.burningman.balanceEntry.type." + type.name()))
                    .orElse(Joiner.on(", ")
                            .join(monthlyBalanceEntry.getTypes().stream()
                                    .map(type -> Res.get("dao.burningman.balanceEntry.type." + type.name()))
                                    .sorted()
                                    .collect(Collectors.toList())));
        } else {
            typeAsString = type.map(type -> Res.get("dao.burningman.balanceEntry.type." + type.name())).orElse("");
        }
        return typeAsString;
    }

    // Dummy for CSV export
    @SuppressWarnings("OptionalAssignedToNull")
    BalanceEntryItem() {
        balanceEntry = null;
        bsqFormatter = null;
        btcFormatter = null;

        date = null;
        type = null;
        price = null;
        month = null;
        receivedBtc = null;
        receivedBtcAsBsq = null;
        burnedBsq = null;
        revenue = 0L;
    }
}
