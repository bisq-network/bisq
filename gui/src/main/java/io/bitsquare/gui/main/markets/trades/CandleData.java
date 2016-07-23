package io.bitsquare.gui.main.markets.trades;

public class CandleData {
    public final long tick; // Is the time tick in the chosen time interval
    public final long open;
    public final long close;
    public final long high;
    public final long low;
    public final long average;
    public final long amount;
    public final long volume;
    public final boolean isBullish;

    public CandleData(long tick, long open, long close, long high, long low, long average, long amount, long volume, boolean isBullish) {
        this.tick = tick;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.average = average;
        this.amount = amount;
        this.volume = volume;
        this.isBullish = isBullish;
    }
}
