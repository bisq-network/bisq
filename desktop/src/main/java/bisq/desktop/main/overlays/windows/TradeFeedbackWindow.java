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

import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;

import com.google.inject.Inject;

import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addHyperlinkWithIcon;

@Slf4j
public class TradeFeedbackWindow extends Overlay<TradeFeedbackWindow> {
    @Inject
    public TradeFeedbackWindow() {
        type = Type.Confirmation;
    }

    @Override
    public void show() {
        headLine(Res.get("tradeFeedbackWindow.title"));
        message(Res.get("tradeFeedbackWindow.msg.feedback"));
        hideCloseButton();
        actionButtonText(Res.get("shared.close"));

        super.show();
    }

    @Override
    protected void addMessage() {
        super.addMessage();


        HyperlinkWithIcon forum = addHyperlinkWithIcon(gridPane, ++rowIndex, "https://bisq.community",
                "https://bisq.community", 40);
        GridPane.setMargin(forum, new Insets(-6, 0, 10, 0));

        AutoTooltipLabel messageLabel3 = new AutoTooltipLabel(Res.get("tradeFeedbackWindow.msg.thanks"));
        messageLabel3.setMouseTransparent(true);
        messageLabel3.setWrapText(true);
        GridPane.setHalignment(messageLabel3, HPos.LEFT);
        GridPane.setHgrow(messageLabel3, Priority.ALWAYS);
        GridPane.setRowIndex(messageLabel3, ++rowIndex);
        GridPane.setColumnIndex(messageLabel3, 0);
        GridPane.setColumnSpan(messageLabel3, 2);
        gridPane.getChildren().add(messageLabel3);
    }

    @Override
    protected void onShow() {
        display();
    }
}
