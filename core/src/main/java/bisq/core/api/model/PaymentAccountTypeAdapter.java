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


import bisq.core.locale.Country;
import bisq.core.locale.FiatCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.ReflectionUtils.getSetterMethodForFieldInClassHierarchy;
import static bisq.common.util.ReflectionUtils.getVisibilityModifierAsString;
import static bisq.common.util.ReflectionUtils.isSetterOnClass;
import static bisq.common.util.ReflectionUtils.loadFieldListForClassHierarchy;
import static bisq.core.locale.CountryUtil.findCountryByCode;
import static bisq.core.locale.CurrencyUtil.getCurrencyByCountryCode;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Comparator.comparing;

@Slf4j
class PaymentAccountTypeAdapter extends TypeAdapter<PaymentAccount> {

    private final Class<? extends PaymentAccount> paymentAccountType;
    private final Class<? extends PaymentAccountPayload> paymentAccountPayloadType;
    private final Map<Field, Optional<Method>> fieldSettersMap;
    private final Predicate<Field> isExcludedField;

    /**
     * Constructor used when de-serializing a json payment account form into a
     * PaymentAccount instance.
     *
     * @param paymentAccountType the PaymentAccount subclass being instantiated
     */
    public PaymentAccountTypeAdapter(Class<? extends PaymentAccount> paymentAccountType) {
        this(paymentAccountType, new String[]{});
    }

    /**
     * Constructor used when serializing a PaymentAccount subclass instance into a json
     * payment account json form.
     *
     * @param paymentAccountType the PaymentAccount subclass being serialized
     * @param excludedFields a string array of field names to exclude from the serialized
     *                       payment account json form.
     */
    public PaymentAccountTypeAdapter(Class<? extends PaymentAccount> paymentAccountType, String[] excludedFields) {
        this.paymentAccountType = paymentAccountType;
        this.paymentAccountPayloadType = getPaymentAccountPayloadType();
        this.isExcludedField = (f) -> Arrays.stream(excludedFields).anyMatch(e -> e.equals(f.getName()));
        this.fieldSettersMap = getFieldSetterMap();
    }

    @Override
    public void write(JsonWriter out, PaymentAccount account) throws IOException {
        // We write a blank payment acct form for a payment method id.
        // We're not serializing a real payment account instance here.
        if (log.isDebugEnabled())
            log.debug("Writing PaymentAccount json form for fields with accessors...");

        out.beginObject();
        writeCommonFields(out, account);
        fieldSettersMap.forEach((field, value) -> {
            try {
                // Write out a json element if there is a @Setter for this field.
                if (value.isPresent()) {
                    if (log.isDebugEnabled())
                        log.debug("Append form with settable field: {} {} {} setter: {}",
                                getVisibilityModifierAsString(field),
                                field.getType().getSimpleName(),
                                field.getName(),
                                value);

                    String fieldName = field.getName();
                    out.name(fieldName);
                    out.value("Your " + fieldName.toLowerCase());
                }
            } catch (Exception ex) {
                throw new IllegalStateException(
                        format("Could not serialize a %s to json", account.getClass().getSimpleName()), ex);
            }
        });
        out.endObject();
        if (log.isDebugEnabled())
            log.debug("Done writing PaymentAccount json form.");
    }

    @Override
    public PaymentAccount read(JsonReader in) throws IOException {
        if (log.isDebugEnabled())
            log.debug("De-serializing json to new {} ...", paymentAccountType.getSimpleName());

        PaymentAccount account = initNewPaymentAccount();
        in.beginObject();
        while (in.hasNext()) {
            String currentFieldName = in.nextName();

            // Some of the fields are common to all payment account types.
            if (didReadCommonField(in, account, currentFieldName))
                continue;

            // If the account is a subclass of CountryBasedPaymentAccount, set the
            // account's Country, and use the Country to derive and set the account's
            // FiatCurrency.
            if (didReadCountryField(in, account, currentFieldName))
                continue;

            try {
                Optional<Field> field = fieldSettersMap.keySet().stream()
                        .filter(k -> k.getName().equals(currentFieldName)).findFirst();

                field.ifPresentOrElse((f) -> invokeSetterMethod(account, f, in), () -> {
                    throw new IllegalStateException(
                            format("Could not de-serialize json to a '%s' because there is no %s field.",
                                    account.getClass().getSimpleName(),
                                    currentFieldName));
                });
            } catch (Exception ex) {
                throw new IllegalStateException(
                        format("Could not de-serialize json to a '%s'.",
                                account.getClass().getSimpleName()), ex);
            }
        }
        in.endObject();
        if (log.isDebugEnabled())
            log.debug("Done de-serializing json.");

        return account;
    }

    private void invokeSetterMethod(PaymentAccount account, Field field, JsonReader jsonReader) {
        Optional<Method> setter = fieldSettersMap.get(field);
        if (setter.isPresent()) {
            try {
                // The setter might be on the PaymentAccount instance, or its
                // PaymentAccountPayload instance.
                if (isSetterOnPaymentAccountClass(setter.get(), account)) {
                    setter.get().invoke(account, nextStringOrNull(jsonReader));
                } else if (isSetterOnPaymentAccountPayloadClass(setter.get(), account)) {
                    setter.get().invoke(account.getPaymentAccountPayload(), nextStringOrNull(jsonReader));
                } else {
                    String exMsg = format("Could not de-serialize json to a '%s' using reflection"
                                    + " because the setter's declaring class was not found.",
                            account.getClass().getSimpleName());
                    throw new IllegalStateException(exMsg);
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException(
                        format("Could not de-serialize json to a '%s' due to reflection error.",
                                account.getClass().getSimpleName()), ex);
            }
        } else {
            throw new IllegalStateException(
                    format("Could not de-serialize json to a '%s' because there is no setter for field %s.",
                            account.getClass().getSimpleName(),
                            field.getName()));
        }
    }

    private boolean isSetterOnPaymentAccountClass(Method setter, PaymentAccount account) {
        return isSetterOnClass(setter, account.getClass());
    }

    private boolean isSetterOnPaymentAccountPayloadClass(Method setter, PaymentAccount account) {
        return isSetterOnClass(setter, account.getPaymentAccountPayload().getClass())
                || isSetterOnClass(setter, account.getPaymentAccountPayload().getClass().getSuperclass());
    }

    private Map<Field, Optional<Method>> getFieldSetterMap() {
        List<Field> orderedFields = getOrderedFields();
        Map<Field, Optional<Method>> map = new LinkedHashMap<>();
        for (Field field : orderedFields) {
            Optional<Method> setter = getSetterMethodForFieldInClassHierarchy(field, paymentAccountType)
                    .or(() -> getSetterMethodForFieldInClassHierarchy(field, paymentAccountPayloadType));
            map.put(field, setter);
        }
        return Collections.unmodifiableMap(map);
    }

    private List<Field> getOrderedFields() {
        List<Field> fields = new ArrayList<>();
        loadFieldListForClassHierarchy(fields, paymentAccountType, isExcludedField);
        loadFieldListForClassHierarchy(fields, paymentAccountPayloadType, isExcludedField);
        fields.sort(comparing(Field::getName));
        return fields;
    }

    private String nextStringOrNull(JsonReader in) {
        try {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                return in.nextString();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not peek at next String value in JsonReader.", ex);
        }
    }

    @SuppressWarnings("unused")
    private Long nextLongOrNull(JsonReader in) {
        try {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                return in.nextLong();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not peek at next Long value in JsonReader.", ex);
        }
    }

    private void writeCommonFields(JsonWriter out, PaymentAccount account) throws IOException {
        if (log.isDebugEnabled())
            log.debug("writeCommonFields(out, {}) -> paymentMethodId = {}",
                    account, account.getPaymentMethod().getId());

        out.name("_COMMENT_");
        out.value("Please do not edit the paymentMethodId field.");

        out.name("paymentMethodId");
        out.value(account.getPaymentMethod().getId());
    }

    private boolean didReadCommonField(JsonReader in, PaymentAccount account, String fieldName) {
        switch (fieldName) {
            case "_COMMENT_":
            case "paymentMethodId":
                // skip
                nextStringOrNull(in);
                return true;
            case "accountName":
                account.setAccountName(nextStringOrNull(in));
                return true;
            default:
                return false;
        }
    }

    private boolean didReadCountryField(JsonReader in, PaymentAccount account, String fieldName) {
        if (account.isCountryBasedPaymentAccount() && fieldName.equals("country")) {
            // Read the country code, and use it to set the account's country and single
            // trade currency fields.
            String countryCode = nextStringOrNull(in);
            Optional<Country> country = findCountryByCode(countryCode);
            if (country.isPresent()) {
                ((CountryBasedPaymentAccount) account).setCountry(country.get());
                FiatCurrency fiatCurrency = getCurrencyByCountryCode(checkNotNull(countryCode));
                account.setSingleTradeCurrency(fiatCurrency);
                return true;
            } else {
                throw new IllegalStateException(
                        format("Could not de-serialize json to a '%s' because %s is an invalid country code.",
                                account.getClass().getSimpleName(), countryCode));
            }
        } else {
            return false;
        }
    }

    private Class<? extends PaymentAccountPayload> getPaymentAccountPayloadType() {
        try {
            Package pkg = PaymentAccountPayload.class.getPackage();
            //noinspection unchecked
            return (Class<? extends PaymentAccountPayload>) Class.forName(pkg.getName()
                    + "." + paymentAccountType.getSimpleName() + "Payload");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    format("Could not get payload class for %s",
                            paymentAccountType.getSimpleName()), ex);
        }
    }

    private PaymentAccount initNewPaymentAccount() {
        try {
            Constructor<?> constructor = paymentAccountType.getDeclaredConstructor();
            PaymentAccount paymentAccount = (PaymentAccount) constructor.newInstance();
            paymentAccount.init();
            return paymentAccount;
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(format("No default declared constructor  found for class %s",
                    paymentAccountType.getSimpleName()), ex);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException ex) {
            throw new IllegalStateException(format("Could not instantiate class %s",
                    paymentAccountType.getSimpleName()), ex);
        }
    }
}
