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
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.MoneyGramAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.ReflectionUtils.*;
import static bisq.common.util.Utilities.decodeFromHex;
import static bisq.core.locale.CountryUtil.findCountryByCode;
import static bisq.core.locale.CurrencyUtil.*;
import static bisq.core.payment.payload.PaymentMethod.*;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.capitalize;

@Slf4j
class PaymentAccountTypeAdapter extends TypeAdapter<PaymentAccount> {

    private static final String[] JSON_COMMENTS = new String[]{
            "Do not manually edit the paymentMethodId field.",
            "Edit the salt field only if you are recreating a payment"
                    + " account on a new installation and wish to preserve the account age."
    };

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
        this.isExcludedField = (f) -> stream(excludedFields).anyMatch(e -> e.equals(f.getName()));
        this.fieldSettersMap = getFieldSetterMap();
    }

    @Override
    public void write(JsonWriter out, PaymentAccount account) throws IOException {
        // We write a blank payment acct form for a payment method id.
        // We're not serializing a real payment account instance here.
        out.beginObject();

        writeComments(out, account);

        out.name("paymentMethodId");
        out.value(account.getPaymentMethod().getId());

        // Write the editable, PaymentAccount subclass specific fields.
        writeInnerMutableFields(out, account);

        // The last field in all json forms is the empty, editable salt field.
        out.name("salt");
        out.value("");

        out.endObject();
    }

    private void writeComments(JsonWriter out, PaymentAccount account) throws IOException {
        // All json forms start with immutable _COMMENTS_ and paymentMethodId fields.
        out.name("_COMMENTS_");
        out.beginArray();
        for (String s : JSON_COMMENTS) {
            out.value(s);
        }
        if (account.hasPaymentMethodWithId("SWIFT_ID")) {
            // Add extra comments for more complex swift account form.
            List<String> wrappedSwiftComments = Res.getWrappedAsList("payment.swift.info.account", 110);
            for (String line : wrappedSwiftComments) {
                out.value(line);
            }
        }
        out.endArray();
    }


    private void writeInnerMutableFields(JsonWriter out, PaymentAccount account) {
        if (account.hasMultipleCurrencies()) {
            writeTradeCurrenciesField(out, account);
            writeSelectedTradeCurrencyField(out, account);
        }

        fieldSettersMap.forEach((field, value) -> {
            try {
                // Write out a json element if there is a @Setter for this field.
                if (value.isPresent()) {
                    log.debug("Append form with settable field: {} {} {} setter: {}",
                            getVisibilityModifierAsString(field),
                            field.getType().getSimpleName(),
                            field.getName(),
                            value);

                    String fieldName = field.getName();
                    out.name(fieldName);
                    if (fieldName.equals("country"))
                        out.value("your two letter country code");
                    else
                        out.value("your " + fieldName.toLowerCase());
                }
            } catch (Exception ex) {
                String errMsg = format("cannot create a new %s json form",
                        account.getClass().getSimpleName());
                log.error(capitalize(errMsg) + ".", ex);
                throw new IllegalStateException("programmer error: " + errMsg);
            }
        });
    }

    // In some cases (TransferwiseAccount), we need to include a 'tradeCurrencies'
    // field in the json form, though the 'tradeCurrencies' field has no setter method in
    // the PaymentAccount class hierarchy.  At of time of this change, TransferwiseAccount
    // is the only known exception to the rule.
    private void writeTradeCurrenciesField(JsonWriter out, PaymentAccount account) {
        try {
            String fieldName = "tradeCurrencies";
            log.debug("Append form with non-settable field: {}", fieldName);
            out.name(fieldName);
            out.value("comma delimited currency code list, e.g., gbp,eur,jpy,usd");
        } catch (Exception ex) {
            String errMsg = format("cannot create a new %s json form",
                    account.getClass().getSimpleName());
            log.error(capitalize(errMsg) + ".", ex);
            throw new IllegalStateException("programmer error: " + errMsg);
        }
    }

    // PaymentAccounts that support multiple 'tradeCurrencies' need to define a
    // 'selectedTradeCurrency' field (not simply defaulting to first in list).
    // Write this field to the form.
    private void writeSelectedTradeCurrencyField(JsonWriter out, PaymentAccount account) {
        try {
            String fieldName = "selectedTradeCurrency";
            log.debug("Append form with settable field: {}", fieldName);
            out.name(fieldName);
            out.value("primary trading currency code, e.g., eur");
        } catch (Exception ex) {
            String errMsg = format("cannot create a new %s json form",
                    account.getClass().getSimpleName());
            log.error(capitalize(errMsg) + ".", ex);
            throw new IllegalStateException("programmer error: " + errMsg);
        }
    }

    @Override
    public PaymentAccount read(JsonReader in) throws IOException {
        PaymentAccount account = initNewPaymentAccount();
        in.beginObject();
        while (in.hasNext()) {
            String currentFieldName = in.nextName();

            // The tradeCurrencies field is common to all payment account types,
            // but has no setter.
            if (didReadTradeCurrenciesField(in, account, currentFieldName))
                continue;

            // The selectedTradeCurrency field is common to all payment account types,
            // but is @Nullable, and may not need to be explicitly defined by user.
            if (didReadSelectedTradeCurrencyField(in, account, currentFieldName))
                continue;

            // Some fields are common to all payment account types.
            if (didReadCommonField(in, account, currentFieldName))
                continue;

            // If the account is a subclass of CountryBasedPaymentAccount, set the
            // account's Country, and use the Country to derive and set the account's
            // FiatCurrency.
            if (didReadCountryField(in, account, currentFieldName))
                continue;

            Optional<Field> field = fieldSettersMap.keySet().stream()
                    .filter(k -> k.getName().equals(currentFieldName)).findFirst();

            field.ifPresentOrElse((f) -> invokeSetterMethod(account, f, in), () -> {
                throw new IllegalStateException(
                        format("programmer error: cannot de-serialize json to a '%s' "
                                        + " because there is no %s field.",
                                account.getClass().getSimpleName(),
                                currentFieldName));
            });
        }
        in.endObject();
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
                    String errMsg = format("programmer error: cannot de-serialize json to a '%s' using reflection"
                                    + " because the setter method's declaring class was not found.",
                            account.getClass().getSimpleName());
                    throw new IllegalStateException(errMsg);
                }
            } catch (ReflectiveOperationException ex) {
                handleSetFieldValueError(account, field, ex);
            }
        } else {
            throw new IllegalStateException(
                    format("programmer error: cannot de-serialize json to a '%s' "
                                    + " because field value cannot be set %s.",
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
        return unmodifiableMap(map);
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
            String errMsg = "cannot see next string in json reader";
            log.error(capitalize(errMsg) + ".", ex);
            throw new IllegalStateException("programmer error: " + errMsg);
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
            String errMsg = "cannot see next long in json reader";
            log.error(capitalize(errMsg) + ".", ex);
            throw new IllegalStateException("programmer error: " + errMsg);
        }
    }

    private final Predicate<String> isCommaDelimitedCurrencyList = (s) -> s != null && s.contains(",");
    private final Function<String, List<String>> commaDelimitedCodesToList = (s) -> {
        if (isCommaDelimitedCurrencyList.test(s))
            return stream(s.split(",")).map(a -> a.trim().toUpperCase()).collect(toList());
        else if (s != null && !s.isEmpty())
            return singletonList(s.trim().toUpperCase());
        else
            return new ArrayList<>();
    };

    private boolean didReadTradeCurrenciesField(JsonReader in,
                                                PaymentAccount account,
                                                String fieldName) {
        if (!fieldName.equals("tradeCurrencies"))
            return false;

        // The PaymentAccount.tradeCurrencies field is a special case because it has
        // no setter, so we add currencies to the List here if the payment account
        // supports multiple trade currencies.
        String fieldValue = nextStringOrNull(in);
        List<String> currencyCodes = commaDelimitedCodesToList.apply(fieldValue);
        Optional<List<TradeCurrency>> tradeCurrencies = getReconciledTradeCurrencies(currencyCodes, account);
        if (tradeCurrencies.isPresent()) {
            for (TradeCurrency tradeCurrency : tradeCurrencies.get()) {
                account.addCurrency(tradeCurrency);
            }
        } else {
            // Log a warning.  We should not throw an exception here because the
            // gson library will not pass it up to the calling Bisq object exactly as
            // it would be defined here (causing confusion).  Do a check in a calling
            // class to make sure the tradeCurrencies field is populated in the
            // PaymentAccount object, if it is required for the payment account method.
            log.warn("No trade currencies were found in the {} account form.",
                    account.getPaymentMethod().getDisplayString());
        }
        return true;
    }

    private Optional<List<TradeCurrency>> getReconciledTradeCurrencies(List<String> currencyCodes,
                                                                       PaymentAccount account) {
        if (account.hasPaymentMethodWithId(ADVANCED_CASH_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllAdvancedCashCurrencies());
        else if (account.hasPaymentMethodWithId(AMAZON_GIFT_CARD_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllAmazonGiftCardCurrencies());
        else if (account.hasPaymentMethodWithId(CAPITUAL_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllCapitualCurrencies());
        else if (account.hasPaymentMethodWithId(MONEY_GRAM_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllMoneyGramCurrencies());
        else if (account.hasPaymentMethodWithId(PAXUM_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllPaxumCurrencies());
        else if (account.hasPaymentMethodWithId(PAYSERA_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllPayseraCurrencies());
        else if (account.hasPaymentMethodWithId(REVOLUT_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllRevolutCurrencies());
        else if (account.hasPaymentMethodWithId(SWIFT_ID))
            return getTradeCurrenciesInList(currencyCodes,
                    new ArrayList<>(getAllSortedFiatCurrencies(
                            comparing(TradeCurrency::getCode))));
        else if (account.hasPaymentMethodWithId(TRANSFERWISE_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllTransferwiseCurrencies());
        else if (account.hasPaymentMethodWithId(UPHOLD_ID))
            return getTradeCurrenciesInList(currencyCodes, getAllUpholdCurrencies());
        else
            return Optional.empty();
    }

    private boolean didReadSelectedTradeCurrencyField(JsonReader in,
                                                      PaymentAccount account,
                                                      String fieldName) {
        if (!fieldName.equals("selectedTradeCurrency"))
            return false;

        String fieldValue = nextStringOrNull(in);
        if (fieldValue != null && !fieldValue.isEmpty()) {
            Optional<TradeCurrency> tradeCurrency = getTradeCurrency(fieldValue.toUpperCase());
            if (tradeCurrency.isPresent()) {
                account.setSelectedTradeCurrency(tradeCurrency.get());
            } else {
                // Log an error.  We should not throw an exception here because the
                // gson library will not pass it up to the calling Bisq object exactly as
                // it would be defined here (causing confusion).
                log.error("{} is not a valid trade currency code.", fieldValue);
            }
        }
        return true;
    }

    private boolean didReadCommonField(JsonReader in,
                                       PaymentAccount account,
                                       String fieldName) throws IOException {
        switch (fieldName) {
            case "_COMMENTS_":
            case "paymentMethodId":
                // Skip over comments and paymentMethodId field, which
                // are already set on the PaymentAccount instance.
                in.skipValue();
                return true;
            case "accountName":
                // Set the acct name using the value read from json.
                account.setAccountName(nextStringOrNull(in));
                return true;
            case "salt":
                // Set the acct salt using the value read from json.
                String saltAsHex = nextStringOrNull(in);
                if (saltAsHex != null && !saltAsHex.trim().isEmpty()) {
                    account.setSalt(decodeFromHex(saltAsHex));
                }
                return true;
            default:
                return false;
        }
    }

    private boolean didReadCountryField(JsonReader in, PaymentAccount account, String fieldName) {
        if (!fieldName.equals("country"))
            return false;

        String countryCode = nextStringOrNull(in);
        Optional<Country> country = findCountryByCode(countryCode);
        if (country.isPresent()) {

            if (account.isCountryBasedPaymentAccount()) {
                ((CountryBasedPaymentAccount) account).setCountry(country.get());
                FiatCurrency fiatCurrency = getCurrencyByCountryCode(checkNotNull(countryCode));
                account.setSingleTradeCurrency(fiatCurrency);
            } else if (account.hasPaymentMethodWithId(MONEY_GRAM_ID)) {
                ((MoneyGramAccount) account).setCountry(country.get());
            } else {
                String errMsg = format("cannot set the country on a %s",
                        paymentAccountType.getSimpleName());
                log.error(capitalize(errMsg) + ".");
                throw new IllegalStateException("programmer error: " + errMsg);
            }

            return true;

        } else {
            throw new IllegalArgumentException(
                    format("'%s' is an invalid country code.", countryCode));
        }
    }

    private Class<? extends PaymentAccountPayload> getPaymentAccountPayloadType() {
        try {
            Package pkg = PaymentAccountPayload.class.getPackage();
            //noinspection unchecked
            return (Class<? extends PaymentAccountPayload>) Class.forName(pkg.getName()
                    + "." + paymentAccountType.getSimpleName() + "Payload");
        } catch (Exception ex) {
            String errMsg = format("cannot get the payload class for %s",
                    paymentAccountType.getSimpleName());
            log.error(capitalize(errMsg) + ".", ex);
            throw new IllegalStateException("programmer error: " + errMsg);
        }
    }

    private PaymentAccount initNewPaymentAccount() {
        try {
            Constructor<?> constructor = paymentAccountType.getDeclaredConstructor();
            PaymentAccount paymentAccount = (PaymentAccount) constructor.newInstance();
            paymentAccount.init();
            return paymentAccount;
        } catch (NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException ex) {
            String errMsg = format("cannot instantiate a new %s",
                    paymentAccountType.getSimpleName());
            log.error(capitalize(errMsg) + ".", ex);
            throw new IllegalStateException("programmer error: " + errMsg);
        }
    }
}
