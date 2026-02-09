package bisq.core.dao.node.full.rpc;

import bisq.wallets.bitcoind.rpc.BitcoindDaemon;
import bisq.wallets.bitcoind.rpc.responses.BitcoindGetBlockResponse;
import bisq.wallets.bitcoind.rpc.responses.BitcoindGetNetworkInfoResponse;
import bisq.wallets.bitcoind.rpc.responses.BitcoindTransaction;
import bisq.wallets.json_rpc.JsonRpcClient;
import bisq.wallets.json_rpc.RpcClientFactory;
import bisq.wallets.json_rpc.RpcConfig;

public class BitcoindClient {
    private final BitcoindDaemon daemon;

    public BitcoindClient(RpcConfig rpcConfig) {
        JsonRpcClient rpcClient = RpcClientFactory.createDaemonRpcClient(rpcConfig);
        this.daemon = new BitcoindDaemon(rpcClient);
    }

    public String getBestBlockHash() {
        return daemon.getBestBlockHash();
    }

    public String getBlockHash(int height) {
        return daemon.getBlockHash(height);
    }

    public BitcoindGetBlockResponse.Result<String> getBlockVerbosityOne(String blockHash) {
        return daemon.getBlockVerbosityOne(blockHash);
    }

    public BitcoindGetBlockResponse.Result<BitcoindTransaction> getBlockVerbosityTwo(String blockHash) {
        return daemon.getBlockVerbosityTwo(blockHash);
    }

    public int getBlockCount() {
        return daemon.getBlockCount();
    }

    public BitcoindGetNetworkInfoResponse.Result getNetworkInfo() {
        return daemon.getNetworkInfo().getResult();
    }
}
