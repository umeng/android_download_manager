package example.filedownload.pub;

public interface DownloadListener {
    public void updateProcess(DownloadMgr mgr);			// 更新进度
    public void finishDownload(DownloadMgr mgr);			// 完成下载
<<<<<<< HEAD
    public void preDownload();					// 准备下载
    public void errorDownload(int error);				// 下载错误上
=======
    public void preDownload();
    public void errorDownload(int error);
>>>>>>> 3db5e1bee1cbd17a3a51bed4bbe7fe5872c390b1
}
