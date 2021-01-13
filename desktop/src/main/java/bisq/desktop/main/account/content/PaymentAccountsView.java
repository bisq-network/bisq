package bisq.desktop.main.account.content;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InfoAutoTooltipLabel;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.ImageUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;

import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import javafx.collections.ObservableList;

import javafx.util.Callback;

import java.util.concurrent.TimeUnit;

public abstract class PaymentAccountsView<R extends Node, M extends ActivatableWithDataModel> extends ActivatableViewAndModel<R, M> {

    protected ListView<PaymentAccount> paymentAccountsListView;
    private ChangeListener<PaymentAccount> paymentAccountChangeListener;
    protected Button addAccountButton, exportButton, importButton;
    protected AccountAgeWitnessService accountAgeWitnessService;
    private EventHandler<KeyEvent> keyEventEventHandler;

    public PaymentAccountsView(M model, AccountAgeWitnessService accountAgeWitnessService) {
        super(model);
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    @Override
    public void initialize() {
        keyEventEventHandler = event -> {
            if (Utilities.isCtrlShiftPressed(KeyCode.L, event)) {
                accountAgeWitnessService.getAccountAgeWitnessUtils().logSignedWitnesses();
            } else if (Utilities.isCtrlShiftPressed(KeyCode.S, event)) {
                accountAgeWitnessService.getAccountAgeWitnessUtils().logSigners();
            } else if (Utilities.isCtrlShiftPressed(KeyCode.U, event)) {
                accountAgeWitnessService.getAccountAgeWitnessUtils().logUnsignedSignerPubKeys();
            } else if (Utilities.isCtrlShiftPressed(KeyCode.C, event)) {
                copyAccount();
            }
        };

        buildForm();
        paymentAccountChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                onSelectAccount(newValue);
        };
        Label placeholder = new AutoTooltipLabel(Res.get("shared.noAccountsSetupYet"));
        placeholder.setWrapText(true);
        paymentAccountsListView.setPlaceholder(placeholder);
    }

    @Override
    protected void activate() {
        paymentAccountsListView.setItems(getPaymentAccounts());
        paymentAccountsListView.getSelectionModel().selectedItemProperty().addListener(paymentAccountChangeListener);
        addAccountButton.setOnAction(event -> addNewAccount());
        exportButton.setOnAction(event -> exportAccounts());
        importButton.setOnAction(event -> importAccounts());
        if (root.getScene() != null)
            root.getScene().addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    @Override
    protected void deactivate() {
        paymentAccountsListView.getSelectionModel().selectedItemProperty().removeListener(paymentAccountChangeListener);
        addAccountButton.setOnAction(null);
        exportButton.setOnAction(null);
        importButton.setOnAction(null);
        if (root.getScene() != null)
            root.getScene().removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    protected void onDeleteAccount(PaymentAccount paymentAccount) {
        new Popup().warning(Res.get("shared.askConfirmDeleteAccount"))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    boolean isPaymentAccountUsed = deleteAccountFromModel(paymentAccount);
                    if (!isPaymentAccountUsed)
                        removeSelectAccountForm();
                    else
                        UserThread.runAfter(() -> new Popup().warning(
                                Res.get("shared.cannotDeleteAccount"))
                                .show(), 100, TimeUnit.MILLISECONDS);
                })
                .closeButtonText(Res.get("shared.cancel"))
                .show();
    }

    protected void setPaymentAccountsCellFactory() {
        paymentAccountsListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<PaymentAccount> call(ListView<PaymentAccount> list) {
                return new ListCell<>() {
                    final InfoAutoTooltipLabel label = new InfoAutoTooltipLabel("", ContentDisplay.RIGHT);
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new AutoTooltipButton("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final PaymentAccount item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getAccountName());

                            boolean needsSigning = PaymentMethod.hasChargebackRisk(item.getPaymentMethod(),
                                    item.getTradeCurrencies());

                            if (needsSigning) {
                                AccountAgeWitnessService.SignState signState =
                                        accountAgeWitnessService.getSignState(accountAgeWitnessService.getMyWitness(
                                                item.paymentAccountPayload));

                                String info = StringUtils.capitalize(signState.getDisplayString());
                                label.setIcon(GUIUtil.getIconForSignState(signState), info);
                            } else {
                                label.hideIcon();
                            }

                            removeButton.setOnAction(e -> onDeleteAccount(item));
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    protected abstract void removeSelectAccountForm();

    protected abstract boolean deleteAccountFromModel(PaymentAccount paymentAccount);

    protected abstract void importAccounts();

    protected abstract void exportAccounts();

    protected abstract void addNewAccount();

    protected abstract ObservableList<PaymentAccount> getPaymentAccounts();

    protected abstract void buildForm();

    protected abstract void onSelectAccount(PaymentAccount paymentAccount);

    protected void copyAccount() {
    }
}
