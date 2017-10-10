package io.bisq.gui.components;

import io.bisq.common.locale.Res;
import io.bisq.core.alert.PrivateNotificationManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.user.Preferences;
import io.bisq.gui.main.overlays.editor.PeerInfoWithTagEditor;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Slf4j
public class PeerInfoIcon extends Group {
    private final String hostName;
    private final String tooltipText;
    private final int numTrades;
    private final Map<String, String> peerTagMap;
    private final Label numTradesLabel;
    private final Label tagLabel;
    private final Pane tagPane;
    private final Pane numTradesPane;

    public PeerInfoIcon(String hostName,
                        String tooltipText,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Offer offer,
                        Preferences preferences) {
        this(hostName, tooltipText, numTrades, privateNotificationManager, offer, preferences, -1);
    }

    public PeerInfoIcon(String hostName,
                        String tooltipText,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Offer offer,
                        Preferences preferences,
                        int accountAgeCategory) {
        this.hostName = hostName;
        this.tooltipText = tooltipText;
        this.numTrades = numTrades;

        peerTagMap = preferences.getPeerTagMap();

        // outer circle
        Color color1;
        switch (accountAgeCategory) {
            case 1:
                color1 = Color.rgb(204, 153, 51); //brown/gold
                break;
            case 2:
                color1 = Color.rgb(204, 204, 51); // green/yellow
                break;
            case 3:
                color1 = Color.rgb(0, 153, 0); // green
                break;
            case 0:
            default:
                color1 = Color.rgb(255, 153, 51); //orange
                break;
        }

        double size1 = 26;
        Canvas outerBackground = new Canvas(size1, size1);
        GraphicsContext gc1 = outerBackground.getGraphicsContext2D();
        gc1.setFill(color1);
        gc1.fillOval(0, 0, size1, size1);
        outerBackground.setLayoutY(1);

        // inner circle
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

        Color color2 = Color.rgb(red, green, blue);
        color2 = color2.deriveColor(1, saturation, 0.8, 1); // reduce saturation and brightness

        double size2 = 22;
        Canvas innerBackground = new Canvas(size2, size2);
        GraphicsContext gc2 = innerBackground.getGraphicsContext2D();
        gc2.setFill(color2);
        gc2.fillOval(0, 0, size2, size2);
        innerBackground.setLayoutY(3);
        innerBackground.setLayoutX(2);

        ImageView avatarImageView = new ImageView();
        avatarImageView.setId("avatar_" + index);
        avatarImageView.setScaleX(intValue % 2 == 0 ? 1d : -1d);

        numTradesPane = new Pane();
        numTradesPane.relocate(18, 14);
        numTradesPane.setMouseTransparent(true);
        ImageView numTradesCircle = new ImageView();
        numTradesCircle.setId("image-green_circle");
        numTradesLabel = new Label();
        numTradesLabel.relocate(5, 1);
        numTradesLabel.setId("ident-num-label");
        numTradesPane.getChildren().addAll(numTradesCircle, numTradesLabel);

        tagPane = new Pane();
        tagPane.relocate(18, -2);
        tagPane.setMouseTransparent(true);
        ImageView tagCircle = new ImageView();
        tagCircle.setId("image-blue_circle");
        tagLabel = new Label();
        tagLabel.relocate(5, 1);
        tagLabel.setId("ident-num-label");
        tagPane.getChildren().addAll(tagCircle, tagLabel);

        updatePeerInfoIcon();

        getChildren().addAll(outerBackground, innerBackground, avatarImageView, tagPane, numTradesPane);

        setOnMouseClicked(e -> new PeerInfoWithTagEditor(privateNotificationManager, offer, preferences)
                .hostName(hostName)
                .numTrades(numTrades)
                .position(localToScene(new Point2D(0, 0)))
                .onSave(newTag -> {
                    preferences.setTagForPeer(hostName, newTag);
                    updatePeerInfoIcon();
                })
                .show());
    }

    private void updatePeerInfoIcon() {
        String tag;
        if (peerTagMap.containsKey(hostName)) {
            tag = peerTagMap.get(hostName);
            Tooltip.install(this, new Tooltip(Res.get("peerInfoIcon.tooltip", tooltipText, tag)));
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
