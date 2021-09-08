package bisq.desktop.main.community.platform;

import bisq.desktop.common.view.AbstractView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.common.util.Utilities;

import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXButton;

import javafx.fxml.FXML;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

@FxmlView
public class PlatformView extends AbstractView<HBox, Void> {

    @FXML
    Label imageLabel, titleLabel, descriptionLabel;

    @FXML
    JFXButton openURLButton, copyURLButton;

    public void setData(Platform platform, boolean wider) {
        if (platform.getIconClass() != null) {
            getRoot().setSpacing(15.0);
            imageLabel.getStyleClass().addAll("community-icon", platform.getIconClass());
        }
        titleLabel.setText(platform.getTitle());
        descriptionLabel.setText(platform.getDescription());
        openURLButton.setText("Open URL"); // TODO: move to translations
        openURLButton.setGraphic(FormBuilder.getIcon(AwesomeIcon.EXTERNAL_LINK));
        openURLButton.setOnAction((event) -> GUIUtil.openWebPage(platform.getUrl(), false));
        copyURLButton.setText("Copy URL"); // TODO: move to translations
        copyURLButton.setGraphic(FormBuilder.getIcon(AwesomeIcon.COPY));
        copyURLButton.setOnAction((event) -> Utilities.copyToClipboard(platform.getUrl()));
        getRoot().setPrefWidth(wider ? 600 : 450);
    }
}
