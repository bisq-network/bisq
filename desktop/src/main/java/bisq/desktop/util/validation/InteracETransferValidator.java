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
import bisq.core.util.validation.InputValidator;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

/*
 * Interac e-Transfer requires a mail address or Canadian (mobile) phone number
 *
 * Mail addresses are covered with class EmailValidator
 *
 * Phone numbers have 11 digits, expected format is +1 NPA xxx-xxxx
 * Plus, spaces and dash might be omitted
 * Canadian area codes (NPA) taken from http://www.cnac.ca/canadian_dial_plan/Current_&_Future_Dialling_Plan.pdf
 * Valid (as of 2017-06-27) NPAs are hardcoded here
 * They are to change in some future (according to the linked document around 2019/2020)
 */
public final class InteracETransferValidator extends InputValidator {

    private static final String[] NPAS = {"204", "226", "236", "249", "250", "289", "306", "343", "365", "403", "416", "418", "431", "437", "438", "450", "506", "514", "519", "548", "579", "581", "587", "604", "613", "639", "647", "705", "709", "778", "780", "782", "807", "819", "825", "867", "873", "902", "905"};
    private final EmailValidator emailValidator;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public InteracETransferValidator(EmailValidator emailValidator, InteracETransferQuestionValidator questionValidator, InteracETransferAnswerValidator answerValidator) {
        this.emailValidator = emailValidator;
        this.questionValidator = questionValidator;
        this.answerValidator = answerValidator;
    }

    public final InputValidator answerValidator;

    public final InputValidator questionValidator;

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (!result.isValid) {
            return result;
        } else {
            ValidationResult emailResult = emailValidator.validate(input);
            if (emailResult.isValid)
                return emailResult;
            else
                return validatePhoneNumber(input);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ValidationResult validatePhoneNumber(String input) {
        // check for correct format and strip +, space and -
        if (input.matches("\\+?1[ -]?\\d{3}[ -]?\\d{3}[ -]?\\d{4}")) {
            input = input.replace("+", "");
            input = StringUtils.deleteWhitespace(input);
            input = input.replace("-", "");

            String inputAreaCode = input.substring(1, 4);
            for (String s : NPAS) {
                // check area code agains list and return if valid
                if (inputAreaCode.compareTo(s) == 0)
                    return new ValidationResult(true);
            }
            return new ValidationResult(false, Res.get("validation.interacETransfer.invalidAreaCode"));
        } else {
            return new ValidationResult(false, Res.get("validation.interacETransfer.invalidPhone"));
        }
    }
}
