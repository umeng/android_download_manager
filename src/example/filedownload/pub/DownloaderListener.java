package example.filedownload.pub;

public interface DownloaderListener {
    public void updateProcess(Downloader dl);			// 更新进度
    public void finishDownload(Downloader dl);			// 完成下载
    public void preDownload();					// 准备下载
    public void errorDownload(int error);				// 下载错误
}
