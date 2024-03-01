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

package bisq.desktop.main.account.content.notifications.qr;

import bisq.common.UserThread;
import bisq.common.handlers.ExceptionHandler;

import java.util.Optional;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;



import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

/**
 * Finds the first available image capture device as a JavaCV {@link FrameGrabber}.
 */
public class ImageCaptureDeviceFinder extends Thread {
    private final Consumer<FrameGrabber> resultHandler;
    private final ExceptionHandler exceptionHandler;

    /**
     * @param resultHandler The action to perform if an available image capture device is
     *                      available.
     * @param exceptionHandler The action to perform if no available image capture devices
     *                         are available.
     */
    public ImageCaptureDeviceFinder(@NotNull final Consumer<FrameGrabber> resultHandler,
                                    @NotNull final ExceptionHandler exceptionHandler) {
        this.resultHandler = resultHandler;
        this.exceptionHandler = exceptionHandler;

        start();
    }

    @Override
    public void run() {
        getFirstAvailableCaptureDevice().ifPresentOrElse(
                frameGrabber ->
                        UserThread.execute(() -> resultHandler.accept(frameGrabber)),
                () ->
                        UserThread.execute(() -> exceptionHandler.handleException(
                                new ImageCaptureDeviceNotFoundException())));
    }

    /**
     * JavaCV doesn't provide a direct, high-level API for determining available capture
     * devices. Therefore, just try to start a device at index 0 and if it fails then no
     * capture devices are available.
     *
     * @return An {@link Optional<FrameGrabber>} containing the first available
     *         capture device, or {@link Optional#empty()} if none are available.
     */
    private Optional<FrameGrabber> getFirstAvailableCaptureDevice() {
        try (FrameGrabber frameGrabber = new OpenCVFrameGrabber(0)) {
            frameGrabber.start();
            return Optional.of(frameGrabber);
        } catch (FrameGrabber.Exception ignored) {
            return Optional.empty();
        }
    }
}
