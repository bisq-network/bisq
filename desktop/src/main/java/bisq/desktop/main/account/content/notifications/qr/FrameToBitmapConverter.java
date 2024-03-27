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

import java.util.Objects;

import lombok.Getter;

import org.jetbrains.annotations.NotNull;



import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Used for converting a JavaCV {@link Frame} into a ZXing {@link BinaryBitmap}.
 * It is specifically designed for integration with the ZXing library, enabling
 * QR code detection and decoding from OpenCV images.
 */
public class FrameToBitmapConverter implements FrameConverter<BinaryBitmap> {
    private final FrameConverter<Mat> frameToMatConverter = new FrameToMatConverter();

    @Override
    public BinaryBitmap convert(@NotNull final Frame frame) {
        Objects.requireNonNull(frame, "Frame must not be null");

        final Mat mat = frameToMatConverter.convert(frame);
        final OpenCVMatToGrayscaleSource source = new OpenCVMatToGrayscaleSource(mat);
        return new BinaryBitmap(new HybridBinarizer(source));
    }

    /**
     * A luminance source that facilitates the conversion of an OpenCV {@link Mat} object,
     * typically in BGR format, into a grayscale image suitable for QR scanning.
     *
     * <p>This approach allows us to eliminate our dependency on
     * {@code java.awt.BufferedImage} which is the typical intermediary.
     */
    private static class OpenCVMatToGrayscaleSource extends LuminanceSource {
        private final byte[] luminances;

        OpenCVMatToGrayscaleSource(@NotNull final Mat mat) {
            super(mat.cols(), mat.rows());
            Objects.requireNonNull(mat, "Mat must not be null");
            try (AutoCloseableMat autoCloseableMat = new AutoCloseableMat()) {
                Mat grayMat = autoCloseableMat.getMat();

                if (mat.channels() == 3) {
                    // Convert BGR to Grayscale
                    opencv_imgproc.cvtColor(mat, grayMat, opencv_imgproc.COLOR_BGR2GRAY);
                } else if (mat.channels() == 1) {
                    grayMat = mat;
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported Mat format with " + mat.channels() + " channels");
                }

                this.luminances = new byte[grayMat.cols() * grayMat.rows()];
                grayMat.data().get(this.luminances);
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            if (row == null || row.length < getWidth()) {
                row = new byte[getWidth()];
            }
            System.arraycopy(luminances, y * getWidth(), row, 0, getWidth());
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return luminances;
        }
    }

    /**
     * A wrapper class for OpenCV's {@link Mat} that implements {@link AutoCloseable},
     * facilitating the use of try-with-resources for automatic resource management.
     * This class ensures that the native memory allocated by the {@link Mat} object is
     * properly released when the {@link AutoCloseableMat} instance goes out of scope or
     * is otherwise no longer needed.
     */
    @Getter
    private static class AutoCloseableMat implements AutoCloseable {
        private final Mat mat;

        public AutoCloseableMat() {
            this.mat = new Mat();
        }

        @Override
        public void close() {
            mat.release();
        }
    }
}
