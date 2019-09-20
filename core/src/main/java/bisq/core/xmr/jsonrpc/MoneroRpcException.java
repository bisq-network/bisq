package bisq.core.xmr.jsonrpc;

/**
 * Exception when interacting with the Monero daemon or wallet RPC API.
 */
public class MoneroRpcException extends MoneroException {

  private static final long serialVersionUID = -6282368684634114151L;
  
  private String rpcMethod;
  private Object rpcParams;
  
  public MoneroRpcException(String rpcDescription, Integer rpcCode, String rpcMethod, Object rpcParams) {
    super(rpcDescription, rpcCode);
    this.rpcMethod = rpcMethod;
    this.rpcParams = rpcParams;
  }

  public String getRpcMethod() {
    return rpcMethod;
  }

  public Object getRpcParams() {
    return rpcParams;
  }
  
  public String toString() {
    String str = super.toString();
    str += "\nRPC request: '" + rpcMethod + "' with params: " + JsonUtils.serialize(rpcParams);
    return str;
  }
}
