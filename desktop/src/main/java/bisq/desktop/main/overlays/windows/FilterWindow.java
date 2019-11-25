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

import bisq.core.app.AppOptionKeys;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.filter.PaymentAccountFilter;
import bisq.core.locale.Res;

import bisq.common.app.DevEnv;

import com.google.inject.Inject;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelCheckBox;
import static bisq.desktop.util.FormBuilder.addTopLabelInputTextField;

public class FilterWindow extends Overlay<FilterWindow> {
    private final FilterManager filterManager;
    private final boolean useDevPrivilegeKeys;

    @Inject
    public FilterWindow(FilterManager filterManager,
                        @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
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
        InputTextField mediatorsInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.mediators"));
        InputTextField refundAgentsInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.refundAgents"));
        InputTextField seedNodesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.seedNode"));
        InputTextField priceRelayNodesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.priceRelayNode"));
        InputTextField btcNodesInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.btcNode"));
        CheckBox preventPublicBtcNetworkCheckBox = addLabelCheckBox(gridPane, ++rowIndex, Res.get("filterWindow.preventPublicBtcNetwork"));
        CheckBox disableDaoCheckBox = addLabelCheckBox(gridPane, ++rowIndex, Res.get("filterWindow.disableDao"));
        InputTextField disableDaoBelowVersionInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.disableDaoBelowVersion"));
        InputTextField disableTradeBelowVersionInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("filterWindow.disableTradeBelowVersion"));

        final Filter filter = filterManager.getDevelopersFilter();
        if (filter != null) {
            setupFieldFromList(offerIdsInputTextField, filter.getBannedOfferIds());
            setupFieldFromList(nodesInputTextField, filter.getBannedNodeAddress());
            setupFieldFromPaymentAccountFiltersList(paymentAccountFilterInputTextField, filter.getBannedPaymentAccounts());
            setupFieldFromList(bannedCurrenciesInputTextField, filter.getBannedCurrencies());
            setupFieldFromList(bannedPaymentMethodsInputTextField, filter.getBannedPaymentMethods());
            setupFieldFromList(arbitratorsInputTextField, filter.getArbitrators());
            setupFieldFromList(mediatorsInputTextField, filter.getMediators());
            setupFieldFromList(refundAgentsInputTextField, filter.getRefundAgents());
            setupFieldFromList(seedNodesInputTextField, filter.getSeedNodes());
            setupFieldFromList(priceRelayNodesInputTextField, filter.getPriceRelayNodes());
            setupFieldFromList(btcNodesInputTextField, filter.getBtcNodes());
            preventPublicBtcNetworkCheckBox.setSelected(filter.isPreventPublicBtcNetwork());
            disableDaoCheckBox.setSelected(filter.isDisableDao());
            disableDaoBelowVersionInputTextField.setText(filter.getDisableDaoBelowVersion());
            disableTradeBelowVersionInputTextField.setText(filter.getDisableTradeBelowVersion());
        }
        Button sendButton = new AutoTooltipButton(Res.get("filterWindow.add"));
        sendButton.setOnAction(e -> {
            if (filterManager.addFilterMessageIfKeyIsValid(
                    new Filter(
                            readAsList(offerIdsInputTextField),
                            readAsList(nodesInputTextField),
                            readAsPaymentAccountFiltersList(paymentAccountFilterInputTextField),
                            readAsList(bannedCurrenciesInputTextField),
                            readAsList(bannedPaymentMethodsInputTextField),
                            readAsList(arbitratorsInputTextField),
                            readAsList(seedNodesInputTextField),
                            readAsList(priceRelayNodesInputTextField),
                            preventPublicBtcNetworkCheckBox.isSelected(),
                            readAsList(btcNodesInputTextField),
                            disableDaoCheckBox.isSelected(),
                            disableDaoBelowVersionInputTextField.getText(),
                            disableTradeBelowVersionInputTextField.getText(),
                            readAsList(mediatorsInputTextField),
                            readAsList(refundAgentsInputTextField)
                    ),
                    keyInputTextField.getText())
            )
                hide();
            else
                new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
        });

        Button removeFilterMessageButton = new AutoTooltipButton(Res.get("filterWindow.remove"));
        removeFilterMessageButton.setOnAction(e -> {
            if (keyInputTextField.getText().length() > 0) {
                if (filterManager.removeFilterMessageIfKeyIsValid(keyInputTextField.getText()))
                    hide();
                else
                    new Popup().warning(Res.get("shared.invalidKey")).width(300).onClose(this::blurAgain).show();
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

    private void setupFieldFromList(InputTextField field, List<String> values) {
        if (values != null)
            field.setText(values.stream().collect(Collectors.joining(", ")));
    }

    private void setupFieldFromPaymentAccountFiltersList(InputTextField field, List<PaymentAccountFilter> values) {
        if (values != null) {
            StringBuilder sb = new StringBuilder();
            values.stream().forEach(e -> {
                if (e != null && e.getPaymentMethodId() != null) {
                    sb
                            .append(e.getPaymentMethodId())
                            .append("|")
                            .append(e.getGetMethodName())
                            .append("|")
                            .append(e.getValue())
                            .append(", ");
                }
            });
            field.setText(sb.toString());
        }
    }

    private List<String> readAsList(InputTextField field) {
        if (field.getText().isEmpty()) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(StringUtils.deleteWhitespace(field.getText()).split(","));
        }
    }

    private List<PaymentAccountFilter> readAsPaymentAccountFiltersList(InputTextField field) {
        return readAsList(field)
                .stream().map(item -> {
                    String[] list = item.split("\\|");
                    if (list.length == 3)
                        return new PaymentAccountFilter(list[0], list[1], list[2]);
                    else
                        return new PaymentAccountFilter("", "", "");
                })
                .collect(Collectors.toList());
    }
}
