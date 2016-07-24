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

package io.bitsquare.gui.main.markets.trades.charts;

public class CandleData {
    public final long tick; // Is the time tick in the chosen time interval
    public final long open;
    public final long close;
    public final long high;
    public final long low;
    public final long average;
    public final long accumulatedAmount;
    public final long accumulatedVolume;
    public final boolean isBullish;

    //  public CandleStickExtraValues(double close, double high, double low, double average, double volume) {
    public CandleData(long tick, long open, long close, long high, long low, long average, long accumulatedAmount, long accumulatedVolume, boolean isBullish) {
        this.tick = tick;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.average = average;
        this.accumulatedAmount = accumulatedAmount;
        this.accumulatedVolume = accumulatedVolume;
        this.isBullish = isBullish;
    }
}
