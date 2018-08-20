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

import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import javafx.geometry.Point3D;

import java.awt.image.BufferedImage;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;



import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.embed.swing.SwingFXUtils;

@Slf4j
// Must not be UI thread
class QrCodeReader extends Thread {
    private final Webcam webCam;
    private final ImageView imageView;
    private final Consumer<String> resultHandler;
    private boolean isRunning;

    QrCodeReader(Webcam webCam, ImageView imageView, Consumer<String> resultHandler) {
        this.webCam = webCam;
        this.imageView = imageView;
        this.resultHandler = resultHandler;

        start();
    }

    @Override
    public void run() {
        try {
            if (!webCam.isOpen())
                webCam.open();

            isRunning = true;
            Result result;
            BufferedImage bufferedImage;
            while (isRunning) {
                bufferedImage = webCam.getImage();
                if (bufferedImage != null) {
                    WritableImage writableImage = SwingFXUtils.toFXImage(bufferedImage, null);
                    imageView.setImage(writableImage);
                    imageView.setRotationAxis(new Point3D(0.0, 1.0, 0.0));
                    imageView.setRotate(180.0);

                    LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                    try {
                        result = new MultiFormatReader().decode(bitmap);
                        isRunning = false;
                        String qrCode = result.getText();
                        UserThread.execute(() -> resultHandler.accept(qrCode));
                    } catch (NotFoundException ignore) {
                        // No qr code in image...
                    }
                }
            }
        } catch (Throwable t) {
            log.error(t.toString());
        } finally {
            webCam.close();
        }
    }

    public void close() {
        isRunning = false;
    }
}
