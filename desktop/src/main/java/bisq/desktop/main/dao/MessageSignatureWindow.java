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

package bisq.desktop.main.dao;

import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.SignVerifyService;
import bisq.core.locale.Res;

import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import java.util.Optional;

import static bisq.desktop.util.FormBuilder.addInputTextField;

public class MessageSignatureWindow extends Overlay<MessageSignatureWindow> {
    private final SignVerifyService signVerifyService;
    private final String txId;
    private final String pubKey;

    private TextField sigTextField;
    private VBox sigTextFieldBox;

    public MessageSignatureWindow(SignVerifyService signVerifyService, String txId) {
        this.signVerifyService = signVerifyService;
        this.txId = txId;
        this.pubKey = signVerifyService.getPubKeyAsHex(txId);
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("dao.proofOfBurn.signature.window.title");

        width = 800;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth(100);
        gridPane.getColumnConstraints().add(columnConstraints);
    }

    private void addContent() {
        FormBuilder.addTopLabelTextField(gridPane, rowIndex, Res.get("dao.proofOfBurn.pubKey"), pubKey, 40);

        InputTextField messageInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("dao.proofOfBurn.message"));

        Button signButton = FormBuilder.addButton(gridPane, ++rowIndex, Res.get("dao.proofOfBurn.sign"), 10);
        signButton.setOnAction(e -> {
            signVerifyService.sign(txId, messageInputTextField.getText()).ifPresent(sig -> {
                sigTextFieldBox.setVisible(true);
                sigTextField.setText(sig);
            });
        });
        Tuple3<Label, TextField, VBox> tuple = FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.get("dao.proofOfBurn.sig"));
        sigTextFieldBox = tuple.third;
        sigTextField = tuple.second;
        sigTextFieldBox.setVisible(false);

        actionHandlerOptional = Optional.of(() -> {
            Utilities.copyToClipboard(sigTextField.getText());
        });
        actionButtonText = Res.get("dao.proofOfBurn.copySig");
    }
}
