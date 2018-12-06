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


import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;

import bisq.core.locale.Res;

import bisq.common.UserThread;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import javafx.geometry.HPos;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebCamWindow extends Overlay<WebCamWindow> {

    @Getter
    private ImageView imageView = new ImageView();
    private ChangeListener<Image> listener;


    public WebCamWindow(double width, double height) {
        type = Type.Feedback;

        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
    }

    public void show() {
        headLine = Res.get("account.notifications.webCamWindow.headline");

        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    private void addContent() {
        GridPane.setHalignment(headLineLabel, HPos.CENTER);

        Label label = FormBuilder.addLabel(gridPane, ++rowIndex, Res.get("account.notifications.waitingForWebCam"));
        label.setAlignment(Pos.CENTER);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.CENTER);

        GridPane.setRowIndex(imageView, rowIndex);
        GridPane.setColumnSpan(imageView, 2);
        gridPane.getChildren().add(imageView);
    }

    @Override
    protected void addButtons() {
        super.addButtons();

        closeButton.setText(Res.get("shared.cancel"));

        listener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                UserThread.execute(() -> closeButton.setText(Res.get("shared.close")));
        };
        imageView.imageProperty().addListener(listener);
    }

    @Override
    public void hide() {
        super.hide();

        if (listener != null)
            imageView.imageProperty().removeListener(listener);
    }
}
