package io.bitsquare.msg;

public interface BootstrapListener
{
    public void onCompleted();

    public void onFailed(Throwable throwable);

}
