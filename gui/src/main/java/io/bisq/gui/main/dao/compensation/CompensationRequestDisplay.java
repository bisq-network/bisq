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

package io.bisq.gui.main.dao.compensation;

import io.bisq.common.locale.Res;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.Layout;
import io.bisq.protobuffer.payload.dao.compensation.CompensationRequestPayload;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static io.bisq.gui.util.FormBuilder.addLabelInputTextField;
import static io.bisq.gui.util.FormBuilder.addTitledGroupBg;

public class CompensationRequestDisplay {
    private static final Logger log = LoggerFactory.getLogger(CompensationRequestDisplay.class);

    private final GridPane gridPane;
    public InputTextField uidTextField, nameTextField, titleTextField, categoryTextField, descriptionTextField, linkTextField,
            startDateTextField, endDateTextField, requestedBTCTextField, btcAddressTextField;
    private int gridRow = 0;

    public CompensationRequestDisplay(GridPane gridPane) {
        this.gridPane = gridPane;
    }

    public void createAllFields(String title, double top) {
        addTitledGroupBg(gridPane, gridRow, 10, title, top);
        uidTextField = addLabelInputTextField(gridPane, gridRow, Res.getWithCol("shared.id"), top == Layout.GROUP_DISTANCE ? Layout.FIRST_ROW_AND_GROUP_DISTANCE : Layout.FIRST_ROW_DISTANCE).second;
        uidTextField.setEditable(false);
        nameTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.name")).second;
        titleTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.title")).second;
        categoryTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.category")).second;
        descriptionTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.description")).second;
        linkTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.link")).second;
        startDateTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.startDate")).second;
        endDateTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.endDate")).second;
        requestedBTCTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.requestedBTC")).second;
        btcAddressTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.compensation.display.btcAddress")).second;

    }

    public void fillWithData(CompensationRequestPayload data) {
        uidTextField.setText(data.uid);
        nameTextField.setText(data.name);
        titleTextField.setText(data.title);
        categoryTextField.setText(data.category);
        descriptionTextField.setText(data.description);
        linkTextField.setText(data.link);
        startDateTextField.setText(data.getStartDate().toString());
        endDateTextField.setText(data.getEndDate().toString());
        requestedBTCTextField.setText(data.getRequestedBtc().toPlainString());
        btcAddressTextField.setText(data.btcAddress);
    }

    public void clearForm() {
        uidTextField.setText("");
        nameTextField.setText("");
        titleTextField.setText("");
        categoryTextField.setText("");
        descriptionTextField.setText("");
        linkTextField.setText("");
        startDateTextField.setText("");
        endDateTextField.setText("");
        requestedBTCTextField.setText("");
        btcAddressTextField.setText("");
    }

    public void fillWithMock() {
        int random = new Random().nextInt(100);
        uidTextField.setText("Mock UID" + random);
        nameTextField.setText("Mock name" + random);
        titleTextField.setText("Mock Title " + random);
        categoryTextField.setText("Mock Category " + random);
        descriptionTextField.setText("Mock Description " + random);
        linkTextField.setText("Mock Link " + random);
        startDateTextField.setText("Mock Start date " + random);
        endDateTextField.setText("Mock Delivery date " + random);
        requestedBTCTextField.setText("Mock Requested funds " + random);
        btcAddressTextField.setText("Mock Bitcoin address " + random);
    }

    public void setAllFieldsEditable(boolean isEditable) {
        nameTextField.setEditable(isEditable);
        titleTextField.setEditable(isEditable);
        categoryTextField.setEditable(isEditable);
        descriptionTextField.setEditable(isEditable);
        linkTextField.setEditable(isEditable);
        startDateTextField.setEditable(isEditable);
        endDateTextField.setEditable(isEditable);
        requestedBTCTextField.setEditable(isEditable);
        btcAddressTextField.setEditable(isEditable);
    }

    public void removeAllFields() {
        gridPane.getChildren().clear();
        gridRow = 0;
    }

    public int incrementAndGetGridRow() {
        return ++gridRow;
    }
}
