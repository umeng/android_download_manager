package example.filedownload.pub;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class DownloadTask extends AsyncTask<Void, Integer, Long> {
    
    public final static int ERROR_NONE = 0;
    public final static int ERROR_SD_NO_MEMORY = 1;
    public final static int ERROR_BLOCK_INTERNET = 2;
    public final static int ERROR_UNKONW = 3;
    public final static int TIME_OUT = 30000;
    private final static int BUFFER_SIZE = 1024 * 8;
    
    private URL	 URL;
    private File file;
    private String url;
    private Throwable exception;
    private RandomAccessFile outputStream;
    private DownloadTaskListener listener;
    private Context context;
    private AndroidHttpClient client = null;
    
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
    
    public DownloadTask(Context context,
	    String url, String path) throws MalformedURLException {
	this(context, url, path, null);
    }
    
    public DownloadTask(Context context,
	    String url, String path, DownloadTaskListener listener) throws MalformedURLException {
	super();
	this.url = url;
	this.URL = new URL(url);
	this.listener = listener;
	String fileName = new File(URL.getFile()).getName();
	this.file = new File(path, fileName);
	this.context = context;
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
	previousTime = System.currentTimeMillis();
	if (listener != null)
	listener.preDownload();
    }

    @Override
    protected Long doInBackground(Void... params) {
	try {
		return download();
	    } catch (Exception e) {
		if (client != null) {
		    client.close();
		}
	      exception = e;
	      errStausCode = ERROR_UNKONW;
	      return null;
	    }
	  }

	  @Override
	  protected void onProgressUpdate(Integer... progress) {
	    if (progress.length > 1) {
	      totalSize = progress[1];
	      if (totalSize == -1) {
		  if (listener != null)
		  listener.errorDownload(ERROR_UNKONW);
	      } else {
		  
	      }
	    } else {
		totalTime = System.currentTimeMillis() - previousTime;
		downloadSize = progress[0];
		downloadPercent = (downloadSize + previousFileSize)* 100 / totalSize;
		networkSpeed = downloadSize / totalTime;	
		if (listener != null)
		listener.updateProcess(this);
	    }
	  }

	  @Override
	  protected void onPostExecute(Long result) {
	    if (interrupt) {
		if (errStausCode != ERROR_NONE) {
		    if (listener != null)
		    listener.errorDownload(errStausCode);
		}
		
	        return;
	    }
	    
	    if (exception != null) {
		Log.v(null, "Download failed.", exception);
	    }
	    if (listener != null)
	    listener.finishDownload(this);
	  }

	  @Override
	  public void onCancelled() {
	      super.onCancelled();
	      interrupt = true;
	  }

	  private long download() throws Exception {	        
	      Log.v(null, "totalSize: " + totalSize);
	      
	      client = AndroidHttpClient.newInstance("DownloadTask");
	      HttpGet httpGet = new HttpGet(url);  
	      HttpResponse response = client.execute(httpGet);             
	      totalSize = response.getEntity().getContentLength();
	      
	      if (file.length() > 0 && totalSize > 0 && totalSize > file.length()) {
		  httpGet.addHeader("Range", "bytes="+ file.length() + "-");
		  previousFileSize = file.length();

		  client.close();
		  client = AndroidHttpClient.newInstance("DownloadTask");
		  response = client.execute(httpGet); 
		  Log.v(null, "File is not complete, download now.");
		  Log.v(null, "File length:" + file.length() + " totalSize:" + totalSize);
	      }	            	      
	      else if (file.exists() && totalSize == file.length()) {
		Log.v(null, "Output file already exists. Skipping download.");
		return 0l;
	      }
            
	      
	      long storage = getAvailableStorage();
	      Log.i(null, "storage:" + storage + " totalSize:" + totalSize);
	      if (totalSize - file.length() > storage) {
		errStausCode = ERROR_SD_NO_MEMORY;
		interrupt = true;
		client.close();
		return 0l;
	      }
	    
	      try {
		outputStream = new ProgressReportingRandomAccessFile(file, "rw");
	      } catch (FileNotFoundException e) {
		Log.v(null, "OutputStream Error");
	      }

	      publishProgress(0, (int)totalSize);
	    
	      InputStream input = null;
	      try {
		  input  = response.getEntity().getContent();
	      } catch (IOException ex) {
		  errStausCode = ERROR_UNKONW;	
		  client.close();
		  Log.v(null, "InputStream Error" + ex.getMessage());
		  return 0;
	      }
	        
	      int bytesCopied = copy(input, outputStream);
	    
	      if ((previousFileSize + bytesCopied) != totalSize && totalSize != -1 && !interrupt) {
	      	throw new IOException("Download incomplete: " + bytesCopied + " != " + totalSize);
	      }
	      
	      outputStream.close();
	      client.close();
	      client = null;
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
	          if (!isOnline()) {
	              interrupt = true;
	              errStausCode = ERROR_BLOCK_INTERNET;
	              break;
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
	        }
	      } finally {
	        try {
	          out.close();
	        } catch (IOException e) {
	          errStausCode = ERROR_UNKONW;
	          Log.e(null,e.getMessage(), e);
	        }
	        try {
	          in.close();
	        } catch (IOException e) {
	          errStausCode = ERROR_UNKONW;
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
	  
	    private boolean isOnline() 
	    {
	    	try
	    	{
		    	ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
		    							Context.CONNECTIVITY_SERVICE);
		    	NetworkInfo ni = cm.getActiveNetworkInfo();
		    	return ni!=null ? ni.isConnectedOrConnecting() : false; 
	    	}
	    	catch(Exception e)
	    	{
	    		e.printStackTrace();
	    		return false;
	    	}
	    }	  
	}
