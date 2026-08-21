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

package bisq.desktop.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Generates QR code PNG images using zxing-core directly.
 * Replaces the abandoned net.glxn:qrgen wrapper. We render the bit matrix to a PNG via ImageIO
 * rather than pulling in zxing-javase, which would add an unverified transitive dependency.
 */
@Slf4j
public class QRCodeUtil {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    // Largest QR image any caller needs; bounds the BufferedImage allocation.
    private static final int MAX_SIZE = 2048;
    // EPC QR spec (and payment QR codes in general) require error correction level M and UTF-8.
    private static final Map<EncodeHintType, ?> HINTS = Map.of(
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

    public static byte[] toPngBytes(String data, int size) {
        checkArgument(data != null && !data.isEmpty(), "QR code data must not be empty");
        checkArgument(size > 0 && size <= MAX_SIZE, "QR code size must be in (0, %s], got %s", MAX_SIZE, size);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, size, size, HINTS);
            ImageIO.write(toBufferedImage(bitMatrix), "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("QR code generation failed for size {}.", size, e);
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage toBufferedImage(BitMatrix bitMatrix) {
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? BLACK : WHITE);
            }
        }
        return image;
    }
}
