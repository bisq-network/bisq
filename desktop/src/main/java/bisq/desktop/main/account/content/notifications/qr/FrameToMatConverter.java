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

import org.jetbrains.annotations.NotNull;



import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Used for converting a JavaCV {@link Frame} into a OpenCV {@link Mat}, primarily as an
 * intermediary to then be converted into another format.
 */
public class FrameToMatConverter implements FrameConverter<Mat> {
    @Override
    public Mat convert(@NotNull final Frame frame) {
        Objects.requireNonNull(frame, "Frame must not be null");

        try (OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {
            return converter.convert(frame);
        }
    }
}
