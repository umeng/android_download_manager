package example.filedownload.pub;

public interface DownloadTaskListener {
    public void updateProcess(long progress);			// 更新进度
    public void finishDownload(long progress);			// 完成下载
    public void preDownload();					// 准备下载
    public void errorDownload(int error);				// 下载错误
}
