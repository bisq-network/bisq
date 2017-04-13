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

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;

import java.util.Optional;

public final class CryptoCurrency extends TradeCurrency {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
    private final static String PREFIX = "✦ ";

    @Getter
    private boolean isAsset = false;

    public CryptoCurrency(String currencyCode, String name) {
        this(currencyCode, name, false);
    }

    public CryptoCurrency(String currencyCode, String name, boolean isAsset) {
        super(currencyCode, name);
        this.isAsset = isAsset;
    }

    public CryptoCurrency(String currencyCode, String name, String symbol, boolean isAsset) {
        super(currencyCode, name, symbol);
        this.isAsset = isAsset;
    }

    @Override
    public String getDisplayPrefix() {
        return PREFIX;
    }

    @Override
    public Message toProtobuf() {
        PB.TradeCurrency.Builder builder = PB.TradeCurrency.newBuilder()
                .setCode(code)
                .setName(name)
                .setCryptoCurrency(PB.CryptoCurrency.newBuilder().setIsAsset(isAsset));
        Optional.ofNullable(symbol).ifPresent(symbol -> builder.setSymbol(symbol));
        return builder.build();
    }
}
