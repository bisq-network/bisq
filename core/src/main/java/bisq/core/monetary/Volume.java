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

package bisq.core.monetary;

import bisq.core.locale.CurrencyUtil;
import bisq.core.util.ParsingUtils;

import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

public class Volume extends MonetaryWrapper implements Comparable<Volume> {
    private static final Logger log = LoggerFactory.getLogger(Volume.class);

    public Volume(Monetary monetary) {
        super(monetary);
    }

    public static Volume parse(String input, String currencyCode) {
        String cleaned = ParsingUtils.convertCharsForNumber(input);
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return new Volume(Fiat.parseFiat(currencyCode, cleaned));
        else
            return new Volume(Altcoin.parseAltcoin(currencyCode, cleaned));
    }

    @Override
    public int compareTo(@NotNull Volume other) {
        if (!this.getCurrencyCode().equals(other.getCurrencyCode()))
            return this.getCurrencyCode().compareTo(other.getCurrencyCode());
        if (this.getValue() != other.getValue())
            return this.getValue() > other.getValue() ? 1 : -1;
        return 0;
    }

    public String getCurrencyCode() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).getCurrencyCode() : ((Fiat) monetary).getCurrencyCode();
    }

    public String toPlainString() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).toPlainString() : ((Fiat) monetary).toPlainString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}
