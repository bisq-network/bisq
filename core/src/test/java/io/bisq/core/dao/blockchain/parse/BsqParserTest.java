package io.bisq.core.dao.blockchain.parse;

import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.core.dao.blockchain.vo.*;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(JMockit.class)
public class BsqParserTest {
    @Tested(availableDuringSetup = true)
    BsqBlockChain bsqBlockChain;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    BsqParser bsqParser;

    @Injectable
    PersistenceProtoResolver persistenceProtoResolver;
    @Injectable
    File storageDir;

    @Injectable
    RpcService rpcService;
    @Injectable
    OpReturnVerification opReturnVerification;
    @Injectable
    IssuanceVerification issuanceVerification;

    @Test
    public void testIsBsqTx() {
        // Setup a basic transaction with two inputs
        int height = 200;
        String hash = "abc123";
        long time = new Date().getTime();
        List<TxInput> inputs = new ArrayList<TxInput>();
        inputs.add(new TxInput("tx1", 0));
        inputs.add(new TxInput("tx1", 1));
        List<TxOutput> outputs = new ArrayList<TxOutput>();
        outputs.add(new TxOutput(0, 101, "tx1", null, null, null, height));
        TxVo txVo = new TxVo("vo", height, hash, time);
        Tx tx = new Tx(txVo, inputs, outputs);

        // Return one spendable txoutputs with value, for three test cases
        // 1) - null, 0     -> not BSQ transaction
        // 2) - 100, null   -> BSQ transaction
        // 3) - 0, 100      -> BSQ transaction
        new Expectations(bsqBlockChain) {{
            bsqBlockChain.getSpendableTxOutput(new TxIdIndexTuple("tx1", 0));
            result = Optional.empty();
            result = Optional.of(new TxOutput(0, 100, "txout1", null, null, null, height));
            result = Optional.of(new TxOutput(0, 0, "txout1", null, null, null, height));

            bsqBlockChain.getSpendableTxOutput(new TxIdIndexTuple("tx1", 1));
            result = Optional.of(new TxOutput(0, 0, "txout2", null, null, null, height));
            result = Optional.empty();
            result = Optional.of(new TxOutput(0, 100, "txout2", null, null, null, height));
        }};

        // First time there is no BSQ value to spend so it's not a bsq transaction
        assertFalse(bsqParser.isBsqTx(height, tx));
        // Second time there is BSQ in the first txout
        assertTrue(bsqParser.isBsqTx(height, tx));
        // Third time there is BSQ in the second txout
        assertTrue(bsqParser.isBsqTx(height, tx));
    }
}
