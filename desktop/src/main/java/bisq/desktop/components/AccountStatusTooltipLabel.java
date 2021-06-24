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

import bisq.desktop.components.controlsfx.control.PopOver;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.locale.Res;
import bisq.core.offer.OfferRestrictions;
import bisq.core.util.coin.CoinFormatter;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;


public class AccountStatusTooltipLabel extends AutoTooltipLabel {

    public static final int DEFAULT_WIDTH = 300;
    private final Node textIcon;
    private final PopOverWrapper popoverWrapper = new PopOverWrapper();
    private final OfferBookListItem.WitnessAgeData witnessAgeData;
    private final String popupTitle;

    public AccountStatusTooltipLabel(OfferBookListItem.WitnessAgeData witnessAgeData,
                                     CoinFormatter formatter) {
        super(witnessAgeData.getDisplayString());
        this.witnessAgeData = witnessAgeData;
        this.textIcon = FormBuilder.getIcon(witnessAgeData.getIcon());
        this.popupTitle = witnessAgeData.isLimitLifted()
                ? Res.get("offerbook.timeSinceSigning.tooltip.accountLimitLifted")
                : Res.get("offerbook.timeSinceSigning.tooltip.accountLimit", formatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT));

        positionAndActivateIcon();
    }

    private void positionAndActivateIcon() {
        textIcon.setOpacity(0.4);
        textIcon.getStyleClass().add("tooltip-icon");
        textIcon.setOnMouseEntered(e -> popoverWrapper.showPopOver(this::createPopOver));
        textIcon.setOnMouseExited(e -> popoverWrapper.hidePopOver());

        setGraphic(textIcon);
        setContentDisplay(ContentDisplay.RIGHT);
    }

    private PopOver createPopOver() {
        Label titleLabel = new Label(popupTitle);
        titleLabel.setMaxWidth(DEFAULT_WIDTH);
        titleLabel.setWrapText(true);
        titleLabel.setPadding(new Insets(10, 10, 2, 10));
        titleLabel.getStyleClass().add("bold-text");
        titleLabel.getStyleClass().add("default-text");

        Label infoLabel = new Label(witnessAgeData.getInfo());
        infoLabel.setMaxWidth(DEFAULT_WIDTH);
        infoLabel.setWrapText(true);
        infoLabel.setPadding(new Insets(2, 10, 2, 10));
        infoLabel.getStyleClass().add("default-text");

        Label buyLabel = createDetailsItem(
                Res.get("offerbook.timeSinceSigning.tooltip.checkmark.buyBtc"),
                witnessAgeData.isAccountSigned()
        );
        Label waitLabel = createDetailsItem(
                Res.get("offerbook.timeSinceSigning.tooltip.checkmark.wait", SignedWitnessService.SIGNER_AGE_DAYS),
                witnessAgeData.isLimitLifted()
        );

        Hyperlink learnMoreLink = new Hyperlink(Res.get("offerbook.timeSinceSigning.tooltip.learnMore"));
        learnMoreLink.setMaxWidth(DEFAULT_WIDTH);
        learnMoreLink.setWrapText(true);
        learnMoreLink.setPadding(new Insets(10, 10, 2, 10));
        learnMoreLink.getStyleClass().add("very-small-text");
        learnMoreLink.setOnAction((e) -> GUIUtil.openWebPage("https://bisq.wiki/Account_limits"));

        VBox vBox = new VBox(2, titleLabel, infoLabel, buyLabel, waitLabel, learnMoreLink);
        vBox.setPadding(new Insets(2, 0, 2, 0));
        vBox.setAlignment(Pos.CENTER_LEFT);

        PopOver popOver = new PopOver(vBox);
        if (textIcon.getScene() != null) {
            popOver.setDetachable(false);
            popOver.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);
            popOver.show(textIcon, -10);
        }
        return popOver;
    }

    private Label createDetailsItem(String text, boolean active) {
        Label icon = FormBuilder.getIcon(active ? AwesomeIcon.OK_SIGN : AwesomeIcon.REMOVE_SIGN);
        icon.setLayoutY(4);
        icon.getStyleClass().add("icon");
        if (active) {
            icon.getStyleClass().add("highlight");
        } else {
            icon.getStyleClass().add("account-status-inactive-info-item");
        }

        Label label = new Label(text, icon);
        label.setMaxWidth(DEFAULT_WIDTH);
        label.setWrapText(true);
        label.setPadding(new Insets(0, 10, 0, 10));
        label.getStyleClass().addAll("small-text");
        if (active) {
            label.getStyleClass().add("success-text");
        } else {
            label.getStyleClass().add("account-status-inactive-info-item");
        }
        return label;
    }
}
