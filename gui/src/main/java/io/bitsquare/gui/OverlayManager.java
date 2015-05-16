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

package io.bitsquare.gui;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class OverlayManager {

    private final static List<OverlayListener> listeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OverlayManager() {
    }

    public static void blurContent() {
        listeners.stream().forEach(OverlayListener::onBlurContentRequested);
    }

    public static void removeBlurContent() {
        listeners.stream().forEach(OverlayListener::onRemoveBlurContentRequested);
    }

    public static void addListener(OverlayListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(OverlayListener listener) {
        listeners.remove(listener);
    }

    public interface OverlayListener {
        void onBlurContentRequested();

        void onRemoveBlurContentRequested();
    }
}
