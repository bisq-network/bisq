package bisq.apitest.method.payment;

import bisq.core.api.model.PaymentAccountForm;
import bisq.core.locale.Res;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;



import bisq.apitest.method.MethodTest;

@Slf4j
public class AbstractPaymentAccountTest extends MethodTest {

    static final String PROPERTY_NAME_COMMENT = "_COMMENT_";
    static final String PROPERTY_VALUE_COMMENT = "Please do not edit the paymentMethodId field.";

    static final String PROPERTY_NAME_PAYMENT_METHOD_ID = "paymentMethodId";

    static final String PROPERTY_NAME_ACCOUNT_NAME = "accountName";
    static final String PROPERTY_NAME_ACCOUNT_NR = "accountNr";
    static final String PROPERTY_NAME_ACCOUNT_TYPE = "accountType";
    static final String PROPERTY_NAME_BANK_ACCOUNT_NAME = "bankAccountName";
    static final String PROPERTY_NAME_BANK_ACCOUNT_NUMBER = "bankAccountNumber";
    static final String PROPERTY_NAME_BANK_ACCOUNT_TYPE = "bankAccountType";
    static final String PROPERTY_NAME_BANK_BRANCH_CODE = "bankBranchCode";
    static final String PROPERTY_NAME_BANK_BRANCH_NAME = "bankBranchName";
    static final String PROPERTY_NAME_BANK_CODE = "bankCode";
    @SuppressWarnings("unused")
    static final String PROPERTY_NAME_BANK_ID = "bankId";
    static final String PROPERTY_NAME_BANK_NAME = "bankName";
    static final String PROPERTY_NAME_BRANCH_ID = "branchId";
    static final String PROPERTY_NAME_BIC = "bic";
    static final String PROPERTY_NAME_COUNTRY = "country";
    static final String PROPERTY_NAME_CITY = "city";
    static final String PROPERTY_NAME_CONTACT = "contact";
    static final String PROPERTY_NAME_EMAIL = "email";
    static final String PROPERTY_NAME_EMAIL_OR_MOBILE_NR = "emailOrMobileNr";
    static final String PROPERTY_NAME_EXTRA_INFO = "extraInfo";
    static final String PROPERTY_NAME_HOLDER_NAME = "holderName";
    static final String PROPERTY_NAME_HOLDER_TAX_ID = "holderTaxId";
    static final String PROPERTY_NAME_IBAN = "iban";
    static final String PROPERTY_NAME_MOBILE_NR = "mobileNr";
    static final String PROPERTY_NAME_NATIONAL_ACCOUNT_ID = "nationalAccountId";
    static final String PROPERTY_NAME_PAY_ID = "payid";
    static final String PROPERTY_NAME_POSTAL_ADDRESS = "postalAddress";

    static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    static final Map<String, Object> EXPECTED_FORM = new HashMap<>();

    // A payment account serializer / deserializer.
    static final PaymentAccountForm PAYMENT_ACCOUNT_FORM = new PaymentAccountForm();

    @BeforeEach
    public void setup() {
        Res.setup();
    }

    protected final File getEmptyForm(TestInfo testInfo, String paymentMethodId) {
        // This would normally be done in @BeforeEach, but these test cases might be
        // called from a single 'scenario' test case, and the @BeforeEach -> clear()
        // would be skipped.
        EXPECTED_FORM.clear();

        File emptyForm = getPaymentAccountForm(alicedaemon, paymentMethodId);
        // A short cut over the API:
        // File emptyForm = PAYMENT_ACCOUNT_FORM.getPaymentAccountForm(paymentMethodId);
        emptyForm.deleteOnExit();
        log.info("{} Empty form saved to {}", testName(testInfo), PAYMENT_ACCOUNT_FORM.getClickableURI(emptyForm));
        return emptyForm;
    }

    protected final void verifyEmptyForm(File jsonForm, String paymentMethodId, String... fields) {
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyForm = (Map<String, Object>) GSON.fromJson(
                PAYMENT_ACCOUNT_FORM.toJsonString(jsonForm),
                Object.class);
        assertNotNull(emptyForm);
        assertEquals(PROPERTY_VALUE_COMMENT, emptyForm.get(PROPERTY_NAME_COMMENT));
        assertEquals(paymentMethodId, emptyForm.get(PROPERTY_NAME_PAYMENT_METHOD_ID));
        assertEquals("Your accountname", emptyForm.get(PROPERTY_NAME_ACCOUNT_NAME));
        for (String field : fields) {
            assertEquals("Your " + field.toLowerCase(), emptyForm.get(field));
        }
    }

    protected final File fillPaymentAccountForm() {
        File tmpJsonForm = null;
        try {
            tmpJsonForm = File.createTempFile("temp_acct_form_",
                    ".json",
                    Paths.get(getProperty("java.io.tmpdir")).toFile());
            tmpJsonForm.deleteOnExit();
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(tmpJsonForm), UTF_8));
            writer.beginObject();
            writer.name(PROPERTY_NAME_COMMENT);
            writer.value(PROPERTY_VALUE_COMMENT);
            for (Map.Entry<String, Object> entry : EXPECTED_FORM.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                writer.name(k);
                writer.value(v.toString());
            }
            writer.endObject();
            writer.close();
        } catch (IOException ex) {
            log.error("", ex);
            fail(format("Could not write json file from form entries %s", EXPECTED_FORM));
        }
        return tmpJsonForm;
    }
}
