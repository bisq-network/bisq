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

package io.bisq.gui.main.dao.compensation;

import bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.request.compensation.CompensationRequestPayload;
import io.bisq.core.dao.request.compensation.consensus.Restrictions;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.gui.components.*;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.BsqAddressValidator;
import io.bisq.gui.util.validation.BsqValidator;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;

import javax.annotation.Nullable;
import java.util.UUID;

import static io.bisq.gui.util.FormBuilder.*;

public class CompensationRequestDisplay {
    private final GridPane gridPane;
    private BsqFormatter bsqFormatter;
    private BsqWalletService bsqWalletService;
    public InputTextField uidTextField, nameTextField, titleTextField, linkInputTextField,
            requestedBsqTextField, bsqAddressTextField;
    private int gridRow = 0;
    public TextArea descriptionTextArea;
    private HyperlinkWithIcon linkHyperlinkWithIcon;
    public TxIdTextField txIdTextField;
    private FeeService feeService;

    public CompensationRequestDisplay(GridPane gridPane, BsqFormatter bsqFormatter, BsqWalletService bsqWalletService, @Nullable FeeService feeService) {
        this.gridPane = gridPane;
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.feeService = feeService;
    }

    public void createAllFields(String title, double top) {
        addTitledGroupBg(gridPane, gridRow, 8, title, top);
        uidTextField = addLabelInputTextField(gridPane, gridRow, Res.getWithCol("shared.id"), top == Layout.GROUP_DISTANCE ? Layout.FIRST_ROW_AND_GROUP_DISTANCE : Layout.FIRST_ROW_DISTANCE).second;
        uidTextField.setEditable(false);
        nameTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.name")).second;
        titleTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.title")).second;
        descriptionTextArea = addLabelTextArea(gridPane, ++gridRow, Res.get("dao.compensation.display.description"), Res.get("dao.compensation.display.description.prompt")).second;
        linkInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.link")).second;
        linkHyperlinkWithIcon = addLabelHyperlinkWithIcon(gridPane, gridRow, Res.get("dao.compensation.display.link"), "", "").second;
        linkHyperlinkWithIcon.setVisible(false);
        linkHyperlinkWithIcon.setManaged(false);
        linkInputTextField.setPromptText(Res.get("dao.compensation.display.link.prompt"));
        requestedBsqTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.requestedBsq")).second;

        if (feeService != null) {
            BsqValidator bsqValidator = new BsqValidator(bsqFormatter);
            //TODO should we use the BSQ or a BTC validator? Technically it is BTC at that stage...
            //bsqValidator.setMinValue(feeService.getCreateCompensationRequestFee());
            bsqValidator.setMinValue(Restrictions.getMinCompensationRequestAmount());
            requestedBsqTextField.setValidator(bsqValidator);
        }

        // TODO validator, addressTF
        bsqAddressTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.get("dao.compensation.display.bsqAddress")).second;
        bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());
        bsqAddressTextField.setValidator(new BsqAddressValidator(bsqFormatter));

        txIdTextField = addLabelTxIdTextField(gridPane, ++gridRow,
                Res.get("dao.compensation.display.txId"), "").second;
    }

    public void fillWithData(CompensationRequestPayload data) {
        uidTextField.setText(data.getUid());
        nameTextField.setText(data.getName());
        titleTextField.setText(data.getTitle());
        descriptionTextArea.setText(data.getDescription());
        linkInputTextField.setVisible(false);
        linkInputTextField.setManaged(false);
        linkHyperlinkWithIcon.setVisible(true);
        linkHyperlinkWithIcon.setManaged(true);
        linkHyperlinkWithIcon.setText(data.getLink());
        linkHyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(data.getLink()));
        requestedBsqTextField.setText(bsqFormatter.formatCoinWithCode(data.getRequestedBsq()));
        bsqAddressTextField.setText(data.getBsqAddress());
        txIdTextField.setup(data.getTxId());
    }

    public void clearForm() {
        uidTextField.clear();
        nameTextField.clear();
        titleTextField.clear();
        descriptionTextArea.clear();
        linkInputTextField.clear();
        linkHyperlinkWithIcon.clear();
        requestedBsqTextField.clear();
        bsqAddressTextField.clear();
        txIdTextField.cleanup();
    }

    public void fillWithMock() {
        uidTextField.setText(UUID.randomUUID().toString());
        nameTextField.setText("Manfred Karrer");
        titleTextField.setText("Development work November 2017");
        descriptionTextArea.setText("Development work");
        linkInputTextField.setText("https://github.com/bisq-network/compensation/issues/12");
        requestedBsqTextField.setText("14000");
        bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());
    }

    public void setAllFieldsEditable(boolean isEditable) {
        nameTextField.setEditable(isEditable);
        titleTextField.setEditable(isEditable);
        descriptionTextArea.setEditable(isEditable);
        linkInputTextField.setEditable(isEditable);
        requestedBsqTextField.setEditable(isEditable);
        bsqAddressTextField.setEditable(isEditable);

        linkInputTextField.setVisible(true);
        linkInputTextField.setManaged(true);
        linkHyperlinkWithIcon.setVisible(false);
        linkHyperlinkWithIcon.setManaged(false);
        linkHyperlinkWithIcon.setOnAction(null);
    }

    public void removeAllFields() {
        gridPane.getChildren().clear();
        gridRow = 0;
    }

    public int incrementAndGetGridRow() {
        return ++gridRow;
    }

    public GridPane createCompensationList(TableView<CompensationRequestListItem> tableView, String headerString) {
        GridPane compensationList = new GridPane();

        TableGroupHeadline header = new TableGroupHeadline(headerString);
        GridPane.setMargin(header, new Insets(10, 0, 0, 0));
        GridPane.setRowIndex(header, 0);
        compensationList.getChildren().add(header);
        header.setMinHeight(20);
        header.setMaxHeight(20);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setMinHeight(90);
        GridPane.setRowIndex(tableView, 1);
        GridPane.setColumnSpan(tableView, 1);
        GridPane.setMargin(tableView, new Insets(5, 10, -10, 10));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        compensationList.getChildren().add(tableView);

        createColumns(tableView);

        return compensationList;
    }

    public ScrollPane createCompensationRequestDisplay() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setMinHeight(100);

        AnchorPane bottomAnchorPane = new AnchorPane();
        scrollPane.setContent(bottomAnchorPane);

        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(140);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        columnConstraints2.setMinWidth(300);

        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        AnchorPane.setBottomAnchor(gridPane, 20d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, -20d);
        bottomAnchorPane.getChildren().add(gridPane);

        return scrollPane;
    }

    public SplitPane createCompensationRequestPane(TableView<CompensationRequestListItem> tableView, String headerString) {
        SplitPane compensationRequestPane = new SplitPane();
        compensationRequestPane.setOrientation(Orientation.VERTICAL);
        compensationRequestPane.setDividerPositions(0.2, 0.7);
        compensationRequestPane.setStyle("-fx-padding: 0; -fx-box-border: transparent;");

        compensationRequestPane.getItems().add(createCompensationList(tableView, headerString));
        compensationRequestPane.getItems().add(createCompensationRequestDisplay());
        return compensationRequestPane;
    }

    private void createColumns(TableView<CompensationRequestListItem> tableView) {
        TableColumn<CompensationRequestListItem, CompensationRequestListItem> dateColumn = new AutoTooltipTableColumn<CompensationRequestListItem, CompensationRequestListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>, TableCell<CompensationRequestListItem,
                        CompensationRequestListItem>>() {
                    @Override
                    public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(
                            TableColumn<CompensationRequestListItem, CompensationRequestListItem> column) {
                        return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {
                            @Override
                            public void updateItem(final CompensationRequestListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getCompensationRequest().getPayload().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator((o1, o2) -> o1.getCompensationRequest().getPayload().getCreationDate().compareTo(o2.getCompensationRequest().getPayload().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<CompensationRequestListItem, CompensationRequestListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>, TableCell<CompensationRequestListItem,
                        CompensationRequestListItem>>() {
                    @Override
                    public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(
                            TableColumn<CompensationRequestListItem, CompensationRequestListItem> column) {
                        return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {
                            @Override
                            public void updateItem(final CompensationRequestListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCompensationRequest().getPayload().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator((o1, o2) -> o1.getCompensationRequest().getPayload().getName().compareTo(o2.getCompensationRequest().getPayload().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<CompensationRequestListItem, CompensationRequestListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));
        uidColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>, TableCell<CompensationRequestListItem,
                        CompensationRequestListItem>>() {
                    @Override
                    public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(
                            TableColumn<CompensationRequestListItem, CompensationRequestListItem> column) {
                        return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {
                            @Override
                            public void updateItem(final CompensationRequestListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCompensationRequest().getPayload().getUid());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        uidColumn.setComparator((o1, o2) -> o1.getCompensationRequest().getPayload().getUid().compareTo(o2.getCompensationRequest().getPayload().getUid()));
        tableView.getColumns().add(uidColumn);

        TableColumn<CompensationRequestListItem, CompensationRequestListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>,
                TableCell<CompensationRequestListItem, CompensationRequestListItem>>() {

            @Override
            public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(TableColumn<CompensationRequestListItem,
                    CompensationRequestListItem> column) {
                return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {

                    @Override
                    public void updateItem(final CompensationRequestListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setGraphic(item.getTxConfidenceIndicator());
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        confidenceColumn.setComparator((o1, o2) -> o1.getConfirmations().compareTo(o2.getConfirmations()));
        tableView.getColumns().add(confidenceColumn);
    }
}
