package bisq.desktop.main.community.platform;

import bisq.desktop.common.view.AbstractView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;

import bisq.common.util.Utilities;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

@FxmlView
public class PlatformView extends AbstractView<HBox, Void> {
    public void setData(Platform platform, boolean wider) {
        getRoot().setPrefWidth(wider ? 600 : 450);
        if (platform.getIconClass() != null) {
            getRoot().setSpacing(15.0);
            AutoTooltipLabel imageLabel = new AutoTooltipLabel();
            imageLabel.getStyleClass().addAll("community-icon", platform.getIconClass());
            getRoot().getChildren().add(imageLabel);
        }

        VBox box = new VBox();
        box.setSpacing(15);
        getRoot().getChildren().add(box);

        AutoTooltipLabel titleLabel = new AutoTooltipLabel(platform.getTitle());
        titleLabel.getStyleClass().add("community-platform-item-title");
        box.getChildren().add(titleLabel);

        AutoTooltipLabel descriptionLabel = new AutoTooltipLabel(platform.getDescription());
        descriptionLabel.getStyleClass().add("community-platform-item-description");
        descriptionLabel.setWrapText(true);
        box.getChildren().add(descriptionLabel);

        HBox hbox = new HBox();
        hbox.setSpacing(50);
        box.getChildren().add(hbox);

        AutoTooltipButton openURLButton = new AutoTooltipButton(Res.get("shared.openUrl"), FormBuilder.getIcon(AwesomeIcon.EXTERNAL_LINK));
        openURLButton.setOnAction((event) -> GUIUtil.openWebPage(platform.getUrl(), false));
        openURLButton.getStyleClass().add("community-platform-item-button");
        hbox.getChildren().add(openURLButton);

        AutoTooltipButton copyURLButton = new AutoTooltipButton(Res.get("shared.copyUrl"), FormBuilder.getIcon(AwesomeIcon.COPY));
        copyURLButton.setOnAction((event) -> Utilities.copyToClipboard(platform.getUrl()));
        copyURLButton.getStyleClass().add("community-platform-item-button");
        hbox.getChildren().add(copyURLButton);
    }
}
