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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addLabel;

@Slf4j
public class NewTradeProtocolLaunchWindow extends Overlay<NewTradeProtocolLaunchWindow> {


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void show() {
        width = 700;
        hideCloseButton();
        super.show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setVgap(20);
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.CENTER);
        gridPane.setPadding(new Insets(74, 64, 74, 64));
    }

    @Override
    protected void addHeadLine() {

        Label versionNumber = new AutoTooltipLabel(BisqEnvironment.DEFAULT_APP_NAME + " v1.2.0");
        versionNumber.getStyleClass().add("news-version");
        HBox.setHgrow(versionNumber, Priority.ALWAYS);
        versionNumber.setMaxWidth(Double.MAX_VALUE);

        Button closeButton = FormBuilder.getIconButton(MaterialDesignIcon.CLOSE);
        closeButton.setOnAction(event -> hide());
        HBox.setHgrow(closeButton, Priority.NEVER);

        HBox header = new HBox(versionNumber, closeButton);

        GridPane.setRowIndex(header, ++rowIndex);
        GridPane.setColumnSpan(header, 2);
        gridPane.getChildren().add(header);

        Label headlineLabel = addLabel(gridPane, ++rowIndex, headLine);
        headlineLabel.getStyleClass().add("news-headline");
        GridPane.setMargin(headlineLabel, new Insets(10, 0, 0, 0));
        GridPane.setColumnSpan(headlineLabel, 2);
    }

    @Override
    protected void addMessage() {
        createContent();
    }

    @Override
    protected void onShow() {
        display();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createContent() {
        HBox content = new HBox();
        content.setMinWidth(700);
        content.setAlignment(Pos.CENTER);
        content.setSpacing(20);

        VBox accountSigning = getFeatureBox(Res.get("popup.news.launch.accountSigning.headline"),
                Res.get("popup.news.launch.accountSigning.description"),
                "image-account-signing-screenshot",
                "https://docs.bisq.network/payment-methods#account-signing");

        VBox newTradeProtocol = getFeatureBox(Res.get("popup.news.launch.ntp.headline"),
                Res.get("popup.news.launch.ntp.description"),
                "image-new-trade-protocol-screenshot",
                "https://docs.bisq.network/trading-rules");

        content.getChildren().addAll(accountSigning, newTradeProtocol);

        GridPane.setRowIndex(content, ++rowIndex);
        GridPane.setColumnSpan(content, 2);
        GridPane.setHgrow(content, Priority.ALWAYS);
        gridPane.getChildren().add(content);
    }

    @NotNull
    private VBox getFeatureBox(String title, String description, String imageId, String url) {
        Label featureTitle = new Label(title);
        featureTitle.setTextAlignment(TextAlignment.CENTER);
        featureTitle.getStyleClass().add("news-feature-headline");

        ImageView sectionScreenshot = new ImageView();
        sectionScreenshot.setId(imageId);

        Label featureDescription = new Label(description);
        featureDescription.setTextAlignment(TextAlignment.CENTER);
        featureDescription.getStyleClass().add("news-feature-description");
        featureDescription.setWrapText(true);

        AutoTooltipButton learnMoreButton = new AutoTooltipButton(Res.get("shared.learnMore"));
        learnMoreButton.getStyleClass().add("action-button");
        learnMoreButton.setOnAction(event -> {

            if (DontShowAgainLookup.showAgain(GUIUtil.OPEN_WEB_PAGE_KEY)) {
                hide();
                GUIUtil.openWebPage(url, true, () -> {
                    this.rowIndex = -1;
                    this.show();
                });
            } else {
                GUIUtil.openWebPage(url);
            }
        });

        VBox vBox = new VBox(featureTitle, sectionScreenshot, featureDescription, learnMoreButton);
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(20);
        vBox.setMaxWidth(300);
        return vBox;
    }
}
