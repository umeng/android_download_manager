package example.filedownload.pub;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import android.os.AsyncTask;
import android.util.Log;

//package example.filedownload.pub;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.io.RandomAccessFile;
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.net.URLConnection;
//
//import android.os.AsyncTask;
//import android.os.Environment;
//import android.os.StatFs;
//import android.util.Log;
//
//public class DownloadTask extends AsyncTask<Void, Integer, Long> {
//    
//    public final static int ERROR_NONE = 0;
//    public final static int ERROR_SD_NO_MEMORY = 1;
//    public final static int ERROR_BLOCK_INTERNET = 2;
//    
//    private final static int BUFFER_SIZE = 1024 * 8;
//    
//    private Throwable exception;
//    private RandomAccessFile outputStream;
//    private String url;
//    private URL	 URL;
//    private File file;
//    private DownloadTaskListener listener;
//    private long downloadSize;
//    private long previousFileSize;
//    private long totalSize;
//    private long downloadPercent;
//    private long networkSpeed;		// 网速
//    private long previousTime;
//    private int errStausCode = ERROR_NONE;
//    private boolean interrupt = false;
//    
//    private final class ProgressReportingRandomAccessFile extends RandomAccessFile {
//	private int progress = 0;
//	
//	public ProgressReportingRandomAccessFile(File file, String mode)
//		throws FileNotFoundException {
//	    super(file, mode);
//	}
//	
//	@Override
//	public void write(byte[] buffer, int offset, int count) throws IOException {
//	    super.write(buffer, offset, count);
//	    progress += count;		
//	    publishProgress(progress);
//	}
//    }
//    
//    public DownloadTask(String url, String path, DownloadTaskListener listener) throws MalformedURLException {
//	super();
//	this.url = url;
//	this.URL = new URL(url);
//	this.listener = listener;
//	String fileName = new File(URL.getFile()).getName();
//	this.file = new File(path, fileName);
//    }
//
//    public String getUrl() {
//	return url;
//    }
//    
//    public long getDownloadPercent() {
//	return downloadPercent;
//    }
//    
//    public long getDownloadSize() {
//	return downloadSize + previousFileSize;
//    }
//    
//    public long getTotalSize() {
//	return totalSize;
//    }
//    
//    public long getDownloadSpeed() {
//	return this.networkSpeed;
//    }
//    
//    @Override
//    protected void onPreExecute() {
//
//    }
//
//    @Override
//    protected Long doInBackground(Void... params) {
//	try {
//	      previousTime = System.currentTimeMillis();
//	      return download();
//	    } catch (Exception e) {
//	      exception = e;
//	      return null;
//	    }
//	  }
//
//	  @Override
//	  protected void onProgressUpdate(Integer... progress) {
//	    if (progress.length > 1) {
//	      totalSize = progress[1];
//	      if (totalSize == -1) {
//	        listener.errorDownload(ERROR_BLOCK_INTERNET);
//	      } else {
//		listener.updateProcess(this);
//	      }
//	    } else {
//		this.downloadSize = progress[0];
//		this.downloadPercent = (downloadSize + previousFileSize)* 100 / totalSize;
//		this.networkSpeed = downloadSize / (System.currentTimeMillis() - previousTime);
//		Log.v(null, ""+ downloadPercent + " " + totalSize + " " + downloadSize);
//		listener.updateProcess(this);
//	    }
//	  }
//
//	  @Override
//	  protected void onPostExecute(Long result) {
//	    if (interrupt) {
//		if (errStausCode != ERROR_NONE) {
//		    listener.errorDownload(errStausCode);
//		}
//	      return;
//	    }
//	    
//	    if (exception != null) {
//		Log.v(null, "Download failed.", exception);
//	    }
//	    
//	    listener.finishDownload(this);
//	  }
//
//	  @Override
//	  public void onCancelled() {
//	      super.onCancelled();
//	      interrupt = true;
//	  }
//
//	  private long download() throws Exception {
//	    URLConnection connection = null;
//	    try {
//	      connection = URL.openConnection();
//	    } catch (IOException e) {
//		Log.v(null, "Cannot open URL: " + url, e);
//	    }
//
//	    totalSize = connection.getContentLength();
//		
//	    if (file.exists() && totalSize == file.length()) {
//		Log.v(null, "Output file already exists. Skipping download.");
//		return 0l;
//	    } else if (file.exists() && totalSize > file.length() && file.length() > 0) {
//		connection = URL.openConnection();
//		connection.setRequestProperty("Range", "bytes="+ file.length() + "-" + totalSize);
//		previousFileSize = file.length();
//		Log.v(null, "File is not complete, download now.");
//		Log.v(null, "File length:" + file.length() + " totalSize:" + totalSize);
//	    }
//	    
//	    long storage = getAvailableStorage();
//	    Log.i(null, "storage:" + storage + " totalSize:" + totalSize);
//	    if (totalSize - file.length() > storage) {
//		errStausCode = ERROR_SD_NO_MEMORY;
//		interrupt = true;
//		return 0l;
//	    }
//	    
//	    try {
//		outputStream = new ProgressReportingRandomAccessFile(file, "rw");
//	    } catch (FileNotFoundException e) {
//		Log.v(null, "OutputStream Error");
//	    }
//
//	    publishProgress(0, (int)totalSize);
//
//	    int bytesCopied = copy(connection.getInputStream(), outputStream);
//	    
//	    if (bytesCopied != totalSize && totalSize != -1) {
//	      throw new IOException("Download incomplete: " + bytesCopied + " != " + totalSize);
//	    }
//	    outputStream.close();
//	    Log.v(null, "Download completed successfully.");
//	    return bytesCopied;
//	  }
//	  
//	  public int copy(InputStream input, RandomAccessFile out) throws Exception, IOException {
//	      byte[] buffer = new byte[BUFFER_SIZE];
//
//	      BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
//	      Log.v(null, "length" + out.length());
//	      out.seek(out.length());
//	      
//	      int count = 0, n = 0;
//	      try {
//	        while ((n = in.read(buffer, 0, BUFFER_SIZE)) != -1 && !interrupt) {
//	          out.write(buffer, 0, n);
//	          count += n;
//	        }
//	      } finally {
//	        try {
//	          out.close();
//	        } catch (IOException e) {
//	          Log.e(null,e.getMessage(), e);
//	        }
//	        try {
//	          in.close();
//	        } catch (IOException e) {
//	          Log.e(null,e.getMessage(), e);
//	        }
//	      }
//	      return count;
//	    }
//	  
//	  public int copy(InputStream input, OutputStream output) throws Exception, IOException {
//	      byte[] buffer = new byte[BUFFER_SIZE];
//
//	      BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
//	      BufferedOutputStream out = new BufferedOutputStream(output, BUFFER_SIZE);
//	      int count = 0, n = 0;
//	      try {
//	        while ((n = in.read(buffer, 0, BUFFER_SIZE)) != -1 && !interrupt) {
//	          out.write(buffer, 0, n);
//	          count += n;
//	        }
//	        out.flush();
//	      } finally {
//	        try {
//	          out.close();
//	        } catch (IOException e) {
//	          Log.e(null,e.getMessage(), e);
//	        }
//	        try {
//	          in.close();
//	        } catch (IOException e) {
//	          Log.e(null,e.getMessage(), e);
//	        }
//	      }
//	      return count;
//	    }
//	  
//	  /*
//	   * 获取 SD 卡内存
//	   */
//	  public static long getAvailableStorage() {		
//	      String storageDirectory = null;		
//	      storageDirectory = Environment.getExternalStorageDirectory().toString();
//			
//	      Log.v(null, "getAvailableStorage. storageDirectory : " + storageDirectory);
//			
//	      try {
//		  StatFs stat = new StatFs(storageDirectory);
//		  long avaliableSize = ((long) stat.getAvailableBlocks() * (long) stat.getBlockSize());
//		  Log.v(null, "getAvailableStorage. avaliableSize : " + avaliableSize);
//		  return avaliableSize;
//	      } catch (RuntimeException ex) {
//		  Log.e(null, "getAvailableStorage - exception. return 0");
//		  return 0;
//		}
//	  }
//	}


    public class DownloadTask extends AsyncTask<Void, Integer, Long> {  
	private final static int BUFFER_SIZE = 1024 * 8;
	private Throwable exception;
	private URL URL;
	private File file;
	private ProgressReportingRaf outputStream;
	private long startPos;
	private long endPos;
	private long subDownloadSize;
	private long subTotalSize;
	private boolean interrupt = false;
	    private final class ProgressReportingRaf extends RandomAccessFile {
		private int progress = 0;

		public ProgressReportingRaf(File file)
			throws FileNotFoundException {
			super(file, "rw");
		}

		@Override
		public void write(byte[] buffer, int byteOffset, int byteCount) throws IOException {
			super.write(buffer, byteOffset, byteCount);
			progress += byteCount;
			publishProgress(progress);
		    }
		}
	    
	public DownloadTask(URL URL, File file, long start, long end) throws MalformedURLException {
	    super();
	    this.startPos = start;
	    this.endPos = end;
	    this.URL = URL;
	    this.file = file;
	}    
	
	public synchronized long getDownloadSize() {
	    return subDownloadSize;
	}
	
	    @Override
	    protected void onPreExecute() {

	    }

	    @Override
	    protected Long doInBackground(Void... params) {
		try {
		      return download();
		    } catch (Exception e) {
		      exception = e;
		      return null;
		    }
		  }

		  @Override
		  protected void onProgressUpdate(Integer... progress) {
		    if (progress.length > 1) {
		      this.subTotalSize = progress[1];
		      if (subTotalSize == -1) {

		      } else {

		      }
		    } else {
			this.subDownloadSize = progress[0];

		    }
		  }

		  @Override
		  protected void onPostExecute(Long result) {
		    if (interrupt) {

		      return;
		    }
		    
		    if (exception != null) {
			Log.v(null, "Download failed.", exception);
		    }
		    
		  }

		  @Override
		  public void onCancelled() {
		      super.onCancelled();
		      interrupt = true;
		  }

		  private long download() throws Exception {
		    URLConnection connection = null;
		    try {
		      connection = URL.openConnection();
		    } catch (IOException e) {
			Log.v(null, "Cannot open URL", e);
		    }
		    
		    connection.setRequestProperty("Range", "bytes="+ startPos + "-" + endPos);
		    subTotalSize = connection.getContentLength();
		    
		    try {
			outputStream = new ProgressReportingRaf(file);
		    } catch (FileNotFoundException e) {
			Log.v(null, "OutputStream Error");
		    }

		    publishProgress(0, (int)subTotalSize);

		    int bytesCopied = copy(connection.getInputStream(), outputStream);
		    
		    if (!interrupt && bytesCopied != subTotalSize && subTotalSize != -1) {
		      throw new IOException("Download incomplete: " + bytesCopied + " != " + subTotalSize);
		    }
		    outputStream.close();

		    return bytesCopied;
		  }
		  
		  public int copy(InputStream input, RandomAccessFile out) throws Exception, IOException {
		      byte[] buffer = new byte[BUFFER_SIZE];

		      BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
		      Log.v(null, "length" + out.length());
		      out.seek(startPos);
		      
		      int count = 0, n = 0;
		      try {
		        while ((n = in.read(buffer, 0, BUFFER_SIZE)) != -1 && !interrupt) {
		          out.write(buffer, 0, n);
		          count += n;
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
		}

