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

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple3;
import io.bisq.gui.main.overlays.Overlay;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;

import static io.bisq.gui.util.FormBuilder.*;

public class FeeOptionWindow extends Overlay<FeeOptionWindow> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface ResultHandler {
        void handle(boolean isCurrencyForMakerFeeBtc);
    }

    private TextField makerFeeTextField;
    private ChangeListener<Toggle> toggleChangeListener;
    private ResultHandler resultHandler;
    private final StringProperty makerFeeWithCodeProperty;
    private final boolean isCurrencyForMakerFeeBtc;
    private ToggleGroup toggleGroup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FeeOptionWindow(StringProperty makerFeeWithCodeProperty, boolean isCurrencyForMakerFeeBtc) {
        this.makerFeeWithCodeProperty = makerFeeWithCodeProperty;
        this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("feeOptionWindow.headline");

        width = 900;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
        addDontShowAgainCheckBox();
        applyStyles();
        display();
    }

    public FeeOptionWindow onResultHandler(ResultHandler resultHandler) {
        this.resultHandler = resultHandler;
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setStyle("-fx-background-color: -bs-content-bg-grey;" +
                        "-fx-background-radius: 5 5 5 5;" +
                        "-fx-effect: dropshadow(gaussian, #999, 10, 0, 0, 0);" +
                        "-fx-background-insets: 10;"
        );
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    protected void doClose() {
        super.doClose();
        if (makerFeeTextField != null)
            makerFeeTextField.textProperty().unbind();
        if (toggleGroup != null)
            toggleGroup.selectedToggleProperty().removeListener(toggleChangeListener);
    }

    private void addContent() {
        Label label = addLabel(gridPane, ++rowIndex, Res.get("feeOptionWindow.info"));
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);

        toggleGroup = new ToggleGroup();
        Tuple3<Label, RadioButton, RadioButton> tuple = addLabelRadioButtonRadioButton(gridPane,
                ++rowIndex,
                toggleGroup,
                Res.get("feeOptionWindow.optionsLabel"),
                "BTC",
                "BSQ");
        RadioButton radioButtonBTC = tuple.second;
        RadioButton radioButtonBSQ = tuple.third;
        toggleGroup.selectToggle(isCurrencyForMakerFeeBtc ? radioButtonBTC : radioButtonBSQ);

        toggleChangeListener = (observable, oldValue, newValue) -> {
            resultHandler.handle(newValue == radioButtonBTC);
        };
        toggleGroup.selectedToggleProperty().addListener(toggleChangeListener);

        makerFeeTextField = addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("createOffer.currencyForFee"), makerFeeWithCodeProperty.get()).second;
        makerFeeTextField.textProperty().bind(makerFeeWithCodeProperty);
    }
}
