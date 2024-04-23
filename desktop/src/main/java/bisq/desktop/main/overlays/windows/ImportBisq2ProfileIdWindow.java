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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;
import bisq.core.util.validation.RegexValidator;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static javafx.beans.binding.Bindings.createBooleanBinding;

public class ImportBisq2ProfileIdWindow extends Overlay<ImportBisq2ProfileIdWindow> {

    private String profileId;
    private InputTextField profileIdTextField;
    @Getter
    private RegexValidator regexValidator;

    public ImportBisq2ProfileIdWindow() {
        type = Type.Attention;
    }

    public void show() {
        width = 868;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();

        regexValidator = new RegexValidator();
        regexValidator.setPattern("[a-fA-F0-9]{40}");
        profileIdTextField.setValidator(regexValidator);
        actionButton.disableProperty().bind(
                createBooleanBinding(() -> !profileIdTextField.getValidator().validate(profileIdTextField.getText()).isValid,
                profileIdTextField.textProperty()));

        applyStyles();
        display();
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        gridPane.getColumnConstraints().addAll(columnConstraints1);
    }

    @Nullable
    public String getProfileId() {
        return profileIdTextField != null ? profileIdTextField.getText() : null;
    }
    public ImportBisq2ProfileIdWindow setProfileId(String x) { this.profileId = x; return this; }

    private void addContent() {
        profileIdTextField = addInputTextField(gridPane, ++rowIndex, Res.get("account.fiat.bisq2profileId"), 10);
        profileIdTextField.setText(profileId);
    }
}
