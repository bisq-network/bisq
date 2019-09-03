package bisq.desktop.main.account.content;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.ImageUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;

import bisq.common.UserThread;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.ObservableList;

import javafx.util.Callback;

import java.util.concurrent.TimeUnit;

public abstract class PaymentAccountsView<R extends Node, M extends ActivatableWithDataModel> extends ActivatableViewAndModel<R, M> {

    protected ListView<PaymentAccount> paymentAccountsListView;
    private ChangeListener<PaymentAccount> paymentAccountChangeListener;
    protected Button addAccountButton, exportButton, importButton;
    SignedWitnessService signedWitnessService;
    protected AccountAgeWitnessService accountAgeWitnessService;

    public PaymentAccountsView(M model, AccountAgeWitnessService accountAgeWitnessService) {
        super(model);
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    @Override
    public void initialize() {
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
    }

    @Override
    protected void deactivate() {
        paymentAccountsListView.getSelectionModel().selectedItemProperty().removeListener(paymentAccountChangeListener);
        addAccountButton.setOnAction(null);
        exportButton.setOnAction(null);
        importButton.setOnAction(null);
    }

    protected void onDeleteAccount(PaymentAccount paymentAccount) {
        new Popup<>().warning(Res.get("shared.askConfirmDeleteAccount"))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    boolean isPaymentAccountUsed = deleteAccountFromModel(paymentAccount);
                    if (!isPaymentAccountUsed)
                        removeSelectAccountForm();
                    else
                        UserThread.runAfter(() -> new Popup<>().warning(
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
                    final Label label = new AutoTooltipLabel();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new AutoTooltipButton("", icon);
                    final ImageView signed = ImageUtil.getImageViewById("image-update-failed");
                    final AnchorPane pane = new AnchorPane(label, signed, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                        AnchorPane.setRightAnchor(signed, removeButton.getWidth());
                    }

                    @Override
                    public void updateItem(final PaymentAccount item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getAccountName());
                            removeButton.setOnAction(e -> onDeleteAccount(item));
                            String signedWitnessId = accountAgeWitnessService.hasSignedWitness(
                                    item.paymentAccountPayload) ? "image-tick" : "rejected";
                            signed.setId(signedWitnessId);
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
}
