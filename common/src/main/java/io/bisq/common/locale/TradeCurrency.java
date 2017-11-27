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

package io.bisq.common.locale;

import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public abstract class TradeCurrency implements PersistablePayload, Comparable<TradeCurrency> {
    protected final String code;
    protected final String name;

    public TradeCurrency(String code, String name) {
        this.code = code;
        this.name = name;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TradeCurrency fromProto(PB.TradeCurrency proto) {
        switch (proto.getMessageCase()) {
            case FIAT_CURRENCY:
                return FiatCurrency.fromProto(proto);
            case CRYPTO_CURRENCY:
                return CryptoCurrency.fromProto(proto);
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }

    public PB.TradeCurrency.Builder getTradeCurrencyBuilder() {
        return PB.TradeCurrency.newBuilder()
                .setCode(code)
                .setName(name);
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
        return this.name.compareTo(other.name);
    }

}
