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

package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.filter.Filter;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.filter.PaymentAccountFilter;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.locale.Res;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;

public class FilterWindow extends Overlay<FilterWindow> {
    private static final Logger log = LoggerFactory.getLogger(FilterWindow.class);

    private Button sendButton;
    private SendFilterMessageHandler sendFilterMessageHandler;
    private RemoveFilterMessageHandler removeFilterMessageHandler;
    private FilterManager filterManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////
    public interface SendFilterMessageHandler {
        boolean handle(Filter filter, String privKey);
    }

    public interface RemoveFilterMessageHandler {
        boolean handle(String privKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FilterWindow(FilterManager filterManager) {
        this.filterManager = filterManager;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("filterWindow.headline");

        width = 900;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
    }

    public FilterWindow onAddFilter(SendFilterMessageHandler sendFilterMessageHandler) {
        this.sendFilterMessageHandler = sendFilterMessageHandler;
        return this;
    }

    public FilterWindow onRemoveFilter(RemoveFilterMessageHandler removeFilterMessageHandler) {
        this.removeFilterMessageHandler = removeFilterMessageHandler;
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
    private void addContent() {
        InputTextField keyInputTextField = addLabelInputTextField(gridPane, ++rowIndex, Res.get("shared.unlock"), 10).second;
        InputTextField offerIdsInputTextField = addLabelInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.offers")).second;
        InputTextField nodesInputTextField = addLabelInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.onions")).second;
        InputTextField paymentAccountFilterInputTextField = addLabelInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.accounts")).second;
        GridPane.setHalignment(paymentAccountFilterInputTextField, HPos.RIGHT);

        final Filter filter = filterManager.getDevelopersFilter();
        if (filter != null) {
            offerIdsInputTextField.setText(filter.bannedOfferIds.stream().collect(Collectors.joining(", ")));
            nodesInputTextField.setText(filter.bannedNodeAddress.stream().collect(Collectors.joining(", ")));
            if (filter.bannedPaymentAccounts != null) {
                StringBuilder sb = new StringBuilder();
                filter.bannedPaymentAccounts.stream().forEach(e -> {
                    if (e != null && e.paymentMethodId != null) {
                        sb.append(e.paymentMethodId)
                                .append("|")
                                .append(e.getMethodName)
                                .append("|")
                                .append(e.value)
                                .append(", ");
                    }
                });
                paymentAccountFilterInputTextField.setText(sb.toString());
            }
        }
        sendButton = new Button(Res.get("filterWindow.add"));
        sendButton.setOnAction(e -> {
            ArrayList<String> offerIds = new ArrayList<>();
            ArrayList<String> nodes = new ArrayList<>();
            ArrayList<PaymentAccountFilter> paymentAccountFilters = new ArrayList<>();

            if (!offerIdsInputTextField.getText().isEmpty())
                offerIds = new ArrayList<>(Arrays.asList(offerIdsInputTextField.getText().replace(" ", "")
                        .replace(", ", ",")
                        .split(",")));
            if (!nodesInputTextField.getText().isEmpty())
                nodes = new ArrayList<>(Arrays.asList(nodesInputTextField.getText().replace(":9999", "")
                        .replace(".onion", "")
                        .replace(" ", "")
                        .replace(", ", ",")
                        .split(",")));
            if (!paymentAccountFilterInputTextField.getText().isEmpty())
                paymentAccountFilters = new ArrayList<>(Arrays.asList(paymentAccountFilterInputTextField.getText()
                        .replace(", ", ",")
                        .split(","))
                        .stream().map(item -> {
                            String[] list = item.split("\\|");
                            if (list.length == 3)
                                return new PaymentAccountFilter(list[0], list[1], list[2]);
                            else
                                return new PaymentAccountFilter("", "", "");
                        })
                        .collect(Collectors.toList()));

            if (sendFilterMessageHandler.handle(new Filter(offerIds, nodes, paymentAccountFilters), keyInputTextField.getText()))
                hide();
            else
                new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
        });

        Button removeFilterMessageButton = new Button(Res.get("filterWindow.remove"));
        removeFilterMessageButton.setOnAction(e -> {
            if (keyInputTextField.getText().length() > 0) {
                if (removeFilterMessageHandler.handle(keyInputTextField.getText()))
                    hide();
                else
                    new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
            }
        });

        closeButton = new Button(Res.get("shared.close"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(sendButton, removeFilterMessageButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }
}
