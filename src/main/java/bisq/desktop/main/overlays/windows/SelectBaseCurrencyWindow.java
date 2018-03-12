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

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;

import bisq.common.app.DevEnv;
import bisq.common.locale.Res;
import bisq.common.util.Tuple2;

import javax.inject.Inject;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addLabelComboBox;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;

public class SelectBaseCurrencyWindow extends Overlay<SelectBaseCurrencyWindow> {

    private ComboBox<BaseCurrencyNetwork> comboBox;
    private Optional<Consumer<BaseCurrencyNetwork>> selectHandlerOptional;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SelectBaseCurrencyWindow() {
        type = Type.Confirmation;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("selectBaseCurrencyWindow.headline");

        width = 700;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
        applyStyles();
        display();
    }

    public SelectBaseCurrencyWindow onSelect(Consumer<BaseCurrencyNetwork> selectHandler) {
        this.selectHandlerOptional = Optional.of(selectHandler);
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        Label label = addMultilineLabel(gridPane, ++rowIndex, Res.get("selectBaseCurrencyWindow.msg", BisqEnvironment.getBaseCurrencyNetwork().getCurrencyName()), 10);
        GridPane.setMargin(label, new Insets(0, 0, 10, 0));

        Tuple2<Label, ComboBox> tuple = addLabelComboBox(gridPane, ++rowIndex, Res.get("selectBaseCurrencyWindow.select"));
        //noinspection unchecked
        comboBox = tuple.second;
        comboBox.setPromptText(Res.get("shared.select"));
        List<BaseCurrencyNetwork> baseCurrencyNetworks = Arrays.asList(BaseCurrencyNetwork.values());
        // show ony mainnet in production version
        if (!DevEnv.isDevMode())
            baseCurrencyNetworks = baseCurrencyNetworks.stream()
                    .filter(e -> e.isMainnet())
                    .collect(Collectors.toList());
        comboBox.setItems(FXCollections.observableArrayList(baseCurrencyNetworks));

        comboBox.setConverter(new StringConverter<BaseCurrencyNetwork>() {
            @Override
            public String toString(BaseCurrencyNetwork baseCurrencyNetwork) {
                return DevEnv.isDevMode() ? (baseCurrencyNetwork.getCurrencyName() + "_" + baseCurrencyNetwork.getNetwork()) :
                        baseCurrencyNetwork.getCurrencyName();
            }

            @Override
            public BaseCurrencyNetwork fromString(String string) {
                return null;
            }
        });

        comboBox.setOnAction(event -> {
            selectHandlerOptional.get().accept(comboBox.getSelectionModel().getSelectedItem());
            hide();
        });
    }
}
