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

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple3;
import io.bisq.gui.Navigation;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.dao.DaoView;
import io.bisq.gui.main.dao.wallet.BsqWalletView;
import io.bisq.gui.main.dao.wallet.receive.BsqReceiveView;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.popups.Popup;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.bisq.gui.util.FormBuilder.*;

public class FeeOptionWindow extends Overlay<FeeOptionWindow> {
    private TextField makerFeeTextField;
    private ChangeListener<Toggle> toggleChangeListener;
    private Consumer<Boolean> selectionChangedHandler;
    private final StringProperty makerFeeWithCodeProperty;
    private final boolean isCurrencyForMakerFeeBtc;
    private final boolean isBsqForFeeAvailable;
    @Nullable
    private final String missingBsq;
    private final Navigation navigation;
    private final Runnable closeHandler;
    private ToggleGroup toggleGroup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FeeOptionWindow(StringProperty makerFeeWithCodeProperty, boolean isCurrencyForMakerFeeBtc,
                           boolean isBsqForFeeAvailable, @Nullable String missingBsq, Navigation navigation,
                           Runnable closeHandler) {
        this.makerFeeWithCodeProperty = makerFeeWithCodeProperty;
        this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
        this.isBsqForFeeAvailable = isBsqForFeeAvailable;
        this.missingBsq = missingBsq;
        this.navigation = navigation;
        this.closeHandler = closeHandler;
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

    public FeeOptionWindow onSelectionChangedHandler(Consumer<Boolean> selectionChangedHandler) {
        this.selectionChangedHandler = selectionChangedHandler;
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    @Override
    protected void cleanup() {
        super.cleanup();

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

        makerFeeTextField = addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("createOffer.currencyForFee"), makerFeeWithCodeProperty.get()).second;

        toggleChangeListener = (observable, oldValue, newValue) -> {
            final boolean isBtc = newValue == radioButtonBTC;
            selectionChangedHandler.accept(isBtc);

            if (!isBsqForFeeAvailable && !isBtc) {
                if (missingBsq != null) {
                    // We don't call hide() because we want to keep the blurred bg
                    if (stage != null)
                        stage.hide();
                    else
                        log.warn("Stage is null");

                    cleanup();
                    onHidden();

                    //noinspection unchecked
                    new Popup().warning(missingBsq)
                            .actionButtonTextWithGoTo("navigation.dao.wallet.receive")
                            .onAction(() -> {
                                UserThread.runAfter(() -> {
                                    hide();
                                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqReceiveView.class);
                                }, 100, TimeUnit.MILLISECONDS);
                            })
                            .closeButtonText(Res.get("feeOptionWindow.useBTC"))
                            .onClose(() -> {
                                selectionChangedHandler.accept(true);
                                closeHandler.run();
                            })
                            .show();
                }

                UserThread.execute(() -> {
                    toggleGroup.selectToggle(radioButtonBTC);
                    radioButtonBSQ.setDisable(true);
                });
            }
        };
        toggleGroup.selectedToggleProperty().addListener(toggleChangeListener);
        toggleGroup.selectToggle(!isBsqForFeeAvailable || isCurrencyForMakerFeeBtc ? radioButtonBTC : radioButtonBSQ);

        makerFeeTextField.textProperty().bind(makerFeeWithCodeProperty);
    }
}
