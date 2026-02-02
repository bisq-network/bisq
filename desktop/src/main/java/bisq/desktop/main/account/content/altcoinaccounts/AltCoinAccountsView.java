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

package bisq.desktop.main.account.content.altcoinaccounts;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.paymentmethods.AssetsForm;
import bisq.desktop.components.paymentmethods.PaymentMethodForm;
import bisq.desktop.components.paymentmethods.XmrForm;
import bisq.desktop.main.account.content.PaymentAccountsView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.asset.AltCoinAccountDisclaimer;
import bisq.asset.Asset;
import bisq.asset.coins.Monero;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.collections.ObservableList;

import javafx.util.StringConverter;

import java.util.Optional;

import static bisq.desktop.util.FormBuilder.*;
import static bisq.desktop.util.GUIUtil.getComboBoxButtonCell;

@FxmlView
public class AltCoinAccountsView extends PaymentAccountsView<GridPane, AltCoinAccountsViewModel> {

    private final InputValidator inputValidator;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private final AssetService assetService;
    private final FilterManager filterManager;
    private final CoinFormatter formatter;
    private final User user;
    private final Preferences preferences;

    private PaymentMethodForm paymentMethodForm;
    private TitledGroupBg accountTitledGroupBg;
    private Button saveNewAccountButton;
    private int gridRow = 0;
    protected ComboBox<TradeCurrency> currencyComboBox;

    @Inject
    public AltCoinAccountsView(AltCoinAccountsViewModel model,
                               InputValidator inputValidator,
                               AltCoinAddressValidator altCoinAddressValidator,
                               AccountAgeWitnessService accountAgeWitnessService,
                               AssetService assetService,
                               FilterManager filterManager,
                               @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                               User user,
                               Preferences preferences) {
        super(model, accountAgeWitnessService);

        this.inputValidator = inputValidator;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.assetService = assetService;
        this.filterManager = filterManager;
        this.formatter = formatter;
        this.user = user;
        this.preferences = preferences;
    }

    @Override
    protected ObservableList<PaymentAccount> getPaymentAccounts() {
        return model.getPaymentAccounts();
    }

    @Override
    protected void importAccounts() {
        model.dataModel.importAccounts((Stage) root.getScene().getWindow());
    }

    @Override
    protected void exportAccounts() {
        model.dataModel.exportAccounts((Stage) root.getScene().getWindow());
    }

    @Override
    protected void exportAccountsForBisq2() {
        model.dataModel.exportAccountsForBisq2((Stage) root.getScene().getWindow());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSaveNewAccount(PaymentAccount paymentAccount) {
        TradeCurrency selectedTradeCurrency = paymentAccount.getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            if (selectedTradeCurrency instanceof CryptoCurrency && ((CryptoCurrency) selectedTradeCurrency).isAsset()) {
                String name = selectedTradeCurrency.getName();
                new Popup().information(Res.get("account.altcoin.popup.wallet.msg",
                                selectedTradeCurrency.getCodeAndName(),
                                name,
                                name))
                        .closeButtonText(Res.get("account.altcoin.popup.wallet.confirm"))
                        .show();
            }

            final Optional<Asset> asset = CurrencyUtil.findAsset(selectedTradeCurrency.getCode());
            if (asset.isPresent()) {
                final AltCoinAccountDisclaimer disclaimerAnnotation = asset.get().getClass().getAnnotation(AltCoinAccountDisclaimer.class);
                if (disclaimerAnnotation != null) {
                    new Popup()
                            .width(asset.get() instanceof Monero ? 1000 : 669)
                            .maxMessageLength(2500)
                            .information(Res.get(disclaimerAnnotation.value()))
                            .useIUnderstandButton()
                            .show();
                }
            }

            if (model.getPaymentAccounts().stream().noneMatch(e -> e.getAccountName() != null &&
                    e.getAccountName().equals(paymentAccount.getAccountName()))) {
                model.onSaveNewAccount(paymentAccount);
                removeNewAccountForm();
            } else {
                new Popup().warning(Res.get("shared.accountNameAlreadyUsed")).show();
            }
        }
    }

    private void onCancelNewAccount() {
        removeNewAccountForm();
    }

    private void onUpdateAccount(PaymentAccount paymentAccount) {
        model.onUpdateAccount(paymentAccount);
        removeSelectAccountForm();
    }

    private void onCancelSelectedAccount(PaymentAccount paymentAccount) {
        paymentAccount.revertChanges();
        removeSelectAccountForm();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Base form
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void buildForm() {
        addTitledGroupBg(root, gridRow, 2, Res.get("shared.manageAccounts"));

        Tuple3<Label, ListView<PaymentAccount>, VBox> tuple = addTopLabelListView(root, gridRow, Res.get("account.altcoin.yourAltcoinAccounts"), Layout.FIRST_ROW_DISTANCE);
        paymentAccountsListView = tuple.second;
        int prefNumRows = Math.min(4, Math.max(2, model.dataModel.getNumPaymentAccounts()));
        paymentAccountsListView.setMinHeight(prefNumRows * Layout.LIST_ROW_HEIGHT + 28);
        setPaymentAccountsCellFactory();

        Tuple4<Button, Button, Button, Button> tuple4 = add4ButtonsAfterGroup(root, ++gridRow, Res.get("shared.addNewAccount"),
                Res.get("shared.ExportAccounts"), Res.get("shared.exportAccountsForBisq2"), Res.get("shared.importAccounts"));
        addAccountButton = tuple4.first;
        exportButton = tuple4.second;
        exportForBisq2Button = tuple4.third;
        importButton = tuple4.fourth;
    }

    // Add new account form
    protected void addNewAccount() {
        paymentAccountsListView.getSelectionModel().clearSelection();
        TradeCurrency selectedCurrency = currencyComboBox == null ? null : currencyComboBox.getValue();
        removeAccountRows();
        addAccountButton.setDisable(true);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.createNewAccount"), Layout.GROUP_DISTANCE);

        if (paymentMethodForm != null) {
            FormBuilder.removeRowsFromGridPane(root, 3, paymentMethodForm.getGridRow() + 1);
            GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
        }
        gridRow = 2;
        addTradeCurrencyComboBox(root, selectedCurrency);
        paymentMethodForm = getPaymentMethodForm(PaymentMethod.BLOCK_CHAINS, selectedCurrency);
        paymentMethodForm.addFormForAddAccount();
        gridRow = paymentMethodForm.getGridRow();
        Tuple2<Button, Button> tuple2 = add2ButtonsAfterGroup(root, ++gridRow, Res.get("shared.saveNewAccount"), Res.get("shared.cancel"));
        saveNewAccountButton = tuple2.first;
        saveNewAccountButton.setOnAction(event -> onSaveNewAccount(paymentMethodForm.getPaymentAccount()));
        saveNewAccountButton.disableProperty().bind(paymentMethodForm.allInputsValidProperty().not());
        Button cancelButton = tuple2.second;
        cancelButton.setOnAction(event -> onCancelNewAccount());
        GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
    }

    // Select account form
    protected void onSelectAccount(PaymentAccount previous, PaymentAccount current) {
        if (previous != null) {
            previous.revertChanges();
        }
        removeAccountRows();
        addAccountButton.setDisable(false);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("shared.selectedAccount"), Layout.GROUP_DISTANCE);
        paymentMethodForm = getPaymentMethodForm(current);
        paymentMethodForm.addFormForEditAccount();
        gridRow = paymentMethodForm.getGridRow();
        Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(
                root,
                ++gridRow,
                Res.get("shared.save"),
                Res.get("shared.deleteAccount"),
                Res.get("shared.cancel")
        );

        Button saveAccountButton = tuple.first;
        saveAccountButton.setOnAction(event -> onUpdateAccount(current));
        Button deleteAccountButton = tuple.second;
        deleteAccountButton.setOnAction(event -> onDeleteAccount(current));
        Button cancelButton = tuple.third;
        cancelButton.setOnAction(event -> onCancelSelectedAccount(current));
        GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan());
        model.onSelectAccount(current);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod) {
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
        paymentAccount.init();
        return getPaymentMethodForm(paymentAccount);
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod, TradeCurrency currencyCode) {
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
        paymentAccount.init();
        paymentAccount.setSingleTradeCurrency(currencyCode);
        paymentAccount.setSelectedTradeCurrency(currencyCode);
        return getPaymentMethodForm(paymentAccount);
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentAccount paymentAccount) {
        if (paymentAccount.getSelectedTradeCurrency() != null &&
                paymentAccount.getSelectedTradeCurrency().getCode() != null &&
                paymentAccount.getSelectedTradeCurrency().getCode().equalsIgnoreCase("XMR")) {
            return new XmrForm(paymentAccount, accountAgeWitnessService, altCoinAddressValidator,
                    inputValidator, root, gridRow, formatter, assetService, user);
        }
        return new AssetsForm(paymentAccount, accountAgeWitnessService, altCoinAddressValidator,
                inputValidator, root, gridRow, formatter, assetService);
    }

    private void removeNewAccountForm() {
        saveNewAccountButton.disableProperty().unbind();
        removeAccountRows();
        addAccountButton.setDisable(false);
    }

    @Override
    protected void removeSelectAccountForm() {
        FormBuilder.removeRowsFromGridPane(root, 2, gridRow);
        gridRow = 1;
        addAccountButton.setDisable(false);
        paymentAccountsListView.getSelectionModel().clearSelection();
    }

    @Override
    protected boolean deleteAccountFromModel(PaymentAccount paymentAccount) {
        return model.onDeleteAccount(paymentAccount);
    }

    private void removeAccountRows() {
        FormBuilder.removeRowsFromGridPane(root, 2, gridRow);
        gridRow = 1;
    }

    protected void addTradeCurrencyComboBox(GridPane gridPane, TradeCurrency selectedCurrency) {
        currencyComboBox = FormBuilder.<TradeCurrency>addLabelAutocompleteComboBox(gridPane, ++gridRow, Res.get("payment.altcoin"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currencyComboBox.setPromptText(Res.get("payment.select.altcoin"));
        currencyComboBox.setButtonCell(getComboBoxButtonCell(Res.get("payment.select.altcoin"), currencyComboBox));

        currencyComboBox.getEditor().focusedProperty().addListener(observable ->
                currencyComboBox.setPromptText(""));

        ((AutocompleteComboBox<TradeCurrency>) currencyComboBox).setAutocompleteItems(
                CurrencyUtil.getActiveSortedCryptoCurrencies(assetService, filterManager));
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 10));

        currencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency != null ? tradeCurrency.getNameAndCode() : "";
            }

            @Override
            public TradeCurrency fromString(String s) {
                return currencyComboBox.getItems().stream().
                        filter(item -> item.getNameAndCode().equals(s)).
                        findAny().orElse(null);
            }
        });

        if (selectedCurrency != null) {
            currencyComboBox.setValue(selectedCurrency);
        }
        ((AutocompleteComboBox<?>) currencyComboBox).setOnChangeConfirmed(e -> {
            if (currencyComboBox.getValue() != null) {
                addNewAccount();
            }
        });
    }


}
