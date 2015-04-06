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

package io.bitsquare.gui.util;

import java.util.function.Function;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.input.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GUIUtil {
    private static final Logger log = LoggerFactory.getLogger(GUIUtil.class);

    public static void copyToClipboard(String content) {
        if (content != null && content.length() > 0) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            clipboard.setContent(clipboardContent);
        }
    }

    public static AnimationTimer setTimeout(int delay, Function<AnimationTimer, Void> callback) {
        AnimationTimer animationTimer = new AnimationTimer() {
            final long lastTimeStamp = System.currentTimeMillis();

            @Override
            public void handle(long arg0) {
                if (System.currentTimeMillis() > delay + lastTimeStamp) {
                    Platform.runLater(() -> callback.apply(this));
                    this.stop();
                }
            }
        };
        animationTimer.start();
        return animationTimer;
    }

    public static AnimationTimer setInterval(int delay, Function<AnimationTimer, Void> callback) {
        AnimationTimer animationTimer = new AnimationTimer() {
            long lastTimeStamp = System.currentTimeMillis();

            @Override
            public void handle(long arg0) {
                if (System.currentTimeMillis() > delay + lastTimeStamp) {
                    lastTimeStamp = System.currentTimeMillis();
                    callback.apply(this);
                }
            }
        };
        animationTimer.start();
        return animationTimer;
    }
}
