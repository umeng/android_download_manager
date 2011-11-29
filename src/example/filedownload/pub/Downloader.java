package example.filedownload.pub;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class Downloader extends AsyncTask<Void, Integer, Long> {
    public final static String TAG = "Downloader";
    public final static int ERROR_NONE = 0;
    public final static int ERROR_SD_NO_MEMORY = 1;
    public final static int ERROR_BLOCK_INTERNET = 2;
    private final static int TIME_OUT = 30000;
    public static int THREADNUM = 1;
    public static int TEST = 0;
    private URL	 URL;
    private File file;
    private String url;
    private DownloaderListener listener;
    private Throwable exception;
    private SharedPreferences settings;
    
    private long downloadSize;
    private long previousFileSize;
    private long totalSize;
    private long downloadPercent;
    private long networkSpeed;		// 网速
    private long previousTime;
    private long totalTime;
    private long[] startPos = 	new long [THREADNUM];
    private long[] endPos = 	new long [THREADNUM];
    private long[] subSize = new long [THREADNUM];
//    private DownloadTask[] task = new DownloadTask[THREADNUM];
    private DownloadThread[] task = new DownloadThread[THREADNUM];
    private boolean interrupt = false;
    private int errStausCode = ERROR_NONE;
	
    public Downloader(
	    String in,
	    String out,
	    Context context) throws MalformedURLException {
	this(in,out,context,null);
    }
    
    public Downloader(
	    String in, 
	    String out, 
	    Context context,
	    DownloaderListener listener) throws MalformedURLException {
	this.listener = listener;
	this.url = in;
	this.URL = new URL(url);
	String fileName = new File(URL.getFile()).getName();
	this.file = new File(out, fileName);
	
	settings = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
    }
    
    public String getUrl() {
	return url;
    }
    
    public long getDownloadPercent() {
	return downloadPercent;
    }
    
    public long getTotalTime() {
	return totalTime;
    }
    
    public long getDownloadSize() {
	return downloadSize + previousFileSize;
    }
    
    public long getTotalSize() {
	return totalSize;
    }
    
    public long getDownloadSpeed() {
	return this.networkSpeed;
    }
    
    private void saveDownloadRecord() {
	Editor edit = settings.edit();
	for(int i = 0; i <THREADNUM; i++) {
	    edit.putLong(getMD5Str(url + i),subSize[i] + task[i].getDownloadSize());
	    Log.i(null, "save record" + i + ":" + task[i].getDownloadSize());
	}
	edit.commit();
	Log.i(null, "get record: " + getMD5Str(url + 0) + settings.getLong(getMD5Str(url + 0), 0));
	
    }
    
    public void clearDownloadRecord() {
	Editor edit = settings.edit();
	for(int  i = 0; i < THREADNUM; i ++) {
	    edit.remove(getMD5Str(url + i));
	}
	edit.commit();
    }
    @Override
    protected Long doInBackground(Void... params) {
	try {
	    previousTime = System.currentTimeMillis();
	    download();
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	} catch (IOException e) {
	    // delete file
	    e.printStackTrace();
	}
	return null;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
	if (values.length > 1) {
	    totalSize = values[1];
	    if (totalSize == -1) {
		listener.errorDownload(ERROR_BLOCK_INTERNET);
	    } else {
		clearDownloadRecord();
		listener.updateProcess(this);
	    }
	} else {
	    this.downloadSize = values[0];
	    this.downloadPercent = (downloadSize + previousFileSize)* 100 / totalSize;
	    this.totalTime = (System.currentTimeMillis() - previousTime);
	    this.networkSpeed = downloadSize / totalTime;
	    listener.updateProcess(this);
	}

    }

    private void download() throws IOException {
	
	    URLConnection connection = null;
	    try {
	      connection = URL.openConnection();
	    } catch (IOException e) {
		Log.v(null, "Cannot open URL: " + url, e);
	    }
	    
	    totalSize = connection.getContentLength();
	    if (totalSize == -1) {
		errStausCode = ERROR_BLOCK_INTERNET;
		interrupt = true;
		return;
	    }
	    
	    if (totalSize > getAvailableStorage()) {
		errStausCode = ERROR_SD_NO_MEMORY;
		interrupt = true;
		return;
	    }
		
	    RandomAccessFile raf = new RandomAccessFile(file, "rw");
	    raf.setLength(totalSize);
	    raf.close();
	    
	    publishProgress(0, (int)totalSize);
	    
	    long block = totalSize / THREADNUM;
	    
	    for(int i = 0; i < THREADNUM; i++) {
		startPos[i] = block * i + subSize[i];
		endPos[i] = (i + 1) % THREADNUM * block + (i + 1) / THREADNUM * totalSize  - 1;
//		task[i] = new DownloadTask(URL, file, startPos[i],endPos[i]);
//		task[i].execute();
		task[i] = new DownloadThread(URL, file, startPos[i],endPos[i]);
		task[i].start();
	    }
	    
	    int step = 0;
	    long errorBlockTimePreviousTime = -1;
	    long expireTime = 0;
		
	    while(previousFileSize + downloadSize < totalSize && !interrupt) {
		if (step % 100000 == 0) {
		    step = 0;
			downloadSize = 0;
			for(int  i = 0; i < THREADNUM; i++) {
			    downloadSize += task[i].getDownloadSize();
			}
			publishProgress((int)downloadSize);
		}
		
		if (networkSpeed == 0) {
			if (errorBlockTimePreviousTime > 0) {
			    expireTime = System.currentTimeMillis() - errorBlockTimePreviousTime;
			    if (expireTime > TIME_OUT) {
				errStausCode = ERROR_BLOCK_INTERNET;
				interrupt = true;
			    }
			}
			else {
			    errorBlockTimePreviousTime = System.currentTimeMillis();
			}
		    }
		    else {
			    expireTime = 0;
			    errorBlockTimePreviousTime = -1;
		    }
		step ++;
	    }
	    
	    if (!interrupt && downloadSize != totalSize && totalSize != -1) {
		Log.v(null, "Download incomplete: " + downloadSize + " != " + totalSize);
		throw new IOException("Download incomplete: " + downloadSize + " != " + totalSize);
	    }
	    Log.v(null, "Download completed successfully. downloadSize: " + downloadSize);
    }
    
    @Override
    protected void onPreExecute() {
	    for(int i = 0; i < THREADNUM; i++) {
		subSize[i] = settings.getLong(getMD5Str(url + i), 0);
		previousFileSize += subSize[i];
		Log.v(null, "record subsize:" + subSize[i]);
	    }
    }
    
    @Override
    protected void onPostExecute(Long result) {
	    if (interrupt) {
		if (errStausCode != ERROR_NONE) {
		    listener.errorDownload(errStausCode);
		}
		for(int i = 0; i < THREADNUM; i++) {
		    task[i].onCancelled();
		}
		saveDownloadRecord();
	      return;
	    }
	    
	    if (exception != null) {
		Log.v(null, "Download failed.", exception);
	    }
	    
	    listener.finishDownload(this);
    }

    @Override
    public void onCancelled() {
	super.onCancelled();
	interrupt = true;
    }
    
    
    
	  /*
	   * 获取 SD 卡内存
	   */
	  public static long getAvailableStorage() {		
	      String storageDirectory = null;		
	      storageDirectory = Environment.getExternalStorageDirectory().toString();
			
	      Log.v(null, "getAvailableStorage. storageDirectory : " + storageDirectory);
			
	      try {
		  StatFs stat = new StatFs(storageDirectory);
		  long avaliableSize = ((long) stat.getAvailableBlocks() * (long) stat.getBlockSize());
		  Log.v(null, "getAvailableStorage. avaliableSize : " + avaliableSize);
		  return avaliableSize;
	      } catch (RuntimeException ex) {
		  Log.e(null, "getAvailableStorage - exception. return 0");
		  return 0;
		}
	  }
	  
		/*
		 * MD5加密
		 */
		private static String getMD5Str(String str) {  
		    MessageDigest msgDigit = null;
		    try {
			    msgDigit = MessageDigest.getInstance("MD5");
			    msgDigit.reset();
			    msgDigit.update(str.getBytes("UTF-8"));
		    } catch (NoSuchAlgorithmException e) {
			System.out.println("NoSuchAlgorithmException caught!");     
			System.exit(-1);     
		    } catch (UnsupportedEncodingException e) {     
			e.printStackTrace();     
		    } 
		    
		    byte[] byteArray = msgDigit.digest();   
		    StringBuffer buf = new StringBuffer();  
		    for (int i = 0; i < byteArray.length; i++) {
			if (Integer.toHexString(0xFF & byteArray[i]).length() == 1) {
			    buf.append("0").append(Integer.toHexString(0xFF & byteArray[i]));
			}
			else {
			    buf.append(Integer.toHexString(0xFF & byteArray[i]));
			}
		    }
		    //16位加密，从第9位到25位
		    return buf.substring(8,24).toString().toUpperCase();
		}	  
}
