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

package bisq.desktop.components;

import bisq.common.Timer;
import bisq.common.UserThread;

import javafx.scene.image.ImageView;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.concurrent.TimeUnit;

public class BusyAnimation extends ImageView {

    private Timer timer;
    private int rotation;
    private final BooleanProperty isRunningProperty = new SimpleBooleanProperty();

    public BusyAnimation() {
        this(true);
    }

    public BusyAnimation(boolean isRunning) {
        isRunningProperty.set(isRunning);

        setMouseTransparent(true);
        setId("spinner");

        sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null)
                stop();
            else if (isRunning())
                play();
        });
        isRunningProperty.addListener((obs, oldVal, newVal) -> {
            if (newVal)
                play();
            else
                stop();
        });

        updateVisibility();
    }

    public void play() {
        isRunningProperty.set(true);

        if (timer != null)
            timer.stop();
        timer = UserThread.runPeriodically(this::updateAnimation, 100, TimeUnit.MILLISECONDS);

        updateVisibility();
    }

    public void stop() {
        isRunningProperty.set(false);
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        updateVisibility();
    }

    public boolean isRunning() {
        return isRunningProperty.get();
    }

    public BooleanProperty isRunningProperty() {
        return isRunningProperty;
    }

    public void setIsRunning(boolean isRunning) {
        isRunningProperty.set(isRunning);
    }

    private void updateAnimation() {
        int increment = 36;
        rotation += increment;
        setRotate(rotation);
    }

    private void updateVisibility() {
        setVisible(isRunning());
        setManaged(isRunning());
    }
}
