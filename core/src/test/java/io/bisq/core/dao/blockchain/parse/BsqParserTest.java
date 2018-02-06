package io.bisq.core.dao.blockchain.parse;

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.Block;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.exceptions.BsqBlockchainException;
import io.bisq.core.dao.blockchain.vo.*;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import org.bitcoinj.core.Coin;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

import static java.util.Arrays.asList;
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
    String manualBsqGenesisId;

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
        List<TxInput> inputs = new ArrayList<>();
        inputs.add(new TxInput("tx1", 0));
        inputs.add(new TxInput("tx1", 1));
        List<TxOutput> outputs = new ArrayList<>();
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

    @Test
    public void testParseBlocks() throws BitcoindException, CommunicationException, BlockNotConnectingException, BsqBlockchainException {
        // Setup blocks to test, starting before genesis
        // Only the transactions related to bsq are relevant, no checks are done on correctness of blocks or other txs
        // so hashes and most other data don't matter
        long time = new Date().getTime();
        int genesisHeight = 200;
        int startHeight = 199;
        int headHeight = 201;
        Coin issuance = Coin.parseCoin("25");

        // Blockhashes
        String bh199 = "blockhash199";
        String bh200 = "blockhash200";
        String bh201 = "blockhash201";

        // Block 199
        String cbId199 = "cbid199";
        Tx cbTx199 = new Tx(new TxVo(cbId199, 199, bh199, time),
                new ArrayList<TxInput>(),
                asList(new TxOutput(0, 25, cbId199, null, null, null, 199)));
        Block block199 = new Block(bh199, 10, 10, 199, 2, "root", asList(cbId199), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", "previousBlockHash", bh200);

        // Genesis Block
        String cbId200 = "cbid200";
        String genesisId = "genesisId";
        Tx cbTx200 = new Tx(new TxVo(cbId200, 200, bh200, time),
                new ArrayList<TxInput>(),
                asList(new TxOutput(0, 25, cbId200, null, null, null, 200)));
        Tx genesisTx = new Tx(new TxVo(genesisId, 200, bh200, time),
                asList(new TxInput("someoldtx", 0)),
                asList(new TxOutput(0, issuance.getValue(), genesisId, null, null, null, 200)));
        Block block200 = new Block(bh200, 10, 10, 200, 2, "root", asList(cbId200, genesisId), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", bh199, bh201);

        // Block 201
        // Make a bsq transaction
        String cbId201 = "cbid201";
        String bsqTx1Id = "bsqtx1";
        long bsqTx1Value1 = Coin.parseCoin("24").getValue();
        long bsqTx1Value2 = Coin.parseCoin("0.4").getValue();
        Tx cbTx201 = new Tx(new TxVo(cbId201, 201, bh201, time),
                new ArrayList<TxInput>(),
                asList(new TxOutput(0, 25, cbId201, null, null, null, 201)));
        Tx bsqTx1 = new Tx(new TxVo(bsqTx1Id, 201, bh201, time),
                asList(new TxInput(genesisId, 0)),
                asList(new TxOutput(0, bsqTx1Value1, bsqTx1Id, null, null, null, 201),
                        new TxOutput(1, bsqTx1Value2, bsqTx1Id, null, null, null, 201)));
        Block block201 = new Block(bh201, 10, 10, 201, 2, "root", asList(cbId201, bsqTx1Id), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", bh200, "nextBlockHash");

        new Expectations(rpcService) {{
            rpcService.requestBlock(199);
            result = block199;
            rpcService.requestBlock(200);
            result = block200;
            rpcService.requestBlock(201);
            result = block201;

            rpcService.requestTx(cbId199, 199);
            result = cbTx199;
            rpcService.requestTx(cbId200, genesisHeight);
            result = cbTx200;
            rpcService.requestTx(genesisId, genesisHeight);
            result = genesisTx;
            rpcService.requestTx(cbId201, 201);
            result = cbTx201;
            rpcService.requestTx(bsqTx1Id, 201);
            result = bsqTx1;
        }};

        // Running parseBlocks to build the bsq blockchain
        bsqParser.parseBlocks(startHeight, headHeight, genesisHeight, genesisId, block -> {
        });

        // Verify that the the genesis tx has been added to the bsq blockchain with the correct issuance amount
        assertTrue(bsqBlockChain.getGenesisTx() == genesisTx);
        assertTrue(bsqBlockChain.getIssuedAmount().getValue() == issuance.getValue());

        // And that other txs are not added
        assertFalse(bsqBlockChain.containsTx(cbId199));
        assertFalse(bsqBlockChain.containsTx(cbId200));
        assertFalse(bsqBlockChain.containsTx(cbId201));

        // But bsq txs are added
        assertTrue(bsqBlockChain.containsTx(bsqTx1Id));
        TxOutput bsqOut1 = bsqBlockChain.getSpendableTxOutput(bsqTx1Id, 0).get();
        assertTrue(bsqOut1.isUnspent());
        assertTrue(bsqOut1.getValue() == bsqTx1Value1);
        TxOutput bsqOut2 = bsqBlockChain.getSpendableTxOutput(bsqTx1Id, 1).get();
        assertTrue(bsqOut2.isUnspent());
        assertTrue(bsqOut2.getValue() == bsqTx1Value2);
        assertFalse(bsqBlockChain.isTxOutputSpendable(genesisId, 0));
        assertTrue(bsqBlockChain.isTxOutputSpendable(bsqTx1Id, 0));
        assertTrue(bsqBlockChain.isTxOutputSpendable(bsqTx1Id, 1));
    }
}
