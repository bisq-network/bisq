package bisq.cli.opt;

import org.junit.jupiter.api.Test;

import static bisq.cli.Method.canceloffer;
import static bisq.cli.Method.createoffer;
import static bisq.cli.Method.createpaymentacct;
import static bisq.cli.opts.OptLabel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;



import bisq.cli.opts.CancelOfferOptionParser;
import bisq.cli.opts.CreateOfferOptionParser;
import bisq.cli.opts.CreatePaymentAcctOptionParser;


public class OptionParsersTest {

    private static final String PASSWORD_OPT = "--" + OPT_PASSWORD + "=" + "xyz";

    // CancelOffer opt parsing tests

    @Test
    public void testCancelOfferWithMissingOfferIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name()
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CancelOfferOptionParser(args).parse());
        assertEquals("no offer id specified", exception.getMessage());
    }

    @Test
    public void testCancelOfferWithEmptyOfferIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name(),
                "--" + OPT_OFFER_ID + "=" // missing opt value
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CancelOfferOptionParser(args).parse());
        assertEquals("no offer id specified", exception.getMessage());
    }

    @Test
    public void testCancelOfferWithMissingOfferIdValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name(),
                "--" + OPT_OFFER_ID // missing equals sign & opt value
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CancelOfferOptionParser(args).parse());
        assertEquals("offer-id requires an argument", exception.getMessage());
    }

    @Test
    public void testValidCancelOfferOpts() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID"
        };
        new CancelOfferOptionParser(args).parse();
    }

    // CreateOffer opt parsing tests

    @Test
    public void testCreateOfferOptParserWithMissingPaymentAccountIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name()
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("no payment account id specified", exception.getMessage());
    }

    @Test
    public void testCreateOfferOptParserWithEmptyPaymentAccountIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("payment-account requires an argument", exception.getMessage());
    }

    @Test
    public void testCreateOfferOptParserWithMissingDirectionOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT + "=" + "abc-payment-acct-id-123"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("no direction (buy|sell) specified", exception.getMessage());
    }


    @Test
    public void testCreateOfferOptParserWithMissingDirectionOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT + "=" + "abc-payment-acct-id-123",
                "--" + OPT_DIRECTION + "=" + ""
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("no direction (buy|sell) specified", exception.getMessage());
    }

    @Test
    public void testValidCreateOfferOpts() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT + "=" + "abc-payment-acct-id-123",
                "--" + OPT_DIRECTION + "=" + "BUY",
                "--" + OPT_CURRENCY_CODE + "=" + "EUR",
                "--" + OPT_AMOUNT + "=" + "0.125",
                "--" + OPT_MKT_PRICE_MARGIN + "=" + "0.0",
                "--" + OPT_SECURITY_DEPOSIT + "=" + "25.0"
        };
        new CreateOfferOptionParser(args).parse();
    }

    // CreatePaymentAcct opt parser tests

    @Test
    public void testCreatePaymentAcctOptParserWithMissingPaymentFormOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createpaymentacct.name()
                // OPT_PAYMENT_ACCOUNT_FORM
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreatePaymentAcctOptionParser(args).parse());
        assertEquals("no path to json payment account form specified", exception.getMessage());
    }

    @Test
    public void testCreatePaymentAcctOptParserWithMissingPaymentFormOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createpaymentacct.name(),
                "--" + OPT_PAYMENT_ACCOUNT_FORM + "="
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreatePaymentAcctOptionParser(args).parse());
        assertEquals("no path to json payment account form specified", exception.getMessage());
    }

    @Test
    public void testCreatePaymentAcctOptParserWithInvalidPaymentFormOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createpaymentacct.name(),
                "--" + OPT_PAYMENT_ACCOUNT_FORM + "=" + "/tmp/milkyway/solarsystem/mars"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreatePaymentAcctOptionParser(args).parse());
        assertEquals("json payment account form '/tmp/milkyway/solarsystem/mars' could not be found",
                exception.getMessage());
    }
}
