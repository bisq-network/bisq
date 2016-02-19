package io.bitsquare.common;

public interface Clock {
    void start();

    void stop();

    void addListener(Listener listener);

    void removeListener(Listener listener);

    interface Listener {
        void onSecondTick();

        void onMinuteTick();

        void onMissedSecondTick(long missed);
    }
}
