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

import static bisq.core.payment.payload.PaymentMethod.getPaymentMethodById;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.io.File.separatorChar;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;

@Singleton
@Slf4j
public class PaymentAccountForm {

    private final GsonBuilder gsonBuilder = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls();

    // Names of PaymentAccount fields to exclude from json forms.
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
            "paymentMethodId",
            "selectedTradeCurrency",
            "tradeCurrencies",
            "HOLDER_NAME",
            "SALT"
    };

    public File getPaymentAccountForm(String paymentMethodId) {
        PaymentMethod paymentMethod = getPaymentMethodById(paymentMethodId);
        File file = new File(getProperty("java.io.tmpdir") + separatorChar
                + paymentMethod.getId().toLowerCase() + "_form.json");
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file, false), UTF_8)) {
            PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
            Class<? extends PaymentAccount> clazz = paymentAccount.getClass();
            Gson gson = gsonBuilder.registerTypeAdapter(clazz, new PaymentAccountTypeAdapter(clazz, excludedFields)).create();
            String json = gson.toJson(paymentAccount); // serializes target to json
            outputStreamWriter.write(json);
        } catch (Exception ex) {
            log.error(format("Could not export json file for %s account.", paymentMethod.getShortName()), ex);
        }
        return file;
    }

    public PaymentAccount toPaymentAccount(File jsonForm) {
        String json = toJsonString(jsonForm);
        Class<? extends PaymentAccount> clazz = getPaymentAccountClassFromJson(json);
        Gson gson = gsonBuilder.registerTypeAdapter(clazz, new PaymentAccountTypeAdapter(clazz)).create();
        return gson.fromJson(json, clazz);
    }

    public String toJsonString(File jsonFile) {
        try {
            checkNotNull(jsonFile, "json file cannot be null");
            return new String(Files.readAllBytes(Paths.get(jsonFile.getAbsolutePath())));
        } catch (IOException ex) {
            throw new IllegalStateException(format("Could not read content from file '%s'",
                    jsonFile.getAbsolutePath()), ex);
        }
    }

    public URI getClickableURI(File jsonForm) {
        try {
            return new URI("file",
                    "",
                    jsonForm.toURI().getPath(),
                    null,
                    null);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("", ex);
        }
    }

    private Class<? extends PaymentAccount> getPaymentAccountClassFromJson(String json) {
        Map<String, Object> jsonMap = gsonBuilder.create().fromJson(json, (Type) Object.class);
        String paymentMethodId = checkNotNull((String) jsonMap.get("paymentMethodId"),
                format("Could not find a paymentMethodId in the json string: %s", json));
        return getPaymentAccountClass(paymentMethodId);
    }

    private Class<? extends PaymentAccount> getPaymentAccountClass(String paymentMethodId) {
        PaymentMethod paymentMethod = PaymentMethod.getPaymentMethodById(paymentMethodId);
        return PaymentAccountFactory.getPaymentAccount(paymentMethod).getClass();
    }
}
