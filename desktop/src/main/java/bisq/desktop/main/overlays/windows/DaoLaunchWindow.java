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

import bisq.desktop.components.AutoTooltipToggleButton;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.Res;

import bisq.common.app.Version;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.ObservableList;

import javafx.util.Duration;

import java.util.ArrayList;

import static bisq.desktop.util.FormBuilder.addLabel;
import static bisq.desktop.util.FormBuilder.getIconButton;

public class DaoLaunchWindow extends Overlay<DaoLaunchWindow> {
    private ImageView sectionScreenshot;
    private ToggleGroup sectionButtonsGroup;
    private ArrayList<Section> sections = new ArrayList<>();
    private IntegerProperty currentSectionIndex = new SimpleIntegerProperty(0);
    private Label sectionDescriptionLabel;
    private Timeline timeline;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void show() {
        width = 1003;
        super.show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void createGridPane() {
        super.createGridPane();

        gridPane.setVgap(0);
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.CENTER);
        gridPane.setPadding(new Insets(74, 64, 74, 64));
    }

    @Override
    protected void addHeadLine() {
        Label versionNumber = addLabel(gridPane, ++rowIndex, BisqEnvironment.DEFAULT_APP_NAME + " v" + Version.VERSION);
        versionNumber.getStyleClass().add("dao-launch-version");
        GridPane.setColumnSpan(versionNumber, 2);
        Label headlineLabel = addLabel(gridPane, ++rowIndex, headLine);
        headlineLabel.getStyleClass().add("dao-launch-headline");
        GridPane.setMargin(headlineLabel, new Insets(10, 0, 0, 0));
        GridPane.setColumnSpan(headlineLabel, 2);
    }

    @Override
    protected void addMessage() {

        sections.add(new Section(Res.get("popup.dao.launch.governance.title"), Res.get("popup.dao.launch.governance"),
                "dao-screenshot-governance"));
        sections.add(new Section(Res.get("popup.dao.launch.trading.title"), Res.get("popup.dao.launch.trading"),
                "dao-screenshot-trading"));
        sections.add(new Section(Res.get("popup.dao.launch.cheaperFees.title"), Res.get("popup.dao.launch.cheaperFees"),
                "dao-screenshot-cheaper-fees"));

        createContent();
        createSlideControls();

        addListeners();

        startAutoSectionChange();
    }

    @Override
    protected void onShow() {
        display();
    }

    @Override
    protected void addButtons() {
        super.addButtons();

        closeButton.setPrefWidth(162);
        actionButton.setPrefWidth(162);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addListeners() {
        currentSectionIndex.addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                ObservableList<Toggle> toggles = sectionButtonsGroup.getToggles();
                Toggle toggleToSelect = toggles.get(newValue.intValue() % toggles.size());
                if (sectionButtonsGroup.getSelectedToggle() != toggleToSelect)
                    sectionButtonsGroup.selectToggle(toggleToSelect);
            }
        });

        sectionButtonsGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                int index = ((SectionButton) newValue).index;
                Section selectedSection = sections.get(index);
                sectionDescriptionLabel.setText(selectedSection.description);
                sectionScreenshot.setId(selectedSection.imageId);

                currentSectionIndex.set(index);
            }
        });
    }

    private void startAutoSectionChange() {
        timeline = new Timeline(new KeyFrame(
                Duration.seconds(10),
                ae -> goToNextSection()
        ));
        timeline.setCycleCount(Animation.INDEFINITE);

        timeline.play();
    }

    private void createSlideControls() {

        sectionButtonsGroup = new ToggleGroup();

        HBox slideButtons = new HBox();
        slideButtons.setMaxWidth(616);
        slideButtons.getStyleClass().add("dao-launch-tab-box");

        sections.forEach(section -> {
            SectionButton sectionButton = new SectionButton(section.title.toUpperCase(), sections.indexOf(section));
            sectionButton.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(sectionButton, Priority.ALWAYS);
            slideButtons.getChildren().add(sectionButton);
        });

        sectionButtonsGroup.getToggles().get(0).setSelected(true);

        GridPane.setRowIndex(slideButtons, ++rowIndex);
        GridPane.setColumnSpan(slideButtons, 2);
        GridPane.setHalignment(slideButtons, HPos.CENTER);
        GridPane.setHgrow(slideButtons, Priority.NEVER);
        gridPane.getChildren().add(slideButtons);
    }

    private void createContent() {
        HBox slidingContentWithPagingBox = new HBox();
        slidingContentWithPagingBox.setPadding(new Insets(30, 0, 0, 0));
        slidingContentWithPagingBox.setAlignment(Pos.CENTER);
        Button prevButton = getIconButton(MaterialDesignIcon.ARROW_LEFT, "dao-launch-paging-button");
        prevButton.setOnAction(event -> {
            timeline.stop();

            if (currentSectionIndex.get() == 0)
                currentSectionIndex.set(sections.size() - 1);
            else
                currentSectionIndex.set(currentSectionIndex.get() - 1);
        });
        Button nextButton = getIconButton(MaterialDesignIcon.ARROW_RIGHT, "dao-launch-paging-button");
        nextButton.setOnAction(event -> {
            timeline.stop();
            goToNextSection();
        });
        VBox slidingContent = new VBox();
        slidingContent.setSpacing(36);
        sectionDescriptionLabel = new Label();
        sectionDescriptionLabel.setTextAlignment(TextAlignment.CENTER);
        sectionDescriptionLabel.getStyleClass().add("dao-launch-description");
        sectionDescriptionLabel.setMaxWidth(562);
        sectionDescriptionLabel.setWrapText(true);
        sectionDescriptionLabel.setText(sections.get(currentSectionIndex.get()).description);
        sectionScreenshot = new ImageView();
        sectionScreenshot.setId(sections.get(currentSectionIndex.get()).imageId);

        slidingContent.setAlignment(Pos.CENTER);
        slidingContent.getChildren().addAll(sectionDescriptionLabel, sectionScreenshot);
        HBox.setHgrow(slidingContent, Priority.ALWAYS);
        slidingContentWithPagingBox.getChildren().addAll(prevButton, slidingContent, nextButton);

        GridPane.setRowIndex(slidingContentWithPagingBox, ++rowIndex);
        GridPane.setColumnSpan(slidingContentWithPagingBox, 2);
        GridPane.setHgrow(slidingContent, Priority.ALWAYS);
        gridPane.getChildren().add(slidingContentWithPagingBox);
    }

    private void goToNextSection() {
        currentSectionIndex.set(currentSectionIndex.get() + 1);
    }

    private class SectionButton extends AutoTooltipToggleButton {

        int index;

        SectionButton(String title, int index) {
            super(title);
            this.index = index;

            this.setToggleGroup(sectionButtonsGroup);
            this.getStyleClass().add("slider-section-button");

            this.selectedProperty().addListener((ov, oldValue, newValue) -> this.setMouseTransparent(newValue));

            this.setOnAction(event -> timeline.stop());
        }

    }

    private class Section {
        private String title;
        String description;
        String imageId;

        Section(String title, String description, String imageId) {
            this.title = title;
            this.description = description;
            this.imageId = imageId;
        }
    }
}
