package example.filedownload.pub;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class DownloadTask extends AsyncTask<Void, Integer, Long> {
    
    public final static int ERROR_NONE = 0;
    public final static int ERROR_SD_NO_MEMORY = 1;
    public final static int ERROR_BLOCK_INTERNET = 2;
    public final static int TIME_OUT = 30000;
    private final static int BUFFER_SIZE = 1024 * 4;
    
    private Throwable exception;
    private RandomAccessFile outputStream;
    private String url;
    private URL	 URL;
    private File file;
    private DownloadTaskListener listener;
    private long downloadSize;
    private long previousFileSize;
    private long totalSize;
    private long downloadPercent;
    private long networkSpeed;		// 网速
    private long previousTime;
    private long totalTime;
    private int errStausCode = ERROR_NONE;
    private boolean interrupt = false;
    
    private final class ProgressReportingRandomAccessFile extends RandomAccessFile {
	private int progress = 0;
	public ProgressReportingRandomAccessFile(File file, String mode)
		throws FileNotFoundException {
	    super(file, mode);
	}
	
	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
	    super.write(buffer, offset, count);
	    progress += count;		
	    publishProgress(progress);
	}
    }
    
    public DownloadTask(String url, String path, DownloadTaskListener listener) throws MalformedURLException {
	super();
	this.url = url;
	this.URL = new URL(url);
	this.listener = listener;
	String fileName = new File(URL.getFile()).getName();
	this.file = new File(path, fileName);
    }

    public String getUrl() {
	return url;
    }
    
    public long getDownloadPercent() {
	return downloadPercent;
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
    
    public long getTotalTime() {
	return this.totalTime;
    }
    
    @Override
    protected void onPreExecute() {
	listener.preDownload();
    }

    @Override
    protected Long doInBackground(Void... params) {
	try {
	    previousTime = System.currentTimeMillis();
		return download();
	    } catch (Exception e) {
	      exception = e;
	      return null;
	    }
	  }

	  @Override
	  protected void onProgressUpdate(Integer... progress) {
	    if (progress.length > 1) {
	      totalSize = progress[1];
	      if (totalSize == -1) {
	        
	      } else {
	        
	      }
	    } else {
		downloadSize = progress[0];
		downloadPercent = (downloadSize + previousFileSize)* 100 / totalSize;
		totalTime = System.currentTimeMillis() - previousTime;
		networkSpeed = downloadSize / totalTime;	
//		Log.v(null, ""+ downloadPercent + " " + totalSize + " " + downloadSize);
		listener.updateProcess(this);
	    }
	  }

	  @Override
	  protected void onPostExecute(Long result) {
	    if (interrupt) {
		if (errStausCode != ERROR_NONE) {
		    listener.errorDownload(errStausCode);
		}
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

	  private long download() throws Exception {	      
//	    URLConnection connection = null;
//	    try {
//	      connection = URL.openConnection();
//	    } catch (IOException e) {
//		Log.v(null, "Cannot open URL: " + url, e);
//	    }
//
//	    totalSize = connection.getContentLength();
		
//	      HttpClient client = new DefaultHttpClient();
	      AndroidHttpClient client = AndroidHttpClient.newInstance("DownloadTask");
	      HttpGet httpGet = new HttpGet(url);  
	      HttpResponse response = client.execute(httpGet);   
	      
	      HttpEntity entity = response.getEntity();

	      totalSize = entity.getContentLength();
	      
	      
	    if (file.exists() && totalSize == file.length()) {
		Log.v(null, "Output file already exists. Skipping download.");
		return 0l;
	    } else if (file.exists() && totalSize > file.length() && file.length() > 0) {	
//		connection = URL.openConnection();
//		connection.setRequestProperty("Range", "bytes="+ file.length() + "-" + totalSize);
//		previousFileSize = file.length();
		client.close();
		client = AndroidHttpClient.newInstance("DownloadTask");
//		client = new DefaultHttpClient();
		httpGet = new HttpGet(url);  
		httpGet.addHeader("Range", "bytes="+ file.length() + "-" + totalSize);
		response = client.execute(httpGet);   		      
		entity = response.getEntity();
		previousFileSize = file.length();     

		Log.v(null, "File is not complete, download now.");
		Log.v(null, "File length:" + file.length() + " totalSize:" + totalSize);
	    }
	    
	    long storage = getAvailableStorage();
	    Log.i(null, "storage:" + storage + " totalSize:" + totalSize);
	    if (totalSize - file.length() > storage) {
		errStausCode = ERROR_SD_NO_MEMORY;
		interrupt = true;
		return 0l;
	    }
	    
	    try {
		outputStream = new ProgressReportingRandomAccessFile(file, "rw");
	    } catch (FileNotFoundException e) {
		Log.v(null, "OutputStream Error");
	    }

	    publishProgress(0, (int)totalSize);

//	    int bytesCopied = copy(connection.getInputStream(), outputStream);
	    int bytesCopied = copy(entity.getContent(), outputStream);
	    
	    if (bytesCopied != totalSize && totalSize != -1) {
	      throw new IOException("Download incomplete: " + bytesCopied + " != " + totalSize);
	    }
	    outputStream.close();
	    client.close();
	    Log.v(null, "Download completed successfully.");
	    return bytesCopied;
	  }
	  
	  public int copy(InputStream input, RandomAccessFile out) throws Exception, IOException {
	      byte[] buffer = new byte[BUFFER_SIZE];

	      BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
	      Log.v(null, "length" + out.length());
	      out.seek(out.length());
	      
	      int count = 0, n = 0;
	      long errorBlockTimePreviousTime = -1, expireTime = 0;
	      try {
	        while (!interrupt) {
	          n = in.read(buffer, 0, BUFFER_SIZE);
	          if (n == -1) {
	              break;
	          }
	          
	          out.write(buffer, 0, n);
	          
	          count += n;
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
	        }
	      } finally {
	        try {
	          out.close();
	        } catch (IOException e) {
	          Log.e(null,e.getMessage(), e);
	        }
	        try {
	            in.close();
	        } catch (IOException e) {
	          Log.e(null,e.getMessage(), e);
	        }
	      }
	      return count;
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
	}
