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

package bisq.desktop.main.market.trades.charts;

public class CandleData {
    public final long tick; // Is the time tick in the chosen time interval
    public final long open;
    public final long close;
    public final long high;
    public final long low;
    public final long average;
    public final long median;
    public final long accumulatedAmount;
    public final long accumulatedVolume;
    public final long numTrades;
    public final boolean isBullish;
    public final String date;
    public final long volumeInUsd;

    public CandleData(long tick, long open, long close, long high, long low, long average, long median,
                      long accumulatedAmount, long accumulatedVolume, long numTrades,
                      boolean isBullish, String date, long volumeInUsd) {
        this.tick = tick;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.average = average;
        this.median = median;
        this.accumulatedAmount = accumulatedAmount;
        this.accumulatedVolume = accumulatedVolume;
        this.numTrades = numTrades;
        this.isBullish = isBullish;
        this.date = date;
        this.volumeInUsd = volumeInUsd;
    }
}
