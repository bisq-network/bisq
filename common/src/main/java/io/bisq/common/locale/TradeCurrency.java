/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.common.locale;

import io.bisq.common.app.Version;
import io.bisq.common.persistence.Persistable;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public abstract class TradeCurrency implements Persistable, Comparable<TradeCurrency> {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    protected final String code;
    protected final String name;
    @Nullable
    protected String symbol;

    protected TradeCurrency(String code, String name) {
        this(code, name, null);
    }

    public TradeCurrency(String code, String name, @Nullable String symbol) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
    }

    public String getDisplayPrefix() {
        return "";
    }

    public String getNameAndCode() {
        return name + " (" + code + ")";
    }

    public String getCodeAndName() {
        return code + " (" + name + ")";
    }

    @Override
    public int compareTo(@NotNull TradeCurrency other) {
        return this.code.compareTo(other.code);
    }

    public static TradeCurrency fromProto(PB.TradeCurrency tradeCurrency) {
        switch (tradeCurrency.getMessageCase()) {
            case FIAT_CURRENCY:
                return new FiatCurrency(tradeCurrency.getCode());
            case CRYPTO_CURRENCY:
                return new CryptoCurrency(tradeCurrency.getCode(), tradeCurrency.getName(), tradeCurrency.getSymbol(),
                        tradeCurrency.getCryptoCurrency().getIsAsset());
            default:
                log.warn("Unknown tradecurrency: {}", tradeCurrency.getMessageCase());
                return null;
        }
    }
}
