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

package bisq.desktop.components;

import bisq.desktop.main.overlays.editor.PeerInfoWithTagEditor;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.NodeAddress;

import com.google.common.base.Charsets;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import javafx.geometry.Point2D;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.Date;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PeerInfoIcon extends Group {
    private final String tooltipText;
    private final int numTrades;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final Map<String, String> peerTagMap;
    private final Label numTradesLabel;
    private final Label tagLabel;
    protected final Pane tagPane;
    protected final Pane numTradesPane;
    private final String fullAddress;
    private final double scaleFactor;

    public PeerInfoIcon(NodeAddress nodeAddress,
                        String role,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Offer offer,
                        Preferences preferences,
                        AccountAgeWitnessService accountAgeWitnessService,
                        BSFormatter formatter,
                        boolean useDevPrivilegeKeys) {
        this(nodeAddress,
                role,
                numTrades,
                privateNotificationManager,
                offer,
                null,
                preferences,
                accountAgeWitnessService,
                formatter,
                useDevPrivilegeKeys);

    }

    public PeerInfoIcon(NodeAddress nodeAddress,
                        String role,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Trade trade,
                        Preferences preferences,
                        AccountAgeWitnessService accountAgeWitnessService,
                        BSFormatter formatter,
                        boolean useDevPrivilegeKeys) {
        this(nodeAddress,
                role,
                numTrades,
                privateNotificationManager,
                null,
                trade,
                preferences,
                accountAgeWitnessService,
                formatter,
                useDevPrivilegeKeys);
    }

    private PeerInfoIcon(NodeAddress nodeAddress,
                         String role,
                         int numTrades,
                         PrivateNotificationManager privateNotificationManager,
                         @Nullable Offer offer,
                         @Nullable Trade trade,
                         Preferences preferences,
                         AccountAgeWitnessService accountAgeWitnessService,
                         BSFormatter formatter,
                         boolean useDevPrivilegeKeys) {
        this.numTrades = numTrades;
        this.accountAgeWitnessService = accountAgeWitnessService;

        scaleFactor = getScaleFactor();
        fullAddress = nodeAddress != null ? nodeAddress.getFullAddress() : "";

        peerTagMap = preferences.getPeerTagMap();

        boolean hasTraded = numTrades > 0;
        long peersAccountAge = getPeersAccountAge(trade, offer);
        if (offer == null) {
            checkNotNull(trade, "Trade must not be null if offer is null.");
            offer = trade.getOffer();
        }

        checkNotNull(offer, "Offer must not be null");

        boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode());

        String accountAge = isFiatCurrency ?
                peersAccountAge > -1 ? Res.get("peerInfoIcon.tooltip.age", formatter.formatAccountAge(peersAccountAge)) :
                        Res.get("peerInfoIcon.tooltip.unknownAge") :
                "";
        tooltipText = hasTraded ?
                Res.get("peerInfoIcon.tooltip.trade.traded", role, fullAddress, numTrades, accountAge) :
                Res.get("peerInfoIcon.tooltip.trade.notTraded", role, fullAddress, accountAge);

        // outer circle
        Color ringColor;
        if (isFiatCurrency) {
            switch (accountAgeWitnessService.getAccountAgeCategory(peersAccountAge)) {
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

        double outerSize = 26 * scaleFactor;
        Canvas outerBackground = new Canvas(outerSize, outerSize);
        GraphicsContext outerBackgroundGc = outerBackground.getGraphicsContext2D();
        outerBackgroundGc.setFill(ringColor);
        outerBackgroundGc.fillOval(0, 0, outerSize, outerSize);
        outerBackground.setLayoutY(1 * scaleFactor);

        // inner circle
        int maxIndices = 15;
        int intValue = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] bytes = md.digest(fullAddress.getBytes(Charsets.UTF_8));
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

        double innerSize = scaleFactor * 22;
        Canvas innerBackground = new Canvas(innerSize, innerSize);
        GraphicsContext innerBackgroundGc = innerBackground.getGraphicsContext2D();
        innerBackgroundGc.setFill(innerColor);
        innerBackgroundGc.fillOval(0, 0, innerSize, innerSize);
        innerBackground.setLayoutY(3 * scaleFactor);
        innerBackground.setLayoutX(2 * scaleFactor);

        ImageView avatarImageView = new ImageView();
        avatarImageView.setId("avatar_" + index);
        avatarImageView.setLayoutX(0);
        avatarImageView.setLayoutY(1 * scaleFactor);
        avatarImageView.setFitHeight(scaleFactor * 26);
        avatarImageView.setFitWidth(scaleFactor * 26);

        numTradesPane = new Pane();
        numTradesPane.relocate(scaleFactor * 18, scaleFactor * 14);
        numTradesPane.setMouseTransparent(true);
        ImageView numTradesCircle = new ImageView();
        numTradesCircle.setId("image-green_circle");
        numTradesLabel = new AutoTooltipLabel();
        numTradesLabel.relocate(scaleFactor * 5, scaleFactor * 1);
        numTradesLabel.setId("ident-num-label");
        numTradesPane.getChildren().addAll(numTradesCircle, numTradesLabel);

        tagPane = new Pane();
        tagPane.relocate(Math.round(scaleFactor * 18), scaleFactor * -2);
        tagPane.setMouseTransparent(true);
        ImageView tagCircle = new ImageView();
        tagCircle.setId("image-blue_circle");
        tagLabel = new AutoTooltipLabel();
        tagLabel.relocate(Math.round(scaleFactor * 5), scaleFactor * 1);
        tagLabel.setId("ident-num-label");
        tagPane.getChildren().addAll(tagCircle, tagLabel);

        updatePeerInfoIcon();

        getChildren().addAll(outerBackground, innerBackground, avatarImageView, tagPane, numTradesPane);

        addMouseListener(numTrades, privateNotificationManager, offer, preferences, formatter, useDevPrivilegeKeys, isFiatCurrency, peersAccountAge);
    }

    private long getPeersAccountAge(@Nullable Trade trade, @Nullable Offer offer) {
        if (trade != null) {
            offer = trade.getOffer();
            if (offer == null) {
                // unexpected
                return -1;
            }

            return accountAgeWitnessService.getTradingPeersAccountAge(trade);
        } else {
            checkNotNull(offer, "Offer must not be null if trade is null.");

            return accountAgeWitnessService.getMakersAccountAge(offer, new Date());
        }
    }

    protected void addMouseListener(int numTrades,
                                    PrivateNotificationManager privateNotificationManager,
                                    Offer offer,
                                    Preferences preferences,
                                    BSFormatter formatter,
                                    boolean useDevPrivilegeKeys,
                                    boolean isFiatCurrency,
                                    long makersAccountAge) {
        final String accountAgeTagEditor = isFiatCurrency ?
                makersAccountAge > -1 ?
                        formatter.formatAccountAge(makersAccountAge) :
                        Res.get("peerInfo.unknownAge") :
                null;
        setOnMouseClicked(e -> new PeerInfoWithTagEditor(privateNotificationManager, offer, preferences, useDevPrivilegeKeys)
                .fullAddress(fullAddress)
                .numTrades(numTrades)
                .accountAge(accountAgeTagEditor)
                .position(localToScene(new Point2D(0, 0)))
                .onSave(newTag -> {
                    preferences.setTagForPeer(fullAddress, newTag);
                    updatePeerInfoIcon();
                })
                .show());
    }

    protected double getScaleFactor() {
        return 1;
    }

    protected void updatePeerInfoIcon() {
        String tag;
        if (peerTagMap.containsKey(fullAddress)) {
            tag = peerTagMap.get(fullAddress);
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
