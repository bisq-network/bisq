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

import bisq.common.handlers.ExceptionHandler;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Optional;
import java.util.function.Consumer;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static bisq.desktop.main.account.content.notifications.qr.FrameUtil.createFrameFromImageResource;
import static org.mockito.Mockito.*;



import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

@ExtendWith(MockitoExtension.class)
class ImageCaptureReaderTest {
    @Mock
    private FrameGrabber frameGrabber;

    @Mock
    private FrameConverter<Image> frameToImageConverter;

    @Mock
    private FrameProcessor<String> frameProcessor;

    @Mock
    private ImageView imageView;

    @Mock
    private Consumer<String> resultHandler;

    @Mock
    private ExceptionHandler exceptionHandler;

    @Test
    void run_CapturesProcessesAndReturnsResultOfValidQrFrame() throws Exception {
        final Frame frame = createFrameFromImageResource("qr/Test_QR.png");
        final Image image = new FrameToImageConverter().convert(frame);
        final String processedResult = "QR Code";

        when(frameGrabber.grabAtFrameRate()).thenReturn(frame);
        when(frameToImageConverter.convert(frame)).thenReturn(image);
        when(frameProcessor.process(frame)).thenReturn(Optional.of(processedResult));

        new ImageCaptureReader<>(
                frameGrabber,
                frameToImageConverter,
                frameProcessor,
                imageView,
                resultHandler,
                exceptionHandler);

        verify(imageView, timeout(500).atLeast(1)).setImage(image);
        verify(frameProcessor, timeout(500).times(1)).process(frame);
        verify(resultHandler, timeout(500).times(1)).accept(processedResult);
        verify(frameGrabber, timeout(500).times(1)).close();
    }

    @Test
    void run_CapturesAndProcessesNonQrFrame() throws Exception {
        final Frame frame = createFrameFromImageResource("qr/Test_QR.png");
        final Image image = new FrameToImageConverter().convert(frame);

        when(frameGrabber.grabAtFrameRate()).thenReturn(frame);
        when(frameToImageConverter.convert(frame)).thenReturn(image);
        when(frameProcessor.process(frame)).thenReturn(Optional.empty());

        new ImageCaptureReader<>(
                frameGrabber,
                frameToImageConverter,
                frameProcessor,
                imageView,
                resultHandler,
                exceptionHandler);

        verify(frameProcessor, timeout(500).atLeast(1)).process(frame);
        verify(resultHandler, timeout(500).times(0)).accept(any(String.class));
        verify(frameGrabber, timeout(500).times(0)).close();
    }

    @Test
    void run_FrameGrabberStartFails_ExceptionThrown() throws Exception {
        doThrow(new FrameGrabber.Exception("Failed to start")).when(frameGrabber).start();

        new ImageCaptureReader<>(
                frameGrabber,
                frameToImageConverter,
                frameProcessor,
                imageView,
                resultHandler,
                exceptionHandler);

        verify(exceptionHandler, timeout(500))
                .handleException(any(ImageCaptureDeviceNotFoundException.class));
    }

    @Test
    void run_FrameGrabberInterrupted_ShutsDownGracefully() throws Exception {
        // Configure frame grabber to block or delay, simulating long-running operation
        when(frameGrabber.grabAtFrameRate()).thenAnswer(invocation -> {
            Thread.sleep(Long.MAX_VALUE);
            return new Frame();
        });

        final ImageCaptureReader<String> reader = new ImageCaptureReader<>(
                frameGrabber,
                frameToImageConverter,
                frameProcessor,
                imageView,
                resultHandler,
                exceptionHandler);

        // Interrupt the reader thread, simulating a shutdown request
        reader.interrupt();

        verify(frameGrabber, timeout(1000).times(1)).close();
    }

    @Test
    void run_CaptureNullFrame_ExceptionThrown() throws Exception {
        when(frameGrabber.grabAtFrameRate()).thenReturn(null);

        new ImageCaptureReader<>(
                frameGrabber,
                frameToImageConverter,
                frameProcessor,
                imageView,
                resultHandler,
                exceptionHandler);

        verify(exceptionHandler, timeout(100))
                .handleException(any(ImageCaptureException.class));
    }
}
