package bisq.core.payment;

import bisq.core.payment.payload.PaymentMethod;

import org.junit.Assert;
import org.junit.Test;

public class PaymentAccountFactoryTest {

    @Test
    public void testGetPaymentAccount() {
        PaymentMethod uphold = PaymentMethod.UPHOLD;
        Assert.assertEquals(new UpholdAccount(), PaymentAccountFactory.getPaymentAccount(uphold));

        PaymentMethod moneyBeam = PaymentMethod.MONEY_BEAM;
        Assert.assertEquals(new MoneyBeamAccount(), PaymentAccountFactory.getPaymentAccount(moneyBeam));

        PaymentMethod popMoney = PaymentMethod.POPMONEY;
        Assert.assertEquals(new PopmoneyAccount(), PaymentAccountFactory.getPaymentAccount(popMoney));

        PaymentMethod revolut = PaymentMethod.REVOLUT;
        Assert.assertEquals(new RevolutAccount(), PaymentAccountFactory.getPaymentAccount(revolut));

        PaymentMethod perfectMoney = PaymentMethod.PERFECT_MONEY;
        Assert.assertEquals(new PerfectMoneyAccount(), PaymentAccountFactory.getPaymentAccount(perfectMoney));

//        PaymentMethod sepa = PaymentMethod.SEPA;
//        Assert.assertEquals(new SepaAccount(), PaymentAccountFactory.getPaymentAccount(sepa));

//        PaymentMethod sepaInstant = PaymentMethod.SEPA_INSTANT;
//        Assert.assertEquals(new SepaInstantAccount(), PaymentAccountFactory.getPaymentAccount(sepaInstant));

        PaymentMethod fpi = PaymentMethod.FASTER_PAYMENTS;
        Assert.assertEquals(new FasterPaymentsAccount(), PaymentAccountFactory.getPaymentAccount(fpi));

//        PaymentMethod nationalBank = PaymentMethod.NATIONAL_BANK;
//        Assert.assertEquals(new NationalBankAccount(), PaymentAccountFactory.getPaymentAccount(nationalBank));

//        PaymentMethod sameBank = PaymentMethod.SAME_BANK;
//        Assert.assertEquals(new SameBankAccount(), PaymentAccountFactory.getPaymentAccount(sameBank));

//        PaymentMethod specBanc = PaymentMethod.SPECIFIC_BANKS;
//        Assert.assertEquals(new SpecificBanksAccount(), PaymentAccountFactory.getPaymentAccount(specBanc));

        PaymentMethod jpnBank = PaymentMethod.JAPAN_BANK;
        Assert.assertEquals(new JapanBankAccount(),PaymentAccountFactory.getPaymentAccount(jpnBank));

        PaymentMethod australia = PaymentMethod.AUSTRALIA_PAYID;
        Assert.assertEquals(new AustraliaPayid(), PaymentAccountFactory.getPaymentAccount(australia));

        PaymentMethod aliPay = PaymentMethod.ALI_PAY;
        Assert.assertEquals(new AliPayAccount(), PaymentAccountFactory.getPaymentAccount(aliPay));

        PaymentMethod wechat = PaymentMethod.WECHAT_PAY;
        Assert.assertEquals(new WeChatPayAccount(), PaymentAccountFactory.getPaymentAccount(wechat));

        PaymentMethod swishPay = PaymentMethod.SWISH;
        Assert.assertEquals(new SwishAccount(), PaymentAccountFactory.getPaymentAccount(swishPay));

        PaymentMethod clearXChange = PaymentMethod.CLEAR_X_CHANGE;
        Assert.assertEquals(new ClearXchangeAccount(), PaymentAccountFactory.getPaymentAccount(clearXChange));

        PaymentMethod chase = PaymentMethod.CHASE_QUICK_PAY;
        Assert.assertEquals(new ChaseQuickPayAccount(), PaymentAccountFactory.getPaymentAccount(chase));

        PaymentMethod interacETransfer = PaymentMethod.INTERAC_E_TRANSFER;
        Assert.assertEquals(new InteracETransferAccount(), PaymentAccountFactory.getPaymentAccount(interacETransfer));

        PaymentMethod usPostal = PaymentMethod.US_POSTAL_MONEY_ORDER;
        Assert.assertEquals(new USPostalMoneyOrderAccount(), PaymentAccountFactory.getPaymentAccount(usPostal));

//        PaymentMethod cashDeposit = PaymentMethod.CASH_DEPOSIT;
//        Assert.assertEquals(new CashDepositAccount(), PaymentAccountFactory.getPaymentAccount(cashDeposit));

        PaymentMethod blockChain = PaymentMethod.BLOCK_CHAINS;
        Assert.assertEquals(new CryptoCurrencyAccount(), PaymentAccountFactory.getPaymentAccount(blockChain));

//        PaymentMethod moneygram = PaymentMethod.MONEY_GRAM;
//        Assert.assertEquals(new MoneyGramAccount(), PaymentAccountFactory.getPaymentAccount(moneygram));

//        PaymentMethod westernAccount = PaymentMethod.WESTERN_UNION;
//        Assert.assertEquals(new WesternUnionAccount(), PaymentAccountFactory.getPaymentAccount(westernAccount));

        PaymentMethod halCash = PaymentMethod.HAL_CASH;
        Assert.assertEquals(new HalCashAccount(), PaymentAccountFactory.getPaymentAccount(halCash));

//        PaymentMethod f2f = PaymentMethod.F2F;
//        Assert.assertEquals(new F2FAccount(), PaymentAccountFactory.getPaymentAccount(f2f));

        PaymentMethod cashByMail = PaymentMethod.CASH_BY_MAIL;
        Assert.assertEquals(new CashByMailAccount(), PaymentAccountFactory.getPaymentAccount(cashByMail));

//        PaymentMethod prompt = PaymentMethod.PROMPT_PAY;
//        Assert.assertEquals(new PromptPayAccount(), prompt);

        PaymentMethod advancedCash = PaymentMethod.ADVANCED_CASH;
        Assert.assertEquals(new AdvancedCashAccount(), PaymentAccountFactory.getPaymentAccount(advancedCash));

        PaymentMethod transferwise = PaymentMethod.TRANSFERWISE;
        Assert.assertEquals(new TransferwiseAccount(), PaymentAccountFactory.getPaymentAccount(transferwise));

        PaymentMethod amazonGiftCard = PaymentMethod.AMAZON_GIFT_CARD;
        Assert.assertEquals(new AmazonGiftCardAccount(), PaymentAccountFactory.getPaymentAccount(amazonGiftCard));

        PaymentMethod blockChainsInstant = PaymentMethod.BLOCK_CHAINS_INSTANT;
        Assert.assertEquals(new InstantCryptoCurrencyAccount(), PaymentAccountFactory.getPaymentAccount(blockChainsInstant));

    }
}
