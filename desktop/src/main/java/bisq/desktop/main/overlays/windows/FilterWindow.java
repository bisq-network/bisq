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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.filter.PaymentAccountFilter;
import bisq.core.locale.Res;

import bisq.common.app.DevEnv;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelCheckBox;
import static bisq.desktop.util.FormBuilder.addTopLabelInputTextField;

public class FilterWindow extends Overlay<FilterWindow> {
    private SendFilterMessageHandler sendFilterMessageHandler;
    private RemoveFilterMessageHandler removeFilterMessageHandler;
    private final FilterManager filterManager;
    private final boolean useDevPrivilegeKeys;


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

    public FilterWindow(FilterManager filterManager, boolean useDevPrivilegeKeys) {
        this.filterManager = filterManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("filterWindow.headline");

        width = 968;
        createGridPane();
        addHeadLine();
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
        gridPane.getColumnConstraints().remove(1);
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);

        InputTextField keyInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("shared.unlock"), 10);
        if (useDevPrivilegeKeys)
            keyInputTextField.setText(DevEnv.DEV_PRIVILEGE_PRIV_KEY);

        InputTextField offerIdsInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.offers"));
        InputTextField nodesInputTextField = addTopLabelInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.onions")).second;
        nodesInputTextField.setPromptText("E.g. zqnzx6o3nifef5df.onion:9999"); // Do not translate
        InputTextField paymentAccountFilterInputTextField = addTopLabelInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.accounts")).second;
        GridPane.setHalignment(paymentAccountFilterInputTextField, HPos.RIGHT);
        paymentAccountFilterInputTextField.setPromptText("E.g. PERFECT_MONEY|getAccountNr|12345"); // Do not translate
        InputTextField bannedCurrenciesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.bannedCurrencies"));
        InputTextField bannedPaymentMethodsInputTextField = addTopLabelInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.bannedPaymentMethods")).second;
        bannedPaymentMethodsInputTextField.setPromptText("E.g. PERFECT_MONEY"); // Do not translate
        InputTextField arbitratorsInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.arbitrators"));
        InputTextField seedNodesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.seedNode"));
        InputTextField priceRelayNodesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.priceRelayNode"));
        InputTextField btcNodesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.btcNode"));
        CheckBox preventPublicBtcNetworkCheckBox = addLabelCheckBox(gridPane, ++rowIndex, Res.get("filterWindow.preventPublicBtcNetwork"));
        CheckBox disableDaoCheckBox = addLabelCheckBox(gridPane, ++rowIndex, Res.get("filterWindow.disableDao"));
        InputTextField disableDaoBelowVersionInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.disableDaoBelowVersion"));
        InputTextField disableTradeBelowVersionInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.disableTradeBelowVersion"));

        final Filter filter = filterManager.getDevelopersFilter();
        if (filter != null) {
            offerIdsInputTextField.setText(filter.getBannedOfferIds().stream().collect(Collectors.joining(", ")));
            nodesInputTextField.setText(filter.getBannedNodeAddress().stream().collect(Collectors.joining(", ")));
            if (filter.getBannedPaymentAccounts() != null) {
                StringBuilder sb = new StringBuilder();
                filter.getBannedPaymentAccounts().stream().forEach(e -> {
                    if (e != null && e.getPaymentMethodId() != null) {
                        sb.append(e.getPaymentMethodId())
                                .append("|")
                                .append(e.getGetMethodName())
                                .append("|")
                                .append(e.getValue())
                                .append(", ");
                    }
                });
                paymentAccountFilterInputTextField.setText(sb.toString());
            }

            if (filter.getBannedCurrencies() != null)
                bannedCurrenciesInputTextField.setText(filter.getBannedCurrencies().stream().collect(Collectors.joining(", ")));

            if (filter.getBannedPaymentMethods() != null)
                bannedPaymentMethodsInputTextField.setText(filter.getBannedPaymentMethods().stream().collect(Collectors.joining(", ")));

            if (filter.getArbitrators() != null)
                arbitratorsInputTextField.setText(filter.getArbitrators().stream().collect(Collectors.joining(", ")));

            if (filter.getSeedNodes() != null)
                seedNodesInputTextField.setText(filter.getSeedNodes().stream().collect(Collectors.joining(", ")));

            if (filter.getPriceRelayNodes() != null)
                priceRelayNodesInputTextField.setText(filter.getPriceRelayNodes().stream().collect(Collectors.joining(", ")));

            if (filter.getBtcNodes() != null)
                btcNodesInputTextField.setText(filter.getBtcNodes().stream().collect(Collectors.joining(", ")));

            preventPublicBtcNetworkCheckBox.setSelected(filter.isPreventPublicBtcNetwork());

            disableDaoCheckBox.setSelected(filter.isDisableDao());
            disableDaoBelowVersionInputTextField.setText(filter.getDisableDaoBelowVersion());
            disableTradeBelowVersionInputTextField.setText(filter.getDisableTradeBelowVersion());
        }
        Button sendButton = new AutoTooltipButton(Res.get("filterWindow.add"));
        sendButton.setOnAction(e -> {
            List<String> offerIds = new ArrayList<>();
            List<String> nodes = new ArrayList<>();
            List<PaymentAccountFilter> paymentAccountFilters = new ArrayList<>();
            List<String> bannedCurrencies = new ArrayList<>();
            List<String> bannedPaymentMethods = new ArrayList<>();
            List<String> arbitrators = new ArrayList<>();
            List<String> seedNodes = new ArrayList<>();
            List<String> priceRelayNodes = new ArrayList<>();
            List<String> btcNodes = new ArrayList<>();

            if (!offerIdsInputTextField.getText().isEmpty()) {
                offerIds = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(offerIdsInputTextField.getText())
                        .split(",")));
            }

            if (!nodesInputTextField.getText().isEmpty()) {
                nodes = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(nodesInputTextField.getText()).split(",")));
            }

            if (!paymentAccountFilterInputTextField.getText().isEmpty()) {
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
            }

            if (!bannedCurrenciesInputTextField.getText().isEmpty()) {
                bannedCurrencies = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(bannedCurrenciesInputTextField.getText())
                        .split(",")));
            }

            if (!bannedPaymentMethodsInputTextField.getText().isEmpty()) {
                bannedPaymentMethods = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(bannedPaymentMethodsInputTextField.getText())
                        .split(",")));
            }

            if (!arbitratorsInputTextField.getText().isEmpty()) {
                arbitrators = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(arbitratorsInputTextField.getText()).split(",")));
            }

            if (!seedNodesInputTextField.getText().isEmpty()) {
                seedNodes = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(seedNodesInputTextField.getText()).split(",")));
            }

            if (!priceRelayNodesInputTextField.getText().isEmpty()) {
                priceRelayNodes = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(priceRelayNodesInputTextField.getText()).split(",")));
            }

            if (!btcNodesInputTextField.getText().isEmpty()) {
                btcNodes = new ArrayList<>(Arrays.asList(StringUtils.deleteWhitespace(btcNodesInputTextField.getText())
                        .split(",")));
            }

            if (sendFilterMessageHandler.handle(new Filter(offerIds,
                            nodes,
                            paymentAccountFilters,
                            bannedCurrencies,
                            bannedPaymentMethods,
                            arbitrators,
                            seedNodes,
                            priceRelayNodes,
                            preventPublicBtcNetworkCheckBox.isSelected(),
                            btcNodes,
                            disableDaoCheckBox.isSelected(),
                            disableDaoBelowVersionInputTextField.getText(),
                            disableTradeBelowVersionInputTextField.getText()),
                    keyInputTextField.getText()))
                hide();
            else
                new Popup<>().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
        });

        Button removeFilterMessageButton = new AutoTooltipButton(Res.get("filterWindow.remove"));
        removeFilterMessageButton.setOnAction(e -> {
            if (keyInputTextField.getText().length() > 0) {
                if (removeFilterMessageHandler.handle(keyInputTextField.getText()))
                    hide();
                else
                    new Popup<>().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
            }
        });

        closeButton = new AutoTooltipButton(Res.get("shared.close"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        hBox.getChildren().addAll(sendButton, removeFilterMessageButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }
}
