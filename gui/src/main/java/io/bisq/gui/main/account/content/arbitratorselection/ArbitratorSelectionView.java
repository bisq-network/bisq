/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.account.content.arbitratorselection;

import io.bisq.common.UserThread;
import io.bisq.common.locale.LanguageUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.TableGroupHeadline;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.account.content.ContentSettings;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.ImageUtil;
import io.bisq.gui.util.Layout;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import javafx.util.StringConverter;

import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class ArbitratorSelectionView extends ActivatableViewAndModel<GridPane, ArbitratorSelectionViewModel> {
    @FXML
    GridPane root;

    private final ArbitratorSelectionViewModel model;

    private ListView<String> languagesListView;
    private ComboBox<String> languageComboBox;
    private TableView<ArbitratorListItem> tableView;
    private int gridRow = 0;
    private CheckBox autoSelectAllMatchingCheckBox;
    private ListChangeListener<String> listChangeListener;
    private ListChangeListener<String> languageCodesListChangeListener;
    private ChangeListener<Boolean> isSelectedChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ArbitratorSelectionView(ArbitratorSelectionViewModel model) {
        super(model);
        this.model = model;
    }

    @Override
    public void initialize() {
        ContentSettings.setDefaultSettings(root, 140);

        addLanguageGroup();
        addArbitratorsGroup();
        listChangeListener = c -> languagesListView.setPrefHeight(MainView.scale(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2));
    }

    @Override
    protected void activate() {
        languagesListView.getItems().addListener(listChangeListener);
        languageComboBox.setItems(model.allLanguageCodes);
        languagesListView.setItems(model.languageCodes);
        languagesListView.setPrefHeight(MainView.scale(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2));

        tableView.setItems(model.arbitratorListItems);
        // TODO should scale with stage resize
        tableView.setPrefHeight(MainView.scale(200));
        
        autoSelectAllMatchingCheckBox.setSelected(model.getAutoSelectArbitrators());
    }

    @Override
    protected void deactivate() {
        languagesListView.getItems().removeListener(listChangeListener);
        if (languageCodesListChangeListener != null)
            model.languageCodes.removeListener(languageCodesListChangeListener);
    }


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
            new Popup<>().warning(Res.get("account.arbitratorSelection.minOneArbitratorRequired")).show();
            model.onAddLanguage(LanguageUtil.getDefaultLanguageLocaleAsCode());
        }
    }

    private void onAddArbitrator(ArbitratorListItem arbitratorListItem) {
        model.onAddArbitrator(arbitratorListItem.arbitrator);
    }

    private void onRemoveArbitrator(ArbitratorListItem arbitratorListItem) {
        model.onRemoveArbitrator(arbitratorListItem.arbitrator);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI builder
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addLanguageGroup() {
        addTitledGroupBg(root, gridRow, 1, Res.get("account.arbitratorSelection.whichLanguages"));

        Tuple2<Label, ListView> tuple = addLabelListView(root, gridRow, Res.get("shared.yourLanguage"), MainView.scale(Layout.FIRST_ROW_DISTANCE));
        GridPane.setValignment(tuple.first, VPos.TOP);
        //noinspection unchecked
        languagesListView = tuple.second;
        languagesListView.setMinHeight(MainView.scale(3 * Layout.LIST_ROW_HEIGHT + 2));
        languagesListView.setMaxHeight(MainView.scale(6 * Layout.LIST_ROW_HEIGHT + 2));
        languagesListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> list) {
                return new ListCell<String>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, MainView.scale(0));
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

        //noinspection unchecked
        languageComboBox = addLabelComboBox(root, ++gridRow, "", MainView.scale(15)).second;
        languageComboBox.setPromptText(Res.get("shared.addLanguage"));
        languageComboBox.setConverter(new StringConverter<String>() {
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
    }

    private void addArbitratorsGroup() {
        TableGroupHeadline tableGroupHeadline = new TableGroupHeadline(Res.get("account.arbitratorSelection.whichDoYouAccept"));
        GridPane.setRowIndex(tableGroupHeadline, ++gridRow);
        GridPane.setColumnSpan(tableGroupHeadline, 2);
        GridPane.setMargin(tableGroupHeadline, new Insets(MainView.scale(40), MainView.scale(-10), MainView.scale(-10), MainView.scale(-10)));
        root.getChildren().add(tableGroupHeadline);

        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(MainView.scale(60), MainView.scale(-10), MainView.scale(5), MainView.scale(-10)));
        root.getChildren().add(tableView);

        autoSelectAllMatchingCheckBox = addCheckBox(root, ++gridRow, Res.get("account.arbitratorSelection.autoSelect"));
        GridPane.setColumnSpan(autoSelectAllMatchingCheckBox, 2);
        GridPane.setHalignment(autoSelectAllMatchingCheckBox, HPos.LEFT);
        GridPane.setColumnIndex(autoSelectAllMatchingCheckBox, 0);
        GridPane.setMargin(autoSelectAllMatchingCheckBox, new Insets(MainView.scale(0), MainView.scale(-10), MainView.scale(0), MainView.scale(-10)));
        autoSelectAllMatchingCheckBox.setOnAction(event ->
                model.setAutoSelectArbitrators(autoSelectAllMatchingCheckBox.isSelected()));

        TableColumn<ArbitratorListItem, String> dateColumn = new TableColumn<>(Res.get("account.arbitratorSelection.regDate"));
        dateColumn.setSortable(false);
        dateColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getRegistrationDate()));
        dateColumn.setMinWidth(MainView.scale(140));
        dateColumn.setMaxWidth(MainView.scale(140));


        TableColumn<ArbitratorListItem, String> nameColumn = new TableColumn<>(Res.get("shared.onionAddress"));
        nameColumn.setSortable(false);
        nameColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getAddressString()));
        nameColumn.setMinWidth(MainView.scale(90));

        TableColumn<ArbitratorListItem, String> languagesColumn = new TableColumn<>(Res.get("account.arbitratorSelection.languages"));
        languagesColumn.setSortable(false);
        languagesColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getLanguageCodes()));
        languagesColumn.setMinWidth(MainView.scale(130));

        TableColumn<ArbitratorListItem, ArbitratorListItem> selectionColumn = new TableColumn<ArbitratorListItem, ArbitratorListItem>(
                Res.get("shared.accept")) {
            {
                setMinWidth(MainView.scale(60));
                setMaxWidth(MainView.scale(60));
                setSortable(false);
            }
        };
        selectionColumn.setCellValueFactory((arbitrator) -> new ReadOnlyObjectWrapper<>(arbitrator.getValue()));
        selectionColumn.setCellFactory(
                new Callback<TableColumn<ArbitratorListItem, ArbitratorListItem>, TableCell<ArbitratorListItem, ArbitratorListItem>>() {
                    @Override
                    public TableCell<ArbitratorListItem, ArbitratorListItem> call(TableColumn<ArbitratorListItem, ArbitratorListItem> column) {
                        return new TableCell<ArbitratorListItem, ArbitratorListItem>() {
                            private final CheckBox checkBox = new CheckBox();
                            private TableRow tableRow;
                            private BooleanProperty selectedProperty;

                            private void updateDisableState(final ArbitratorListItem item) {
                                boolean selected = model.isAcceptedArbitrator(item.arbitrator);
                                item.setIsSelected(selected);

                                boolean hasMatchingLanguage = model.hasMatchingLanguage(item.arbitrator);
                                if (!hasMatchingLanguage) {
                                    model.onRemoveArbitrator(item.arbitrator);
                                    if (selected)
                                        item.setIsSelected(false);
                                }

                                boolean isMyOwnRegisteredArbitrator = model.isMyOwnRegisteredArbitrator(item.arbitrator);
                                checkBox.setDisable(!hasMatchingLanguage || isMyOwnRegisteredArbitrator);

                                tableRow = getTableRow();
                                if (tableRow != null) {
                                    tableRow.setOpacity(hasMatchingLanguage && !isMyOwnRegisteredArbitrator ? 1 : 0.4);

                                    if (isMyOwnRegisteredArbitrator) {
                                        String text = Res.get("account.arbitratorSelection.cannotSelectHimself");
                                        tableRow.setTooltip(new Tooltip(text));
                                        tableRow.setOnMouseClicked(e -> new Popup<>().warning(
                                                text).show());
                                    } else if (!hasMatchingLanguage) {
                                        tableRow.setTooltip(new Tooltip(Res.get("account.arbitratorSelection.noMatchingLang")));
                                        tableRow.setOnMouseClicked(e -> new Popup<>()
                                                .warning(Res.get("account.arbitratorSelection.noLang")).show());
                                    } else {
                                        tableRow.setOnMouseClicked(null);
                                        tableRow.setTooltip(null);
                                    }
                                }
                            }

                            @Override
                            public void updateItem(final ArbitratorListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    selectedProperty = item.isSelectedProperty();
                                    languageCodesListChangeListener = c -> updateDisableState(item);
                                    model.languageCodes.addListener(languageCodesListChangeListener);

                                    isSelectedChangeListener = (observable, oldValue, newValue) -> checkBox.setSelected(newValue);
                                    selectedProperty.addListener(isSelectedChangeListener);

                                    checkBox.setSelected(model.isAcceptedArbitrator(item.arbitrator));
                                    checkBox.setOnAction(e -> {
                                                if (checkBox.isSelected()) {
                                                    onAddArbitrator(item);
                                                } else if (model.isDeselectAllowed(item)) {
                                                    onRemoveArbitrator(item);
                                                } else {
                                                    new Popup<>().warning(Res.get("account.arbitratorSelection.minOne")).show();
                                                    checkBox.setSelected(true);
                                                }
                                                item.setIsSelected(checkBox.isSelected());
                                            }
                                    );

                                    updateDisableState(item);
                                    setGraphic(checkBox);
                                } else {
                                    model.languageCodes.removeListener(languageCodesListChangeListener);
                                    if (selectedProperty != null)
                                        selectedProperty.removeListener(isSelectedChangeListener);

                                    setGraphic(null);

                                    if (checkBox != null)
                                        checkBox.setOnAction(null);
                                    if (tableRow != null)
                                        tableRow.setOnMouseClicked(null);
                                }
                            }
                        };
                    }
                });

        //noinspection unchecked
        tableView.getColumns().addAll(dateColumn, nameColumn, languagesColumn, selectionColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }
}

