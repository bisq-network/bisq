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
import bisq.common.config.Config;

import com.google.inject.Inject;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

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
    private ScrollPane scrollPane;

    @Inject
    public FilterWindow(FilterManager filterManager,
                        @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        this.filterManager = filterManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        type = Type.Attention;
    }

    @Override
    protected Region getRootContainer() {
        return scrollPane;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("filterWindow.headline");

        width = 1000;

        createGridPane();

        scrollPane = new ScrollPane();
        scrollPane.setContent(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setMaxHeight(700);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

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

        InputTextField keyTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("shared.unlock"), 10);
        if (useDevPrivilegeKeys) {
            keyTF.setText(DevEnv.DEV_PRIVILEGE_PRIV_KEY);
        }

        InputTextField offerIdsTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.offers"));
        InputTextField nodesTF = addTopLabelInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.onions")).second;
        nodesTF.setPromptText("E.g. zqnzx6o3nifef5df.onion:9999"); // Do not translate
        InputTextField paymentAccountFilterTF = addTopLabelInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.accounts")).second;
        GridPane.setHalignment(paymentAccountFilterTF, HPos.RIGHT);
        paymentAccountFilterTF.setPromptText("E.g. PERFECT_MONEY|getAccountNr|12345"); // Do not translate
        InputTextField bannedCurrenciesTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.bannedCurrencies"));
        InputTextField bannedPaymentMethodsTF = addTopLabelInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.bannedPaymentMethods")).second;
        bannedPaymentMethodsTF.setPromptText("E.g. PERFECT_MONEY"); // Do not translate
        InputTextField bannedAccountWitnessSignerPubKeysTF = addTopLabelInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.bannedAccountWitnessSignerPubKeys")).second;
        bannedAccountWitnessSignerPubKeysTF.setPromptText("E.g. 7f66117aa084e5a2c54fe17d29dd1fee2b241257"); // Do not translate
        InputTextField arbitratorsTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.arbitrators"));
        InputTextField mediatorsTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.mediators"));
        InputTextField refundAgentsTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.refundAgents"));
        InputTextField btcFeeReceiverAddressesTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.btcFeeReceiverAddresses"));
        InputTextField seedNodesTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.seedNode"));
        InputTextField priceRelayNodesTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.priceRelayNode"));
        InputTextField btcNodesTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.btcNode"));
        CheckBox preventPublicBtcNetworkCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("filterWindow.preventPublicBtcNetwork"));
        CheckBox disableDaoCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("filterWindow.disableDao"));
        CheckBox disableAutoConfCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("filterWindow.disableAutoConf"));
        InputTextField disableDaoBelowVersionTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.disableDaoBelowVersion"));
        InputTextField disableTradeBelowVersionTF = addInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.disableTradeBelowVersion"));
        InputTextField bannedPrivilegedDevPubKeysTF = addTopLabelInputTextField(gridPane, ++rowIndex,
                Res.get("filterWindow.bannedPrivilegedDevPubKeys")).second;

        Filter filter = filterManager.getDevFilter();
        if (filter != null) {
            setupFieldFromList(offerIdsTF, filter.getBannedOfferIds());
            setupFieldFromList(nodesTF, filter.getBannedNodeAddress());
            setupFieldFromPaymentAccountFiltersList(paymentAccountFilterTF, filter.getBannedPaymentAccounts());
            setupFieldFromList(bannedCurrenciesTF, filter.getBannedCurrencies());
            setupFieldFromList(bannedPaymentMethodsTF, filter.getBannedPaymentMethods());
            setupFieldFromList(bannedAccountWitnessSignerPubKeysTF, filter.getBannedAccountWitnessSignerPubKeys());
            setupFieldFromList(arbitratorsTF, filter.getArbitrators());
            setupFieldFromList(mediatorsTF, filter.getMediators());
            setupFieldFromList(refundAgentsTF, filter.getRefundAgents());
            setupFieldFromList(btcFeeReceiverAddressesTF, filter.getBtcFeeReceiverAddresses());
            setupFieldFromList(seedNodesTF, filter.getSeedNodes());
            setupFieldFromList(priceRelayNodesTF, filter.getPriceRelayNodes());
            setupFieldFromList(btcNodesTF, filter.getBtcNodes());
            setupFieldFromList(bannedPrivilegedDevPubKeysTF, filter.getBannedPrivilegedDevPubKeys());

            preventPublicBtcNetworkCheckBox.setSelected(filter.isPreventPublicBtcNetwork());
            disableDaoCheckBox.setSelected(filter.isDisableDao());
            disableAutoConfCheckBox.setSelected(filter.isDisableAutoConf());
            disableDaoBelowVersionTF.setText(filter.getDisableDaoBelowVersion());
            disableTradeBelowVersionTF.setText(filter.getDisableTradeBelowVersion());
        }

        Button removeFilterMessageButton = new AutoTooltipButton(Res.get("filterWindow.remove"));
        removeFilterMessageButton.setDisable(filterManager.getDevFilter() == null);

        Button sendButton = new AutoTooltipButton(Res.get("filterWindow.add"));
        sendButton.setOnAction(e -> {
            String privKeyString = keyTF.getText();
            if (filterManager.canAddDevFilter(privKeyString)) {
                String signerPubKeyAsHex = filterManager.getSignerPubKeyAsHex(privKeyString);
                Filter newFilter = new Filter(
                        readAsList(offerIdsTF),
                        readAsList(nodesTF),
                        readAsPaymentAccountFiltersList(paymentAccountFilterTF),
                        readAsList(bannedCurrenciesTF),
                        readAsList(bannedPaymentMethodsTF),
                        readAsList(arbitratorsTF),
                        readAsList(seedNodesTF),
                        readAsList(priceRelayNodesTF),
                        preventPublicBtcNetworkCheckBox.isSelected(),
                        readAsList(btcNodesTF),
                        disableDaoCheckBox.isSelected(),
                        disableDaoBelowVersionTF.getText(),
                        disableTradeBelowVersionTF.getText(),
                        readAsList(mediatorsTF),
                        readAsList(refundAgentsTF),
                        readAsList(bannedAccountWitnessSignerPubKeysTF),
                        readAsList(btcFeeReceiverAddressesTF),
                        filterManager.getOwnerPubKey(),
                        signerPubKeyAsHex,
                        readAsList(bannedPrivilegedDevPubKeysTF),
                        disableAutoConfCheckBox.isSelected()
                );

                // We remove first the old filter
                if (filterManager.canRemoveDevFilter(privKeyString)) {
                    filterManager.removeDevFilter(privKeyString);
                }
                filterManager.addDevFilter(newFilter, privKeyString);
                removeFilterMessageButton.setDisable(filterManager.getDevFilter() == null);
                hide();
            } else {
                new Popup().warning(Res.get("shared.invalidKey")).onClose(this::blurAgain).show();
            }
        });

        removeFilterMessageButton.setOnAction(e -> {
            String privKeyString = keyTF.getText();
            if (filterManager.canRemoveDevFilter(privKeyString)) {
                filterManager.removeDevFilter(privKeyString);
                hide();
            } else {
                new Popup().warning(Res.get("shared.invalidKey")).onClose(this::blurAgain).show();
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
            field.setText(String.join(", ", values));
    }

    private void setupFieldFromPaymentAccountFiltersList(InputTextField field, List<PaymentAccountFilter> values) {
        if (values != null) {
            StringBuilder sb = new StringBuilder();
            values.forEach(e -> {
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
