package io.bitsquare.btc;

import java.util.Date;

public interface DownloadListener
{
    void progress(double percent, int blocksSoFar, Date date);

    void doneDownload();
}
