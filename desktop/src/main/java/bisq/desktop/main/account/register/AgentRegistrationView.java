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

package bisq.desktop.main.account.register;


import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.UnlockDisputeAgentRegistrationWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.ImageUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.LanguageUtil;
import bisq.core.locale.Res;
import bisq.core.support.dispute.agent.DisputeAgent;

import bisq.common.UserThread;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.VPos;

import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;

import javafx.util.Callback;
import javafx.util.StringConverter;

import static bisq.desktop.util.FormBuilder.add2ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public abstract class AgentRegistrationView<R extends DisputeAgent, T extends AgentRegistrationViewModel<R, ?>>
        extends ActivatableViewAndModel<VBox, T> {

    private final boolean useDevPrivilegeKeys;
    private ListView<String> languagesListView;
    private ComboBox<String> languageComboBox;

    private int gridRow = 0;

    private ChangeListener<R> changeListener;
    private UnlockDisputeAgentRegistrationWindow unlockDisputeAgentRegistrationWindow;
    private ListChangeListener<String> listChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AgentRegistrationView(T model, boolean useDevPrivilegeKeys) {
        super(model);
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        buildUI();

        languageComboBox.setItems(model.allLanguageCodes);

        changeListener = (observable, oldValue, newValue) -> updateLanguageList();
    }

    @Override
    protected void activate() {
    }

    @Override
    protected void deactivate() {
        model.myDisputeAgentProperty.removeListener(changeListener);
        languagesListView.getItems().removeListener(listChangeListener);
    }

    public void onTabSelection(boolean isSelectedTab) {
        if (isSelectedTab) {
            model.myDisputeAgentProperty.addListener(changeListener);
            updateLanguageList();

            if (model.registrationPubKeyAsHex.get() == null && unlockDisputeAgentRegistrationWindow == null) {
                unlockDisputeAgentRegistrationWindow = new UnlockDisputeAgentRegistrationWindow(useDevPrivilegeKeys);
                unlockDisputeAgentRegistrationWindow.onClose(() -> unlockDisputeAgentRegistrationWindow = null)
                        .onKey(model::setPrivKeyAndCheckPubKey)
                        .width(700)
                        .show();
            }
        } else {
            model.myDisputeAgentProperty.removeListener(changeListener);
        }
    }

    private void updateLanguageList() {
        languagesListView.setItems(model.languageCodes);
        languagesListView.setPrefHeight(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2);
        listChangeListener = c -> languagesListView.setPrefHeight(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2);
        languagesListView.getItems().addListener(listChangeListener);
    }

    private void buildUI() {
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(30, 25, -1, 25));
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(200);
        columnConstraints1.setMaxWidth(500);
        gridPane.getColumnConstraints().addAll(columnConstraints1);
        root.getChildren().add(gridPane);

        addTitledGroupBg(gridPane, gridRow, 4, Res.get("account.arbitratorRegistration.registration", getRole()));
        TextField pubKeyTextField = addTopLabelTextField(gridPane, gridRow, Res.get("account.arbitratorRegistration.pubKey"),
                model.registrationPubKeyAsHex.get(), Layout.FIRST_ROW_DISTANCE).second;

        pubKeyTextField.textProperty().bind(model.registrationPubKeyAsHex);

        Tuple3<Label, ListView<String>, VBox> tuple = FormBuilder.addTopLabelListView(gridPane, ++gridRow, Res.get("shared.yourLanguage"));
        GridPane.setValignment(tuple.first, VPos.TOP);
        languagesListView = tuple.second;
        languagesListView.disableProperty().bind(model.registrationEditDisabled);
        languagesListView.setMinHeight(3 * Layout.LIST_ROW_HEIGHT + 2);
        languagesListView.setMaxHeight(6 * Layout.LIST_ROW_HEIGHT + 2);
        languagesListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<String> call(ListView<String> list) {
                return new ListCell<>() {
                    final Label label = new AutoTooltipLabel();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new AutoTooltipButton("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(LanguageUtil.getDisplayName(item));
                            removeButton.setOnAction(e -> onRemoveLanguage(item));
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        languageComboBox = FormBuilder.addComboBox(gridPane, ++gridRow);
        languageComboBox.disableProperty().bind(model.registrationEditDisabled);
        languageComboBox.setPromptText(Res.get("shared.addLanguage"));
        languageComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return LanguageUtil.getDisplayName(code);
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });
        languageComboBox.setOnAction(e -> onAddLanguage());

        Tuple2<Button, Button> buttonButtonTuple2 = add2ButtonsAfterGroup(gridPane, ++gridRow,
                Res.get("account.arbitratorRegistration.register"), Res.get("account.arbitratorRegistration.revoke"));
        Button registerButton = buttonButtonTuple2.first;
        registerButton.disableProperty().bind(model.registrationEditDisabled);
        registerButton.setOnAction(e -> onRegister());

        Button revokeButton = buttonButtonTuple2.second;
        revokeButton.setDefaultButton(false);
        revokeButton.disableProperty().bind(model.revokeButtonDisabled);
        revokeButton.setOnAction(e -> onRevoke());

        final TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("shared.information"), Layout.GROUP_DISTANCE);

        titledGroupBg.getStyleClass().add("last");

        Label infoLabel = addMultilineLabel(gridPane, gridRow);
        GridPane.setMargin(infoLabel, new Insets(Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        infoLabel.setText(Res.get("account.arbitratorRegistration.info.msg", getRole().toLowerCase()));
    }

    protected abstract String getRole();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddLanguage() {
        model.onAddLanguage(languageComboBox.getSelectionModel().getSelectedItem());
        UserThread.execute(() -> languageComboBox.getSelectionModel().clearSelection());
    }

    private void onRemoveLanguage(String locale) {
        model.onRemoveLanguage(locale);

        if (languagesListView.getItems().size() == 0) {
            new Popup().warning(Res.get("account.arbitratorRegistration.warn.min1Language")).show();
            model.onAddLanguage(LanguageUtil.getDefaultLanguageLocaleAsCode());
        }
    }

    private void onRevoke() {
        if (model.isBootstrappedOrShowPopup()) {
            model.onRevoke(
                    () -> new Popup().feedback(Res.get("account.arbitratorRegistration.removedSuccess")).show(),
                    (errorMessage) -> new Popup().error(Res.get("account.arbitratorRegistration.removedFailed",
                            Res.get("shared.errorMessageInline", errorMessage))).show());
        }
    }

    private void onRegister() {
        if (model.isBootstrappedOrShowPopup()) {
            model.onRegister(
                    () -> new Popup().feedback(Res.get("account.arbitratorRegistration.registerSuccess")).show(),
                    (errorMessage) -> new Popup().error(Res.get("account.arbitratorRegistration.registerFailed",
                            Res.get("shared.errorMessageInline", errorMessage))).show());
        }
    }
}
