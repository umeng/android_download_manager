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

import android.util.Log;

public class DownloadThread extends Thread {
	private final static int BUFFER_SIZE = 1024 * 8;
	private URL URL;
	private File file;
	private RandomAccessFile outputStream;
	private long startPos;
	private long endPos;
	private long subDownloadSize;
	private long subTotalSize;
	private boolean interrupt = false;
	
	public DownloadThread(URL URL, File file, long start, long end) throws MalformedURLException {
	    super();
	    this.startPos = start;
	    this.endPos = end;
	    this.URL = URL;
	    this.file = file;
	}    
	
	public synchronized long getDownloadSize() {
	    return subDownloadSize;
	}
	
	public synchronized void onCancelled() {
	      interrupt = true;
	}
	  
	@Override
	public void run() {
	    try {
		download();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
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
		outputStream = new RandomAccessFile(file, "rw");
	    } catch (FileNotFoundException e) {
		Log.v(null, "OutputStream Error");
	    }

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
		          subDownloadSize +=n;
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
