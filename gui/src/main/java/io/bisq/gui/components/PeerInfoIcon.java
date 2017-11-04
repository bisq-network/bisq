package io.bisq.gui.components;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.core.alert.PrivateNotificationManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.user.Preferences;
import io.bisq.gui.main.overlays.editor.PeerInfoWithTagEditor;
import io.bisq.gui.util.BSFormatter;
import io.bisq.network.p2p.NodeAddress;
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
import java.util.Date;
import java.util.Map;

@Slf4j
public class PeerInfoIcon extends Group {
    private final String tooltipText;
    private final int numTrades;
    private final Map<String, String> peerTagMap;
    private final Label numTradesLabel;
    private final Label tagLabel;
    private final Pane tagPane;
    private final Pane numTradesPane;
    private final String hostName;

    public PeerInfoIcon(NodeAddress nodeAddress,
                        String role,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Offer offer,
                        Preferences preferences,
                        AccountAgeWitnessService accountAgeWitnessService,
                        BSFormatter formatter) {
        this.numTrades = numTrades;

        hostName = nodeAddress != null ? nodeAddress.getHostName() : "";
        String address = nodeAddress != null ? nodeAddress.getFullAddress() : "";

        peerTagMap = preferences.getPeerTagMap();

        boolean hasTraded = numTrades > 0;
        final boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode());
        final long makersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer, new Date());
        final String accountAge = isFiatCurrency ?
                makersAccountAge > -1 ? Res.get("peerInfoIcon.tooltip.age", formatter.formatAccountAge(makersAccountAge)) :
                        Res.get("peerInfoIcon.tooltip.unknownAge") :
                "";
        tooltipText = hasTraded ?
                Res.get("peerInfoIcon.tooltip.trade.traded", role, hostName, numTrades, accountAge) :
                Res.get("peerInfoIcon.tooltip.trade.notTraded", role, hostName, accountAge);

        // outer circle
        Color ringColor;
        if (isFiatCurrency) {
            switch (accountAgeWitnessService.getAccountAgeCategory(makersAccountAge)) {
                case TWO_MONTHS_OR_MORE:
                    ringColor = Color.rgb(0, 225, 0); // > 2 months green
                    break;
                case ONE_TO_TWO_MONTHS:
                    ringColor = Color.rgb(0, 139, 205); // 1-2 months blue
                    break;
                case LESS_ONE_MONTH:
                default:
                    ringColor = Color.rgb(255, 140, 0); //< 1 month orange
                    break;
            }


        } else {
            // for altcoins we always display green
            ringColor = Color.rgb(0, 225, 0);
        }

        double outerSize = 26;
        Canvas outerBackground = new Canvas(outerSize, outerSize);
        GraphicsContext outerBackgroundGc = outerBackground.getGraphicsContext2D();
        outerBackgroundGc.setFill(ringColor);
        outerBackgroundGc.fillOval(0, 0, outerSize, outerSize);
        outerBackground.setLayoutY(1);

        // inner circle
        int maxIndices = 15;
        int intValue = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] bytes = md.digest(address.getBytes());
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

        Color innerColor = Color.rgb(red, green, blue);
        innerColor = innerColor.deriveColor(1, saturation, 0.8, 1); // reduce saturation and brightness

        double innerSize = 22;
        Canvas innerBackground = new Canvas(innerSize, innerSize);
        GraphicsContext innerBackgroundGc = innerBackground.getGraphicsContext2D();
        innerBackgroundGc.setFill(innerColor);
        innerBackgroundGc.fillOval(0, 0, innerSize, innerSize);
        innerBackground.setLayoutY(3);
        innerBackground.setLayoutX(2);

        ImageView avatarImageView = new ImageView();
        avatarImageView.setId("avatar_" + index);
        avatarImageView.setLayoutX(0);
        avatarImageView.setLayoutY(1);

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

        final String accountAgeTagEditor = isFiatCurrency ?
                makersAccountAge > -1 ?
                        formatter.formatAccountAge(makersAccountAge) :
                        Res.get("peerInfo.unknownAge") :
                null;
        setOnMouseClicked(e -> new PeerInfoWithTagEditor(privateNotificationManager, offer, preferences)
                .hostName(hostName)
                .numTrades(numTrades)
                .accountAge(accountAgeTagEditor)
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
            final String text = !tag.isEmpty() ? Res.get("peerInfoIcon.tooltip", tooltipText, tag) : tooltipText;
            Tooltip.install(this, new Tooltip(text));
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
