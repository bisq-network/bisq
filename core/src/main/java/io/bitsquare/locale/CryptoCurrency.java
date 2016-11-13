/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.locale;

import io.bitsquare.app.Version;

public final class CryptoCurrency extends TradeCurrency {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
    private final static String PREFIX = "✦ ";
   
    private boolean isAsset;

    public CryptoCurrency(String currencyCode, String name) {
        this(currencyCode, name, false);
    }

    public CryptoCurrency(String currencyCode, String name, boolean isAsset) {
        super(currencyCode, name);
        this.isAsset = isAsset;
    }

    public CryptoCurrency(String currencyCode, String name, String symbol) {
        super(currencyCode, name, symbol);
    }

    public boolean isAsset() {
        return isAsset;
    }
    
    @Override
    public String getDisplayPrefix() {
        return PREFIX;
    }
}
