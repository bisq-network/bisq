package bisq.desktop.main.account.content.notifications;

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;



import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javax.swing.JFrame;

public class QrCodeWindow extends JFrame {

    private Webcam webcam;
    private Executor executor = Utilities.getListeningSingleThreadExecutor("readQR-runner");

    private WebcamPanel panel;
    private Consumer<String> qrCodeResultHandler;

    public QrCodeWindow(Consumer<String> qrCodeResultHandler) {
        this.qrCodeResultHandler = qrCodeResultHandler;

        setLayout(new FlowLayout());
        setTitle("Bisq Notification token"); //TODO

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                webcam.close();
                setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            }
        });

        executor.execute(this::doRun);
    }

    private void doRun() {
        try {
            webcam = Webcam.getDefault(1000); // one second timeout - the default is too long
            Dimension[] sizes = webcam.getViewSizes();
            webcam.setViewSize(sizes[sizes.length - 1]);
            panel = new WebcamPanel(webcam);
            add(panel);
            pack();
            setLocationRelativeTo(null);
            setVisible(true);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        // check 10 times a second for the QR code
        boolean run = true;
        while (run) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Result result = null;
            BufferedImage image;

            if (webcam != null && webcam.isOpen()) {
                if ((image = webcam.getImage()) == null)
                    continue;

                LuminanceSource source = new BufferedImageLuminanceSource(image);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                try {
                    result = new MultiFormatReader().decode(bitmap);
                } catch (NotFoundException ignore) {
                    // fall thru, it means there is no QR code in image
                }
            }

            if (result != null) {
                String qrCode = result.getText();
                //boolean sendConfirmation = phone.validatePhoneId(qrCode);
                // phone.save();
                dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
                run = false;
                UserThread.execute(() -> {
                    qrCodeResultHandler.accept(qrCode);
                });
               /* Platform.runLater(
                        () -> {
                            // app.updateGUI();
                            // app.sendConfirmation();
                        }
                );*/
            }
        }
    }
}
