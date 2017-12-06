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

import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.BsqAddressValidator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

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

    public CompensationRequestDisplay(GridPane gridPane, BsqFormatter bsqFormatter, BsqWalletService bsqWalletService) {
        this.gridPane = gridPane;
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
    }

    public void createAllFields(String title, double top) {
        addTitledGroupBg(gridPane, gridRow, 7, title, top);
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

        // TODO validator, addressTF
        bsqAddressTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.get("dao.compensation.display.bsqAddress")).second;
        bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());
        bsqAddressTextField.setValidator(new BsqAddressValidator(bsqFormatter));
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
}
