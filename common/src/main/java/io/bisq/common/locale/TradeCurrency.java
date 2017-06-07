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

import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.persistable.PersistablePayload;
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
public abstract class TradeCurrency implements PersistablePayload, Comparable<TradeCurrency> {
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TradeCurrency fromProto(PB.TradeCurrency proto) {
        switch (proto.getMessageCase()) {
            case FIAT_CURRENCY:
                return new FiatCurrency(proto.getCode());
            case CRYPTO_CURRENCY:
                return new CryptoCurrency(proto.getCode(),
                        proto.getName(),
                        proto.getSymbol(),
                        proto.getCryptoCurrency().getIsAsset());
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

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

}
