package io.bitsquare.btc;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Wallet;

import java.math.BigInteger;
import java.util.Set;

public class WalletUtil
{

    // TODO check if that is correct and safe
    public static int getConfDepthInBlocks(Wallet wallet)
    {
        Transaction transaction = WalletUtil.getTransaction(wallet);
        if (transaction != null && transaction.getConfidence() != null)
        {
            if (transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                return wallet.getLastBlockSeenHeight() - transaction.getConfidence().getAppearedAtChainHeight() + 1;
            else
                return 0;
        }
        else
        {
            return 0;
        }
    }

    // TODO check if that is correct and safe
    public static Transaction getTransaction(Wallet wallet)
    {
        Set<Transaction> transactions = wallet.getTransactions(true);
        if (transactions != null)
        {
            for (Transaction transaction : transactions)
            {
                if (transaction.getValueSentFromMe(wallet).compareTo(BigInteger.ZERO) == 0)
                    return transaction;
            }
        }
        return null;
    }
}
