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

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;



import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

/**
 * Continuously reads from the provided image capture device, a JavaCV
 * {@link FrameGrabber}, processing captured frames until the provided processor
 * finds a result.
 *
 * @param <T> The object type that will be returned from the processor once it finds
 *            a result.
 */
public class ImageCaptureReader<T> extends Thread implements AutoCloseable {
    private final FrameGrabber frameGrabber;
    private final FrameConverter<Image> frameToImageConverter;
    private final FrameProcessor<T> frameProcessor;
    private final ImageView imageView;
    private final Consumer<T> resultHandler;
    private final ExceptionHandler exceptionHandler;
    private boolean isRunning;

    /**
     * @param frameGrabber The JavaCV {@link FrameGrabber} to capture frames from.
     * @param frameToImageConverter A {@link FrameConverter<Image>} that will convert
     *                              captured JavaCV {@link Frame}'s to a JavaFx
     *                              {@link Image} to be shown within the {@link #imageView}.
     * @param frameProcessor A {@link FrameProcessor<T>} that will process captured frames
     *                       looking for a result.
     * @param imageView The JavaFx {@link ImageView} to show captured frames within.
     * @param resultHandler The action to perform once the {@link #frameProcessor} finds
     *                      a result.
     * @param exceptionHandler The action to perform if an error is encountered while
     *                         capturing images.
     */
    public ImageCaptureReader(@NotNull final FrameGrabber frameGrabber,
                              @NotNull final FrameConverter<Image> frameToImageConverter,
                              @NotNull final FrameProcessor<T> frameProcessor,
                              @NotNull final ImageView imageView,
                              @NotNull final Consumer<T> resultHandler,
                              @NotNull final ExceptionHandler exceptionHandler) {
        this.frameGrabber = Objects.requireNonNull(frameGrabber,
                "FrameGrabber must not be null");
        this.frameToImageConverter = Objects.requireNonNull(frameToImageConverter,
                "FrameConverter must not be null");
        this.frameProcessor = Objects.requireNonNull(frameProcessor,
                "FrameProcessor must not be null");
        this.imageView = Objects.requireNonNull(imageView,
                "ImageView must not be null");
        this.resultHandler = Objects.requireNonNull(resultHandler,
                "ResultHandler must not be null");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler,
                "ExceptionHandler must not be null");

        start();
    }

    @Override
    public void run() {
        try {
            frameGrabber.start();
        } catch (FrameGrabber.Exception e) {
            UserThread.execute(() -> exceptionHandler.handleException(
                    new ImageCaptureDeviceNotFoundException(e)));
            return;
        }

        try {
            isRunning = true;
            while (isRunning) {
                try (Frame capturedFrame = frameGrabber.grabAtFrameRate()) {
                    if (capturedFrame == null) {
                        throw new FrameGrabber.Exception("Failed to capture frame");
                    }

                    final Image image = frameToImageConverter.convert(capturedFrame);
                    imageView.setImage(image);

                    frameProcessor.process(capturedFrame).ifPresent(result -> {
                        isRunning = false;
                        UserThread.execute(() -> resultHandler.accept(result));
                    });
                }
            }
        } catch (FrameGrabber.Exception e) {
            if (isRunning) {
                UserThread.execute(() -> exceptionHandler.handleException(
                        new ImageCaptureException(e)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                frameGrabber.close();
            } catch (FrameGrabber.Exception ignored) {
                // Don't care if this throws an exception at this point
            }
            close();
        }
    }

    @Override
    public void close() {
        isRunning = false;
    }
}
