package io.bitsquare.gui.util;

import javafx.animation.AnimationTimer;

import java.util.function.Function;

public class GUIUtils
{
    /**
     * @param delay    in milliseconds
     * @param callback
     * @usage Utils.setTimeout(1000, (AnimationTimer animationTimer) -> {
     * doSomething();
     * return null;
     * });
     */
    public static void setTimeout(int delay, Function<AnimationTimer, Void> callback)
    {
        long startTime = System.currentTimeMillis();
        AnimationTimer animationTimer = new AnimationTimer()
        {
            @Override
            public void handle(long arg0)
            {
                if (System.currentTimeMillis() > delay + startTime)
                {
                    callback.apply(this);
                    this.stop();
                }
            }
        };
        animationTimer.start();
    }
}

