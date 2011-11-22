package example.filedownload.pub;

public interface DownloadListener {
    public void updateProcess(DownloadMgr mgr);			// 更新进度
    public void finishDownload(DownloadMgr mgr);			// 完成下载
}
