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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static bisq.desktop.main.account.content.notifications.qr.FrameUtil.createFrameFromImageResource;
import static bisq.desktop.main.account.content.notifications.qr.FrameUtil.createRandomFrame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;



import com.google.zxing.BinaryBitmap;
import org.bytedeco.javacv.Frame;

class QrCodeProcessorTest {
    private final FrameConverter<BinaryBitmap> converter = new FrameToBitmapConverter();
    private final QrCodeProcessor processor = new QrCodeProcessor(converter);

    @Test
    void process_ValidQrCodeFrame_ReturnsDecodedText() {
        final Frame validQrFrame = createFrameFromImageResource("qr/Test_QR.png");
        final String expectedResult = "test";

        final Optional<String> result = processor.process(validQrFrame);

        assertTrue(result.isPresent());
        assertEquals(expectedResult, result.get());
    }

    @Test
    void process_NonQrCodeFrame_ReturnsEmptyOptional() {
        final Frame randomFrame = createRandomFrame(100, 100, 1);

        final Optional<String> result = processor.process(randomFrame);

        assertFalse(result.isPresent());
    }

    @Test
    void process_Null_ReturnsEmptyOptional() {
        final Optional<String> result = processor.process(null);

        assertFalse(result.isPresent());
    }

    @Test
    void process_InvalidFrame_ReturnsEmptyOptional() {
        try (Frame invalidFrame = createRandomFrame(0, 0, 0)) {
            final Optional<String> result = processor.process(invalidFrame);

            assertFalse(result.isPresent());
        }
    }
}
