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

package bisq.desktop.util.validation;

import bisq.core.locale.Res;

import com.jfoenix.validation.base.ValidatorBase;

import javafx.scene.control.TextInputControl;

public final class PasswordValidator extends ValidatorBase {

    private boolean passwordsMatch = true;

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = (TextInputControl) srcControl.get();
        String text = textField.getText();
        hasErrors.set(false);

        if (!passwordsMatch) {
            hasErrors.set(true);
            message.set(Res.get("password.passwordsDoNotMatch"));
        } else if (text.length() < 8) {
            hasErrors.set(true);
            message.set(Res.get("validation.passwordTooShort"));
        } else if (text.length() > 50) {
            hasErrors.set(true);
            message.set(Res.get("validation.passwordTooLong"));
        }
    }

    public void setPasswordsMatch(boolean isMatch) {
        this.passwordsMatch = isMatch;
    }
}
