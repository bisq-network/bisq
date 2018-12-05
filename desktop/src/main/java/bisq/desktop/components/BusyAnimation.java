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

import com.jfoenix.controls.JFXSpinner;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class BusyAnimation extends JFXSpinner {

    private final BooleanProperty isRunningProperty = new SimpleBooleanProperty();

    public BusyAnimation() {
        this(true);
    }

    public BusyAnimation(boolean isRunning) {
        getStyleClass().add("busyanimation");
        isRunningProperty.set(isRunning);

        updateVisibility();
    }

    public void play() {
        isRunningProperty.set(true);

        setProgress(-1);
        updateVisibility();
    }

    public void stop() {
        isRunningProperty.set(false);
        setProgress(0);
        updateVisibility();
    }

    public boolean isRunning() {
        return isRunningProperty.get();
    }

    public BooleanProperty isRunningProperty() {
        return isRunningProperty;
    }

    private void updateVisibility() {
        setVisible(isRunning());
        setManaged(isRunning());
    }
}
