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
    public final String c; // currencyCode
    public final double a; // ask;
    public final double b; // bid
    public final double l; // last

    public PriceData(String currencyCode, double ask, double bid, double last) {
        this.c = currencyCode;
        this.a = ask;
        this.b = bid;
        this.l = last;
    }
}
