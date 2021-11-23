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

package bisq.core.api.model;

import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.payload.PaymentMethod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.Map;

import java.lang.reflect.Type;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.payment.payload.PaymentMethod.getPaymentMethod;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * <p>
 * An instance of this class can write new payment account forms (editable json files),
 * and de-serialize edited json files into {@link PaymentAccount}
 * instances.
 * </p>
 * <p>
 * Example use case: (1) ask for a blank Hal Cash account form, (2) edit it, (3) derive a
 * {@link bisq.core.payment.HalCashAccount} instance from the edited json file.
 * </p>
 * <br>
 * <p>
 * (1) Ask for a hal cash account form:  Pass a {@link PaymentMethod#HAL_CASH_ID}
 * to {@link PaymentAccountForm#getPaymentAccountForm(String)} to
 * get the json Hal Cash payment account form:
 * <pre>
 * {
 *   "_COMMENTS_": [
 *     "Do not manually edit the paymentMethodId field.",
 *     "Edit the salt field only if you are recreating a payment account on a new installation and wish to preserve the account age."
 *   ],
 *   "paymentMethodId": "HAL_CASH",
 *   "accountName": "Your accountname",
 *   "mobileNr": "Your mobilenr"
 *   "salt": ""
 * }
 * </pre>
 * </p>
 * <p>
 * (2) Save the Hal Cash payment account form to disk, and edit it:
 * <pre>
 * {
 *   "_COMMENTS_": [
 *     "Do not manually edit the paymentMethodId field.",
 *     "Edit the salt field only if you are recreating a payment account on a new installation and wish to preserve the account age."
 *   ],
 *   "paymentMethodId": "HAL_CASH",
 *   "accountName": "Hal Cash Acct",
 *   "mobileNr": "798 123 456"
 *   "salt": ""
 * }
 * </pre>
 * </p>
 * (3) De-serialize the edited json account form:  Pass the edited json file to
 * {@link PaymentAccountForm#toPaymentAccount(File)}, or
 * a json string to {@link PaymentAccountForm#toPaymentAccount(String)}
 * and get a {@link bisq.core.payment.HalCashAccount} instance.
 * <pre>
 * PaymentAccount(
 * paymentMethod=PaymentMethod(id=HAL_CASH,
 *                             maxTradePeriod=86400000,
 *                             maxTradeLimit=50000000),
 * id=e33c9d94-1a1a-43fd-aa11-fcaacbb46100,
 * creationDate=Mon Nov 16 12:26:43 BRST 2020,
 * paymentAccountPayload=HalCashAccountPayload(mobileNr=798 123 456),
 * accountName=Hal Cash Acct,
 * tradeCurrencies=[FiatCurrency(currency=EUR)],
 * selectedTradeCurrency=FiatCurrency(currency=EUR)
 * )
 * </pre>
 */
@Singleton
@Slf4j
public class PaymentAccountForm {

    private final GsonBuilder gsonBuilder = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls();

    // A list of PaymentAccount fields to exclude from json forms.
    private final String[] excludedFields = new String[]{
            "log",
            "id",
            "acceptedCountryCodes",
            "countryCode",
            "creationDate",
            "excludeFromJsonDataMap",
            "maxTradePeriod",
            "paymentAccountPayload",
            "paymentMethod",
            "paymentMethodId",          // Will be included, but handled differently.
            "selectedTradeCurrency",    // May be included, but handled differently.
            "tradeCurrencies",          // May be included, but handled differently.
            "HOLDER_NAME",
            "SALT"                      // Will be included, but handled differently.
    };

    /**
     * Returns a blank payment account form (json) for the given paymentMethodId.
     *
     * @param paymentMethodId Determines what kind of json form to return.
     * @return A uniquely named tmp file used to define new payment account details.
     */
    public File getPaymentAccountForm(String paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethod(paymentMethodId);
        File file = getTmpJsonFile(paymentMethodId);
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(checkNotNull(file), false), UTF_8)) {
            PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
            Class<? extends PaymentAccount> clazz = paymentAccount.getClass();
            Gson gson = gsonBuilder.registerTypeAdapter(clazz, new PaymentAccountTypeAdapter(clazz, excludedFields)).create();
            String json = gson.toJson(paymentAccount); // serializes target to json
            outputStreamWriter.write(json);
        } catch (Exception ex) {
            String errMsg = format("cannot create a payment account form for a %s payment method", paymentMethodId);
            log.error(StringUtils.capitalize(errMsg) + ".", ex);
            throw new IllegalStateException(errMsg);
        }
        return file;
    }

    /**
     * De-serialize a PaymentAccount json form into a new PaymentAccount instance.
     *
     * @param jsonForm The file representing a new payment account form.
     * @return A populated PaymentAccount subclass instance.
     */
    @SuppressWarnings("unused")
    @VisibleForTesting
    public PaymentAccount toPaymentAccount(File jsonForm) {
        String jsonString = toJsonString(jsonForm);
        return toPaymentAccount(jsonString);
    }

    /**
     * De-serialize a PaymentAccount json string into a new PaymentAccount instance.
     *
     * @param jsonString The json data representing a new payment account form.
     * @return A populated PaymentAccount subclass instance.
     */
    public PaymentAccount toPaymentAccount(String jsonString) {
        Class<? extends PaymentAccount> clazz = getPaymentAccountClassFromJson(jsonString);
        Gson gson = gsonBuilder.registerTypeAdapter(clazz, new PaymentAccountTypeAdapter(clazz)).create();
        return gson.fromJson(jsonString, clazz);
    }

    public String toJsonString(File jsonFile) {
        try {
            checkNotNull(jsonFile, "json file cannot be null");
            return new String(Files.readAllBytes(Paths.get(jsonFile.getAbsolutePath())));
        } catch (IOException ex) {
            String errMsg = format("cannot read json string from file '%s'",
                    jsonFile.getAbsolutePath());
            log.error(StringUtils.capitalize(errMsg) + ".", ex);
            throw new IllegalStateException(errMsg);
        }
    }

    @VisibleForTesting
    public URI getClickableURI(File jsonFile) {
        try {
            return new URI("file",
                    "",
                    jsonFile.toURI().getPath(),
                    null,
                    null);
        } catch (URISyntaxException ex) {
            String errMsg = format("cannot create clickable url to file '%s'",
                    jsonFile.getAbsolutePath());
            log.error(StringUtils.capitalize(errMsg) + ".", ex);
            throw new IllegalStateException(errMsg);
        }
    }

    @VisibleForTesting
    public static File getTmpJsonFile(String paymentMethodId) {
        File file;
        try {
            // Creates a tmp file that includes a random number string between the
            // prefix and suffix, i.e., sepa_form_13243546575879.json, so there is
            // little chance this will fail because the tmp file already exists.
            file = File.createTempFile(paymentMethodId.toLowerCase() + "_form_",
                    ".json",
                    Paths.get(getProperty("java.io.tmpdir")).toFile());
        } catch (IOException ex) {
            String errMsg = format("cannot create json file for a %s payment method",
                    paymentMethodId);
            log.error(StringUtils.capitalize(errMsg) + ".", ex);
            throw new IllegalStateException(errMsg);
        }
        return file;
    }

    private Class<? extends PaymentAccount> getPaymentAccountClassFromJson(String json) {
        Map<String, Object> jsonMap = gsonBuilder.create().fromJson(json, (Type) Object.class);
        String paymentMethodId = checkNotNull((String) jsonMap.get("paymentMethodId"),
                format("cannot not find a paymentMethodId in json string: %s", json));
        return getPaymentAccountClass(paymentMethodId);
    }

    private Class<? extends PaymentAccount> getPaymentAccountClass(String paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethod(paymentMethodId);
        return PaymentAccountFactory.getPaymentAccount(paymentMethod).getClass();
    }
}
