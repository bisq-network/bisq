package io.bitsquare.gui.components;

import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.gui.main.overlays.editor.PeerInfoWithTagEditor;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class PeerInfoIcon extends Group {
    private static final Logger log = LoggerFactory.getLogger(PeerInfoIcon.class);

    private final String hostName;
    private final String tooltipText;
    private final int numTrades;
    private PrivateNotificationManager privateNotificationManager;
    private Offer offer;
    private final Map<String, String> peerTagMap;
    private final Label numTradesLabel;
    private final double SIZE = 26;
    private final ImageView numTradesCircle;
    private final ImageView tagCircle;
    private final Label tagLabel;
    private final Pane tagPane;
    private final Pane numTradesPane;

    public PeerInfoIcon(String hostName, String tooltipText, int numTrades, PrivateNotificationManager privateNotificationManager, Offer offer) {
        this.hostName = hostName;
        this.tooltipText = tooltipText;
        this.numTrades = numTrades;
        this.privateNotificationManager = privateNotificationManager;
        this.offer = offer;

        peerTagMap = Preferences.INSTANCE.getPeerTagMap();

        int maxIndices = 15;
        int intValue = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] bytes = md.digest(hostName.getBytes());
            intValue = Math.abs(((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF));

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.error(e.toString());
        }

        int index = (intValue % maxIndices) + 1;
        double saturation = (intValue % 1000) / 1000d;
        int red = (intValue >> 8) % 256;
        int green = (intValue >> 16) % 256;
        int blue = (intValue >> 24) % 256;

        Color color = Color.rgb(red, green, blue);
        color = color.deriveColor(1, saturation, 0.8, 1); // reduce saturation and brightness

        Canvas background = new Canvas(SIZE, SIZE);
        GraphicsContext gc = background.getGraphicsContext2D();
        gc.setFill(color);
        gc.fillOval(0, 0, SIZE, SIZE);
        background.setLayoutY(1);


        ImageView avatarImageView = new ImageView();
        avatarImageView.setId("avatar_" + index);
        avatarImageView.setScaleX(intValue % 2 == 0 ? 1d : -1d);

        numTradesPane = new Pane();
        numTradesPane.relocate(18, 14);
        numTradesPane.setMouseTransparent(true);
        numTradesCircle = new ImageView();
        numTradesCircle.setId("image-green_circle");
        numTradesLabel = new Label();
        numTradesLabel.relocate(5, 1);
        numTradesLabel.setId("ident-num-label");
        numTradesPane.getChildren().addAll(numTradesCircle, numTradesLabel);

        tagPane = new Pane();
        tagPane.relocate(18, -2);
        tagPane.setMouseTransparent(true);
        tagCircle = new ImageView();
        tagCircle.setId("image-blue_circle");
        tagLabel = new Label();
        tagLabel.relocate(5, 1);
        tagLabel.setId("ident-num-label");
        tagPane.getChildren().addAll(tagCircle, tagLabel);

        updatePeerInfoIcon();

        getChildren().addAll(background, avatarImageView, tagPane, numTradesPane);

        setOnMouseClicked(e -> new PeerInfoWithTagEditor(privateNotificationManager, offer)
                .hostName(hostName)
                .numTrades(numTrades)
                .position(localToScene(new Point2D(0, 0)))
                .onSave(newTag -> {
                    Preferences.INSTANCE.setTagForPeer(hostName, newTag);
                    updatePeerInfoIcon();
                })
                .show());
    }

    private void updatePeerInfoIcon() {
        String tag;
        if (peerTagMap.containsKey(hostName)) {
            tag = peerTagMap.get(hostName);
            Tooltip.install(this, new Tooltip(tooltipText + "\nTag: " + tag));
        } else {
            tag = "";
            Tooltip.install(this, new Tooltip(tooltipText));
        }

        if (!tag.isEmpty())
            tagLabel.setText(tag.substring(0, 1));

        if (numTrades < 10)
            numTradesLabel.setText(String.valueOf(numTrades));
        else
            numTradesLabel.setText("★");

        numTradesPane.setVisible(numTrades > 0);

        tagPane.setVisible(!tag.isEmpty());
    }
}
