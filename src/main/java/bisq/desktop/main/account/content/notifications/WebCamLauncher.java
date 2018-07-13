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

package bisq.desktop.main.account.content.notifications;

import bisq.common.UserThread;

import java.awt.Dimension;

import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;



import com.github.sarxos.webcam.Webcam;

@Slf4j
// Must not be UI thread
public class WebCamLauncher extends Thread {
    private Consumer<Webcam> resultHandler;

    public WebCamLauncher(Consumer<Webcam> resultHandler) {
        this.resultHandler = resultHandler;

        start();
    }

    @Override
    public void run() {
        try {
            Webcam webCam = Webcam.getDefault(1000); // one second timeout - the default is too long
            Dimension[] sizes = webCam.getViewSizes();
            Dimension size = sizes[sizes.length - 1]; // the largest size
            webCam.setViewSize(size);
            UserThread.execute(() -> resultHandler.accept(webCam));
        } catch (TimeoutException e) {
            log.error(e.toString());
        }
    }
}
