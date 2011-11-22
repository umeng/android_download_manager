package example.filedownload.pub;

public interface DownloadTaskListener {
    public void startDownload(String url);		// 开始下载
    
    public void updateProcess(String url, String process);	// 更新进度
    
    public void finishDownload(String url);		// 完成下载
}
