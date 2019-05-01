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

import bisq.core.account.score.AccountScoreCategory;
import bisq.core.account.score.AccountScoreService;
import bisq.core.account.score.ScoreInfo;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.Utilities;

import com.google.common.base.Charsets;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

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

import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.getBigIconForLabel;
import static bisq.desktop.util.FormBuilder.getIconForLabel;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PeerInfoIcon extends Group {
    private final int numTrades;
    @Nullable
    private final Offer offer;
    @Nullable
    private final Trade trade;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final AccountScoreService accountScoreService;
    private final Map<String, String> peerTagMap;
    private final Label numTradesLabel;
    private final Label tagLabel;
    protected final Pane tagPane;
    protected final Pane numTradesPane;
    private final String fullAddress;
    private final double scaleFactor;
    private final Label delayLabel, accountLevelIcon, delayIcon, signIcon;
    private final BSFormatter formatter;
    private String tooltipText;

    public PeerInfoIcon(NodeAddress nodeAddress,
                        String role,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Offer offer,
                        Preferences preferences,
                        AccountAgeWitnessService accountAgeWitnessService,
                        BSFormatter formatter,
                        boolean useDevPrivilegeKeys,
                        AccountScoreService accountScoreService) {
        this(nodeAddress,
                role,
                numTrades,
                privateNotificationManager,
                offer,
                null,
                preferences,
                accountAgeWitnessService,
                formatter,
                useDevPrivilegeKeys,
                accountScoreService);
    }

    public PeerInfoIcon(NodeAddress nodeAddress,
                        String role,
                        int numTrades,
                        PrivateNotificationManager privateNotificationManager,
                        Trade trade,
                        Preferences preferences,
                        AccountAgeWitnessService accountAgeWitnessService,
                        BSFormatter formatter,
                        boolean useDevPrivilegeKeys,
                        AccountScoreService accountScoreService) {
        this(nodeAddress,
                role,
                numTrades,
                privateNotificationManager,
                null,
                trade,
                preferences,
                accountAgeWitnessService,
                formatter,
                useDevPrivilegeKeys,
                accountScoreService);
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
                         boolean useDevPrivilegeKeys,
                         AccountScoreService accountScoreService) {
        this.numTrades = numTrades;
        if (offer == null) {
            checkNotNull(trade, "trade must not be null if offer is null");
            this.offer = trade.getOffer();
        } else {
            this.offer = offer;
        }
        this.trade = trade;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.accountScoreService = accountScoreService;
        this.formatter = formatter;

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

        Optional<ScoreInfo> optionalScoreInfo = accountScoreService.getScoreInfoForMaker(offer);
        if (optionalScoreInfo.isPresent()) {
            ScoreInfo scoreInfo = optionalScoreInfo.get();
            String buyersDelay = offer.getDirection() == OfferPayload.Direction.BUY ?
                    "\n" + Res.getWithCol("peerInfo.buyersDelay") + " " + formatter.formatAccountAge(scoreInfo.getRequiredDelay()) :
                    "";

            String signedTradeAge = scoreInfo.getSignedTradeAge().isPresent() ?
                    formatter.formatAccountAge(scoreInfo.getSignedTradeAge().get()) :
                    Res.get("peerInfo.notTraded");
            signedTradeAge = "\n" + Res.getWithCol("peerInfo.tradeAge") + " " + signedTradeAge;

            String canSign = scoreInfo.isCanSign() ? Res.get("shared.yes") : Res.get("shared.no");
            canSign = "\n" + Res.getWithCol("peerInfo.canSign") + " " + canSign;
            tooltipText = hasTraded ?
                    Res.get("peerInfoIcon.tooltip.trade.scoreInfo.traded", role, fullAddress, numTrades, accountAge,
                            buyersDelay, signedTradeAge, canSign) :
                    Res.get("peerInfoIcon.tooltip.trade.scoreInfo.notTraded", role, fullAddress, accountAge,
                            buyersDelay, signedTradeAge, canSign);
        } else {
            tooltipText = hasTraded ?
                    Res.get("peerInfoIcon.tooltip.trade.traded", role, fullAddress, numTrades, accountAge) :
                    Res.get("peerInfoIcon.tooltip.trade.notTraded", role, fullAddress, accountAge);
        }
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

        //TODO just dummy impl.
        accountLevelIcon = new Label();
        accountLevelIcon.setLayoutX(-30);
        accountLevelIcon.setVisible(false);
        accountLevelIcon.setManaged(false);

        delayIcon = new Label();
        getIconForLabel(MaterialDesignIcon.CLOCK_FAST, delayIcon);
        delayIcon.setLayoutX(-50);
        delayIcon.setVisible(false);
        delayIcon.setManaged(false);

        delayLabel = new Label();
        delayLabel.setLayoutX(-63);
        delayLabel.setLayoutY(15);
        delayLabel.setMinWidth(40);
        delayLabel.setMaxWidth(40);
        delayLabel.getStyleClass().add("delay-label");
        delayLabel.setVisible(false);
        delayLabel.setManaged(false);

        signIcon = new Label();
        getIconForLabel(MaterialDesignIcon.APPROVAL, signIcon);
        signIcon.setLayoutX(-70);
        signIcon.setVisible(false);
        signIcon.setManaged(false);

        getChildren().addAll(outerBackground, innerBackground, avatarImageView, tagPane, numTradesPane, accountLevelIcon, delayIcon, delayLabel, signIcon);

        addMouseListener(numTrades, privateNotificationManager, offer, preferences, formatter, useDevPrivilegeKeys, isFiatCurrency, peersAccountAge);

        updatePeerInfoIcon();
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

            return accountAgeWitnessService.getMakersAccountAge(offer);
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
        setOnMouseClicked(e -> new PeerInfoWithTagEditor(privateNotificationManager, offer, preferences, accountScoreService, formatter, useDevPrivilegeKeys)
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

        if (!accountScoreService.ignoreRestrictions(offer)) {
            Optional<ScoreInfo> optionalScoreInfo;
            if (trade == null) {
                optionalScoreInfo = accountScoreService.getScoreInfoForMaker(offer);
            } else {
                optionalScoreInfo = accountScoreService.getScoreInfoForBuyer(trade);
            }
            boolean isScoreInfoPresent = optionalScoreInfo.isPresent();
            accountLevelIcon.setVisible(isScoreInfoPresent);
            accountLevelIcon.setManaged(isScoreInfoPresent);
            delayIcon.setVisible(isScoreInfoPresent);
            delayIcon.setManaged(isScoreInfoPresent);
            signIcon.setVisible(isScoreInfoPresent);
            signIcon.setManaged(isScoreInfoPresent);

            if (isScoreInfoPresent) {
                //TODO just dummy impl.
                ScoreInfo scoreInfo = optionalScoreInfo.get();
                boolean canSign = scoreInfo.isCanSign();
                signIcon.setVisible(canSign);
                signIcon.setManaged(canSign);

                long requiredDelay = scoreInfo.getRequiredDelay();
                String age = Utilities.toTruncatedString(formatter.formatAccountAge(requiredDelay), 8);
                delayLabel.setText(age);

                boolean requireDelay = requiredDelay > 0;
                delayIcon.setVisible(requireDelay);
                delayIcon.setManaged(requireDelay);
                delayLabel.setVisible(requireDelay);
                delayLabel.setManaged(requireDelay);

                if (scoreInfo.getAccountScoreCategory() == AccountScoreCategory.GOLD) {
                    getBigIconForLabel(MaterialDesignIcon.SHIELD, accountLevelIcon)
                            .getStyleClass().add("score-gold");
                } else if (scoreInfo.getAccountScoreCategory() == AccountScoreCategory.SILVER) {
                    getBigIconForLabel(MaterialDesignIcon.SHIELD_HALF_FULL, accountLevelIcon)
                            .getStyleClass().add("score-silver");
                } else if (scoreInfo.getAccountScoreCategory() == AccountScoreCategory.BRONZE) {
                    getBigIconForLabel(MaterialDesignIcon.SHIELD_OUTLINE, accountLevelIcon)
                            .getStyleClass().add("score-bronze");
                }
            }
        }
    }
}
