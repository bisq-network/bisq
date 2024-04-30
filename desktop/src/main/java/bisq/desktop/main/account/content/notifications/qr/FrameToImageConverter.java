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

import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

import java.nio.ByteBuffer;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import static org.opencv.imgproc.Imgproc.COLOR_BGR2BGRA;



import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Used for converting a JavaCV {@link Frame} into a JavaFx {@link Image} to be shown
 * within an {@link javafx.scene.image.ImageView}.
 */
public class FrameToImageConverter implements FrameConverter<Image> {
    private final FrameConverter<Mat> frameToMatConverter = new FrameToMatConverter();
    private final WritablePixelFormat<ByteBuffer> pixelFormatByteBgra =
            PixelFormat.getByteBgraPreInstance();
    private final Mat destMat = new Mat();
    private ByteBuffer buffer;

    @Override
    public Image convert(@NotNull final Frame frame) {
        Objects.requireNonNull(frame, "Frame must not be null");

        final Mat srcMat = frameToMatConverter.convert(frame);
        opencv_imgproc.cvtColor(srcMat, destMat, COLOR_BGR2BGRA);

        if (buffer == null) {
            buffer = destMat.createBuffer();
        }

        final PixelBuffer<ByteBuffer> pb = new PixelBuffer<>(
                frame.imageWidth,
                frame.imageHeight,
                buffer,
                pixelFormatByteBgra
        );
        return new WritableImage(pb);
    }
}
