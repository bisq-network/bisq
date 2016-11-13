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

package io.bitsquare.pricefeed;

@SuppressWarnings("FieldCanBeLocal")
public class PriceData {
    private final String c; // currencyCode
    private final double a; // ask;
    private final double b; // bid
    private final double l; // last

    public PriceData(String currencyCode, double ask, double bid, double last) {
        this.c = currencyCode;
        this.a = ask;
        this.b = bid;
        this.l = last;
    }
}
