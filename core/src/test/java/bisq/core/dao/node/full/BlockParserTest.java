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

package bisq.core.dao.node.full;

// not converting this test because it is already ignored.
// Intro to jmockit can be found at http://jmockit.github.io/tutorial/Mocking.html
//@Ignore
/*
public class BlockParserTest {
    // @Tested classes are instantiated automatically when needed in a test case,
    // using injection where possible, see http://jmockit.github.io/tutorial/Mocking.html#tested
    // To force instantiate earlier, use availableDuringSetup
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    BlockParser blockParser;

    @Tested(fullyInitialized = true, availableDuringSetup = true)
    DaoStateService daoStateService;

    // @Injectable are mocked resources used to for injecting into @Tested classes
    // The naming of these resources doesn't matter, any resource that fits will be used for injection

    // Used by daoStateService
    @Injectable
    PersistenceProtoResolver persistenceProtoResolver;
    @Injectable
    File storageDir;
    @Injectable
    String genesisTxId = "genesisTxId";
    @Injectable
    int genesisBlockHeight = 200;

    // Used by fullNodeParser
    @Injectable
    RpcService rpcService;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    DaoStateService writeModel;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    TxParser txParser;

    //FIXME
    @Test
    public void testIsBsqTx() {
        // Setup a basic transaction with two inputs
        int height = 200;
        String hash = "abc123";
        long time = new Date().getTime();
        final List<TxInput> inputs = asList(new TxInput("tx1", 0, null),
                new TxInput("tx1", 1, null));
        final List<RawTxOutput> outputs = asList(new RawTxOutput(0, 101, "tx1", null, null, null, height));
        RawTx rawTx = new RawTx("vo", height, hash, time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(outputs));

        // Return one spendable txoutputs with value, for three test cases
        // 1) - null, 0     -> not BSQ transaction
        // 2) - 100, null   -> BSQ transaction
        // 3) - 0, 100      -> BSQ transaction
        new Expectations(daoStateService) {{
            // Expectations can be recorded on mocked instances, either with specific matching arguments or catch all
            // http://jmockit.github.io/tutorial/Mocking.html#results
            // Results are returned in the order they're recorded, so in this case for the first call to
            // getSpendableTxOutput("tx1", 0) the return value will be Optional.empty()
            // for the second call the return is Optional.of(new TxOutput(0,... and so on
            daoStateService.getUnspentTxOutput(new TxOutputKey("tx1", 0));
            result = Optional.empty();
            result = Optional.of(new RawTxOutput(0, 100, "txout1", null, null, null, height));
            result = Optional.of(new RawTxOutput(0, 0, "txout1", null, null, null, height));

            daoStateService.getUnspentTxOutput(new TxOutputKey("tx1", 1));
            result = Optional.of(new RawTxOutput(0, 0, "txout2", null, null, null, height));
            result = Optional.empty();
            result = Optional.of(new RawTxOutput(0, 100, "txout2", null, null, null, height));
        }};
        String genesisTxId = "genesisTxId";
        int blockHeight = 200;
        String blockHash = "abc123";
        Coin genesisTotalSupply = Coin.parseCoin("2.5");

        // First time there is no BSQ value to spend so it's not a bsq transaction
        assertFalse(txParser.findTx(rawTx, genesisTxId, blockHeight, genesisTotalSupply).isPresent());
        // Second time there is BSQ in the first txout
        assertTrue(txParser.findTx(rawTx, genesisTxId, blockHeight, genesisTotalSupply).isPresent());
        // Third time there is BSQ in the second txout
        assertTrue(txParser.findTx(rawTx, genesisTxId, blockHeight, genesisTotalSupply).isPresent());
    }

    @Test
    public void testParseBlocks() {
        // Setup blocks to test, starting before genesis
        // Only the transactions related to bsq are relevant, no checks are done on correctness of blocks or other txs
        // so hashes and most other data don't matter
        long time = new Date().getTime();
        int genesisHeight = 200;
        int startHeight = 199;
        int headHeight = 201;
        Coin issuance = Coin.parseCoin("2.5");
        RawTransaction genTx = new RawTransaction("gen block hash", 0, 0L, 0L, genesisTxId);

        // Blockhashes
        String bh199 = "blockhash199";
        String bh200 = "blockhash200";
        String bh201 = "blockhash201";

        // Block 199
        String cbId199 = "cbid199";
        RawTransaction tx199 = new RawTransaction(bh199, 0, 0L, 0L, cbId199);
        RawTx cbTx199 = new RawTx(cbId199, 199, bh199, time,
                ImmutableList.copyOf(new ArrayList<TxInput>()),
                ImmutableList.copyOf(asList(new RawTxOutput(0, 25, cbId199, null, null, null, 199))));
        RawBlock block199 = new RawBlock(bh199, 10, 10, 199, 2, "root", asList(tx199), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", "previousBlockHash", bh200);

        // Genesis Block
        String cbId200 = "cbid200";
        RawTransaction tx200 = new RawTransaction(bh200, 0, 0L, 0L, cbId200);
        RawTx cbTx200 = new RawTx(cbId200, 200, bh200, time,
                ImmutableList.copyOf(new ArrayList<TxInput>()),
                ImmutableList.copyOf(asList(new RawTxOutput(0, 25, cbId200, null, null, null, 200))));
        RawTx genesisTx = new RawTx(genesisTxId, 200, bh200, time,
                ImmutableList.copyOf(asList(new TxInput("someoldtx", 0, null))),
                ImmutableList.copyOf(asList(new RawTxOutput(0, issuance.getValue(), genesisTxId, null, null, null, 200))));
        RawBlock block200 = new RawBlock(bh200, 10, 10, 200, 2, "root", asList(tx200, genTx), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", bh199, bh201);

        // Block 201
        // Make a bsq transaction
        String cbId201 = "cbid201";
        String bsqTx1Id = "bsqtx1";
        RawTransaction tx201 = new RawTransaction(bh201, 0, 0L, 0L, cbId201);
        RawTransaction txbsqtx1 = new RawTransaction(bh201, 0, 0L, 0L, bsqTx1Id);
        long bsqTx1Value1 = Coin.parseCoin("2.4").getValue();
        long bsqTx1Value2 = Coin.parseCoin("0.04").getValue();
        RawTx cbTx201 = new RawTx(cbId201, 201, bh201, time,
                ImmutableList.copyOf(new ArrayList<TxInput>()),
                ImmutableList.copyOf(asList(new RawTxOutput(0, 25, cbId201, null, null, null, 201))));
        RawTx bsqTx1 = new RawTx(bsqTx1Id, 201, bh201, time,
                ImmutableList.copyOf(asList(new TxInput(genesisTxId, 0, null))),
                ImmutableList.copyOf(asList(new RawTxOutput(0, bsqTx1Value1, bsqTx1Id, null, null, null, 201),
                        new RawTxOutput(1, bsqTx1Value2, bsqTx1Id, null, null, null, 201))));
        RawBlock block201 = new RawBlock(bh201, 10, 10, 201, 2, "root", asList(tx201, txbsqtx1), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", bh200, "nextBlockHash");

        // TODO update test with new API
        /*
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
            rpcService.requestTx(genesisTxId, genesisHeight);
            result = genesisTx;
            rpcService.requestTx(cbId201, 201);
            result = cbTx201;
            rpcService.requestTx(bsqTx1Id, 201);
            result = bsqTx1;
        }};

        // Running parseBlocks to build the bsq blockchain
        fullNodeParser.parseBlocks(startHeight, headHeight, block -> {
        });
*/

// Verify that the genesis tx has been added to the bsq blockchain with the correct issuance amount
    /*    assertTrue(daoStateService.getGenesisTx().get() == genesisTx);
        assertTrue(daoStateService.getGenesisTotalSupply().getValue() == issuance.getValue());

        // And that other txs are not added
        assertFalse(daoStateService.containsTx(cbId199));
        assertFalse(daoStateService.containsTx(cbId200));
        assertFalse(daoStateService.containsTx(cbId201));

        // But bsq txs are added
        assertTrue(daoStateService.containsTx(bsqTx1Id));
        TxOutput bsqOut1 = daoStateService.getUnspentAndMatureTxOutput(bsqTx1Id, 0).get();
        assertTrue(daoStateService.isUnspent(bsqOut1));
        assertTrue(bsqOut1.getValue() == bsqTx1Value1);
        TxOutput bsqOut2 = daoStateService.getUnspentAndMatureTxOutput(bsqTx1Id, 1).get();
        assertTrue(daoStateService.isUnspent(bsqOut2));
        assertTrue(bsqOut2.getValue() == bsqTx1Value2);
        assertFalse(daoStateService.isTxOutputSpendable(genesisTxId, 0));
        assertTrue(daoStateService.isTxOutputSpendable(bsqTx1Id, 0));
        assertTrue(daoStateService.isTxOutputSpendable(bsqTx1Id, 1));

    }
            }
            */
