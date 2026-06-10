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

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QRCodeUtilTest {

    @Test
    public void roundTripPreservesUtf8Content() throws Exception {
        // EPC QR payload with non-Latin-1 recipient name
        String content = "BCD\n001\n1\nSCT\nBIC123\nŁukasz Müller Ωmega\nIBAN123\nEUR12.34";

        Result result = decode(QRCodeUtil.toPngBytes(content, 150));

        assertEquals(content, result.getText());
    }

    @Test
    public void encodesWithErrorCorrectionLevelM() throws Exception {
        Result result = decode(QRCodeUtil.toPngBytes("bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", 150));

        assertEquals("M", result.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL));
    }

    @Test
    public void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> QRCodeUtil.toPngBytes(null, 150));
        assertThrows(IllegalArgumentException.class, () -> QRCodeUtil.toPngBytes("", 150));
        assertThrows(IllegalArgumentException.class, () -> QRCodeUtil.toPngBytes("data", 0));
        assertThrows(IllegalArgumentException.class, () -> QRCodeUtil.toPngBytes("data", -1));
        assertThrows(IllegalArgumentException.class, () -> QRCodeUtil.toPngBytes("data", 100_000));
    }

    private static Result decode(byte[] pngBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        return new QRCodeReader().decode(bitmap);
    }
}
