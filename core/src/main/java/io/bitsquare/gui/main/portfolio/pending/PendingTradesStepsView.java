/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pending;

import io.bitsquare.gui.util.Layout;

import javafx.scene.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PendingTradesStepsView extends HBox {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesStepsView.class);

    protected VBox leftVBox;
    protected AnchorPane contentPane;
    protected TradeWizardItem current;

    public PendingTradesStepsView() {
        setSpacing(Layout.PADDING_WINDOW);
        buildViews();
    }

    public void activate() {

    }

    public void deactivate() {
    }

    protected void buildViews() {
        addLeftBox();
        addContentPane();
        addWizards();
        activate();
    }

    abstract protected void addWizards();

    protected void showItem(TradeWizardItem item) {
        if (current != null)
            current.onCompleted();
        current = item;
        current.show();
        loadView(item.getViewClass());
    }

    protected void loadView(Class<? extends Node> viewClass) {
        Node view = null;
        try {
            view = viewClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        contentPane.getChildren().setAll(view);
    }

    private void addLeftBox() {
        leftVBox = new VBox();
        leftVBox.setSpacing(Layout.SPACING_VBOX);
        getChildren().add(leftVBox);
    }

    private void addContentPane() {
        contentPane = new AnchorPane();
        HBox.setHgrow(contentPane, Priority.SOMETIMES);
        getChildren().add(contentPane);
    }


}



