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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;



import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.qrcode.QRCodeReader;
import org.bytedeco.javacv.Frame;

/**
 * Processes JavaCV {@link Frame}'s to detect and decode QR codes within the image.
 */
public class QrCodeProcessor implements FrameProcessor<String> {
    private static final Map<DecodeHintType, Object> HINTS = Map.of(
            DecodeHintType.TRY_HARDER, Boolean.TRUE
    );

    private final FrameConverter<BinaryBitmap> frameToBitmapConverter;

    public QrCodeProcessor(
            @NotNull final FrameConverter<BinaryBitmap> frameToBitmapConverter) {
        this.frameToBitmapConverter = Objects.requireNonNull(
                frameToBitmapConverter,
                "FrameConverter must not be null"
        );
    }

    /**
     * Processes the given JavaCV {@link Frame} to detect and decode any QR codes present.
     *
     * @param frame The JavaCV {@link Frame} to be processed.
     * @return An {@link Optional<String>} containing the decoded QR code text if a
     *         QR code is detected, or {@link Optional#empty()} if no QR code is found.
     */
    @Override
    public Optional<String> process(final Frame frame) {
        if (frame == null || frame.image == null || frame.imageWidth <= 0 || frame.imageHeight <= 0) {
            // Ignore the frame if null or has invalid dimensions
            return Optional.empty();
        }

        try {
            final BinaryBitmap bitmap = frameToBitmapConverter.convert(frame);
            return Optional.of(new QRCodeReader().decode(bitmap, HINTS).getText());
        } catch (Exception ignored) {
            // There is no QR code in the image
            return Optional.empty();
        }
    }
}
