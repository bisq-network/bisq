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

import bisq.common.util.Utilities;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Custom scroll pane that uses a workaround to fix slow vertical scrolling.
 */
public class BisqScrollPane extends ScrollPane {

    private final double BASE_DELTA_Y_MULTIPLIER = 0.8;
    private double deltaYMultiplier = BASE_DELTA_Y_MULTIPLIER;

    public BisqScrollPane() {
        super();
        if (Utilities.isLinux() || Utilities.isWindows()) {
            if (getContent() == null) {
                contentProperty().addListener(new ChangeListener<>() {
                    @Override
                    public void changed(ObservableValue<? extends Node> o, Node oldVal, Node newVal) {
                        contentProperty().removeListener(this);
                        changeScrollingSpeed();
                    }
                });
            } else {
                changeScrollingSpeed();
            }
        }
    }

    private void changeScrollingSpeed() {
        getContent().setOnScroll(scrollEvent -> {
            double deltaY = scrollEvent.getDeltaY() * deltaYMultiplier;
            double fullHeight = getContent().getBoundsInLocal().getHeight();
            double visibleHeight = getBoundsInLocal().getHeight();
            double heightDiff = fullHeight - visibleHeight;
            double diff = heightDiff > 1 ? heightDiff : 1;
            setVvalue(getVvalue() - deltaY / diff);
        });
    }

    /**
     * Sets the vertical scroll amount's multiplier. Any value below BASE_DELTA_Y_MULTIPLIER
     * will decrease the scrolling speed whereas any value above will accelerate it.
     * @param deltaYMultiplier the value by which deltaY will be multiplied
     */
    public void setDeltaYMultiplier(double deltaYMultiplier) {
        this.deltaYMultiplier = deltaYMultiplier;
    }
}
