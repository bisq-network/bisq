package io.bitsquare.btc;

import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.script.ScriptOpCodes;

import static com.google.bitcoin.script.ScriptOpCodes.OP_RETURN;

public class ScriptUtil
{
    public static Script getOpReturnScript()
    {
        return new ScriptBuilder().op(OP_RETURN).build();
    }

    public static Script getOpReturnScriptWithData(byte[] data)
    {
        return new ScriptBuilder().op(OP_RETURN).data(data).build();
    }

    public static boolean isOpReturnScript(TransactionOutput transactionOutput)
    {
        return transactionOutput.getScriptPubKey().getChunks().get(0).equalsOpCode(ScriptOpCodes.OP_RETURN);
    }
}
