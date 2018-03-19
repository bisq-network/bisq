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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqAddressValidator;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.proposal.ProposalPayload;
import bisq.core.dao.proposal.ProposalType;
import bisq.core.dao.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.proposal.compensation.consensus.Restrictions;
import bisq.core.dao.proposal.consensus.ProposalRestrictions;
import bisq.core.locale.Res;
import bisq.core.provider.fee.FeeService;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;

import javafx.beans.value.ChangeListener;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;

// TODO add listener for descriptionTextArea and restrict size of 100 chars. show popup if exceeds.
// we store data locally so we want to keep it small. external link is intended for more info....
// use ProposalRestrictions.getMaxLengthDescriptionText()
public class ProposalDisplay {
    private final GridPane gridPane;
    private final int maxLengthDescriptionText;
    private BsqFormatter bsqFormatter;
    private BsqWalletService bsqWalletService;
    public InputTextField uidTextField, nameTextField, titleTextField, linkInputTextField;
    @Nullable
    public InputTextField requestedBsqTextField, bsqAddressTextField;
    private int gridRow;
    public TextArea descriptionTextArea;
    private HyperlinkWithIcon linkHyperlinkWithIcon;
    @Nullable
    private TxIdTextField txIdTextField;
    private FeeService feeService;
    private ChangeListener<String> descriptionTextAreaListener;

    public ProposalDisplay(GridPane gridPane, BsqFormatter bsqFormatter, BsqWalletService bsqWalletService, @Nullable FeeService feeService) {
        this.gridPane = gridPane;
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.feeService = feeService;

        maxLengthDescriptionText = ProposalRestrictions.getMaxLengthDescriptionText();

        descriptionTextAreaListener = (observable, oldValue, newValue) -> {
            if (!ProposalRestrictions.isDescriptionSizeValid(newValue)) {
                new Popup<>().warning(Res.get("dao.proposal.display.description.tooLong", maxLengthDescriptionText)).show();
                descriptionTextArea.setText(newValue.substring(0, maxLengthDescriptionText));
            }
        };
    }

    public void createAllFields(String title, int index, double top, ProposalType proposalType, boolean isMakeProposalScreen) {
        this.gridRow = index;
        int rowSpan = 5;
        if (proposalType == ProposalType.COMPENSATION_REQUEST)
            rowSpan += 2;
        if (!isMakeProposalScreen)
            rowSpan += 1;

        addTitledGroupBg(gridPane, gridRow, rowSpan, title, top);
        uidTextField = addLabelInputTextField(gridPane, gridRow, Res.getWithCol("shared.id"), top == Layout.GROUP_DISTANCE ? Layout.FIRST_ROW_AND_GROUP_DISTANCE : Layout.FIRST_ROW_DISTANCE).second;
        uidTextField.setEditable(false);
        nameTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.name")).second;
        titleTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.title")).second;

        descriptionTextArea = addLabelTextArea(gridPane, ++gridRow, Res.get("dao.proposal.display.description"), Res.get("dao.proposal.display.description.prompt", maxLengthDescriptionText)).second;
        descriptionTextArea.setPrefColumnCount(2);
        descriptionTextArea.textProperty().addListener(descriptionTextAreaListener);
        linkInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.link")).second;
        linkHyperlinkWithIcon = addLabelHyperlinkWithIcon(gridPane, gridRow, Res.get("dao.proposal.display.link"), "", "").second;
        linkHyperlinkWithIcon.setVisible(false);
        linkHyperlinkWithIcon.setManaged(false);
        linkInputTextField.setPromptText(Res.get("dao.proposal.display.link.prompt"));

        if (proposalType == ProposalType.COMPENSATION_REQUEST) {
            requestedBsqTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.requestedBsq")).second;

            if (feeService != null) {
                BsqValidator bsqValidator = new BsqValidator(bsqFormatter);
                //TODO should we use the BSQ or a BTC validator? Technically it is BTC at that stage...
                //bsqValidator.setMinValue(feeService.getCreateCompensationRequestFee());
                bsqValidator.setMinValue(Restrictions.getMinCompensationRequestAmount());
                Objects.requireNonNull(requestedBsqTextField).setValidator(bsqValidator);
            }
            // TODO validator, addressTF
            bsqAddressTextField = addLabelInputTextField(gridPane, ++gridRow,
                    Res.get("dao.proposal.display.bsqAddress")).second;
            Objects.requireNonNull(bsqAddressTextField).setText("B" + bsqWalletService.getUnusedAddress().toBase58());
            bsqAddressTextField.setValidator(new BsqAddressValidator(bsqFormatter));
        }

        if (!isMakeProposalScreen)
            txIdTextField = addLabelTxIdTextField(gridPane, ++gridRow,
                    Res.get("dao.proposal.display.txId"), "").second;
    }

    public void fillWithData(ProposalPayload proposalPayload) {
        uidTextField.setText(proposalPayload.getUid());
        nameTextField.setText(proposalPayload.getName());
        titleTextField.setText(proposalPayload.getTitle());
        descriptionTextArea.setText(proposalPayload.getDescription());
        linkInputTextField.setVisible(false);
        linkInputTextField.setManaged(false);
        linkHyperlinkWithIcon.setVisible(true);
        linkHyperlinkWithIcon.setManaged(true);
        linkHyperlinkWithIcon.setText(proposalPayload.getLink());
        linkHyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(proposalPayload.getLink()));
        if (proposalPayload instanceof CompensationRequestPayload) {
            CompensationRequestPayload compensationRequestPayload = (CompensationRequestPayload) proposalPayload;
            Objects.requireNonNull(requestedBsqTextField).setText(bsqFormatter.formatCoinWithCode(compensationRequestPayload.getRequestedBsq()));
            Objects.requireNonNull(bsqAddressTextField).setText(compensationRequestPayload.getBsqAddress());
        }
        if (txIdTextField != null)
            txIdTextField.setup(proposalPayload.getTxId());
    }

    public void clearForm() {
        uidTextField.clear();
        nameTextField.clear();
        titleTextField.clear();
        descriptionTextArea.clear();
        linkInputTextField.clear();
        linkHyperlinkWithIcon.clear();
        if (requestedBsqTextField != null)
            requestedBsqTextField.clear();
        if (bsqAddressTextField != null)
            bsqAddressTextField.clear();
        if (txIdTextField != null)
            txIdTextField.cleanup();

        descriptionTextArea.textProperty().removeListener(descriptionTextAreaListener);
    }

    public void fillWithMock() {
        uidTextField.setText(UUID.randomUUID().toString());
        nameTextField.setText("Manfred Karrer");
        titleTextField.setText("Development work November 2017");
        descriptionTextArea.setText("Development work");
        linkInputTextField.setText("https://github.com/bisq-network/compensation/issues/12");
        if (requestedBsqTextField != null)
            requestedBsqTextField.setText("14000");
        if (bsqAddressTextField != null)
            bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());
    }

    public void setAllFieldsEditable(boolean isEditable) {
        nameTextField.setEditable(isEditable);
        titleTextField.setEditable(isEditable);
        descriptionTextArea.setEditable(isEditable);
        linkInputTextField.setEditable(isEditable);
        if (requestedBsqTextField != null)
            requestedBsqTextField.setEditable(isEditable);
        if (bsqAddressTextField != null)
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


    public ScrollPane getView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setMinHeight(100);

        AnchorPane anchorPane = new AnchorPane();
        scrollPane.setContent(anchorPane);

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
        AnchorPane.setTopAnchor(gridPane, 20d);
        anchorPane.getChildren().add(gridPane);

        return scrollPane;
    }
}
