package example.filedownload.pub;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class DownloadMgr {
    public final static int ERROR_NONE = 0;
    public final static int ERROR_SD_NO_MEMORY = 1;
    public final static int ERROR_BLOCK_INTERNET = 2;
    
    private final static String TAG = "DownloadMgr";
    private final static int TIME_OUT = 30000;
    
    private String filePath;
    private DownloadTask task;
    private DownloadListener listener;
    private File file;
    private String url;
    private URL URL;
    private int errStausCode = ERROR_NONE;
    private SharedPreferences preference;
    
    public DownloadMgr(
	    String url, 
	    String filePath, SharedPreferences preference,
	    DownloadListener listener) throws MalformedURLException {
	this.url = url;
	this.URL = new URL(url);
	this.listener = listener;
	this.filePath = filePath;
	this.preference = preference;
	
	String fileName = new File(this.URL.getFile()).getName();
	this.file = new File(filePath, fileName);
	
	try {
	    task = new DownloadTask(url, filePath, listener);
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}
    }
    
    public void start() {
	try {
	    Log.i(TAG, "Continue download");
	    task = new DownloadTask(url, filePath, listener);
	    task.execute();
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}	
    }
    
    public void pause() {
	    if (task != null) {
		task.onCancelled();
	    }
	    task = null;
    }
    
    public long getDownloadPercent() {
	if (task != null)
	    return task.getDownloadPercent();
	else return 0;
    }
    
    public long getDownloadSize() {
	if (task != null)
	    return task.getDownloadSize();
	else return 0;
    }
    
    public long getTotalSize() {
	if (task != null)
	    return task.getTotalSize();
	else return 0;
    }
    
    public long getCurrentSpeed() {
	if (task != null)
	    return task.getDownloadSpeed();
	else return 0;
    }
    
    public String getUrl() {
	return url;
    }
    
    public void clearDownloadRecord() {
	if (task != null)
	    task.clearDownloadRecord();
    }
    
    private class DownloadTask extends AsyncTask<String, String, String> {
	    private final static String TAG = "DownloadFileAsync";
	    
	    private final int threadNum = 1;		// Thread Num
	    
	    private final int blockNum = 1;		// Block Num
	    
	    private ExecutorService executorService;
	    
	    private URL	 url;	
	    private String filePath;		// 路径
	    
	    private long totalSize = -1; 
	    private long networkSpeed;		// 网速
	    private long downloadSize;
	    private long downloadPercent;
	    
	    private long[][] startPos = 	new long [blockNum][threadNum];
	    private long[][] endPos = 		new long [blockNum][threadNum];
	    private long[][] subSize = 	new long [blockNum][threadNum];
	    
	    private boolean interrupt = false;
	    private DownloadListener listener = null;
	    private List<DownloadThread> threadList = new ArrayList<DownloadThread>();	    
		
	    public DownloadTask(
		    String url, 
		    String filePath, 
		    DownloadListener listener) throws MalformedURLException {
		this.listener = listener;
		
		this.url = new URL(url);
		this.filePath = filePath;
				
		executorService = Executors.newFixedThreadPool(threadNum);
	    }
		
	    public long getDownloadPercent() {
		return this.downloadPercent;
	    }
	    
	    public long getDownloadSpeed() {
		return this.networkSpeed;
	    }
	    
	    public long getTotalSize() {
		return this.totalSize;
	    }
	    
	    public long getDownloadSize() {
		return this.downloadSize;
	    }
	    
	    @Override
	    protected void onPreExecute() {
	        super.onPreExecute();
	        if (listener != null)
	        listener.preDownload();
	    }

	    @Override
	    public void onCancelled() {
	        super.onCancelled();
	        interrupt = true;    
	    }
	    
	    @Override
	    protected String doInBackground(String... urls) {
	        try {
	    		Log.i(TAG,"doInBackground");
	    		downloadFile();
		    } catch (ClientProtocolException e) {
			Log.i(TAG, e.getMessage());
			e.printStackTrace();
		    } catch (IOException e) {
			Log.i(TAG, e.getMessage());
			e.printStackTrace();
		    } catch (InterruptedException e) {
			Log.i(TAG, e.getMessage());
			e.printStackTrace();
		    }
		    return null;
	        
	    }
	    
	    private void setHttpHeader(HttpURLConnection con) {
			con.setAllowUserInteraction(true);
			con.setConnectTimeout(5000);
			con.setDoOutput(true);
			con.setReadTimeout(10000); 
			con.setRequestProperty("Accept", "image/gif, image/jpeg, image/pjpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
			con.setRequestProperty("Accept-Language", "zh-CN");
			con.setRequestProperty("Referer", url.toString()); 
			con.setRequestProperty("Charset", "UTF-8");
			try {
			    con.setRequestMethod("GET");
			} catch (ProtocolException e) {
			    
			    e.printStackTrace();
			}
	    }
	    
	    private void saveDownloadRecord() {
		Editor edit = preference.edit();
		// 键值初始化
		for(int i = 0; i < blockNum; i++) {
		    for(int j = 0; j < threadNum; j++) {
			if (threadList.size() > (i * threadNum + j))
			edit.putLong(getMD5Str(DownloadMgr.this.url + "i" + i + "j" + j), 
			threadList.get(i * threadNum + j).getDownloadSize());
		    }
		}
		edit.commit();
	    }
	    
	    private void clearDownloadRecord() {
		Editor edit = preference.edit();
		for(int i = 0; i < blockNum; i++) {
		    for(int j = 0; j < threadNum; j++) {
			edit.remove(getMD5Str(DownloadMgr.this.url + "i" + i + "j" + j));
		    }
		}
		
		edit.commit();
	    }
	    
	    private void downloadFile() throws ClientProtocolException, IOException, InterruptedException {
	    	Log.i(TAG,"downloadFile");
	    	
	    	long start = System.currentTimeMillis();
	    	long previousTime = start;
	    	long previousBytes = 0;
	    	HttpURLConnection con = null;
	    	// 打开URL连接
	    	con = (HttpURLConnection) this.url.openConnection();
	    	
	    	// 设置Http Header
	    	setHttpHeader(con);
			
		totalSize = con.getContentLength();
			
		// 检查是否SD卡够用
		long storage = DownloadMgr.getAvailableStorage();
		Log.i(TAG, "storage:" + storage);
		if (totalSize > storage) {
		    errStausCode = ERROR_SD_NO_MEMORY;
		    interrupt = true;
		    return;
		}
		
		con.disconnect();
		if (HttpStatus.SC_OK != con.getResponseCode()) {
		    Log.i(TAG,"ResponseCode: " + con.getResponseCode());
		    interrupt = true;
		    return;
		}
			
		if (totalSize < 0 && !interrupt) {
		    downloadFile();
		    return;
		}
				
		// TODO
		RandomAccessFile raf = new RandomAccessFile(file, "rw");
		raf.setLength(totalSize);
		raf.close();
		 
		// 开启分段下载线程	
		long totalBlockSize = totalSize / blockNum; 
		long remindTotalBlockSize = totalSize % blockNum;		
		for (int i = 0; i < blockNum; i++) {
		    long blockSize = ((i + 1) / blockNum * remindTotalBlockSize + totalBlockSize) / threadNum;
		    long remindBlockSize = ((i + 1) / blockNum * remindTotalBlockSize + totalBlockSize) % threadNum;
		    
		    long startBlockPos = totalBlockSize * i;
		    long endBlockPos = totalBlockSize * ((i + 1) % blockNum) + (totalSize - 1) * ((i + 1) / blockNum);
		    for(int j = 0; j < threadNum; j++) {
			subSize[i][j] = preference.getLong(getMD5Str(DownloadMgr.this.url + "i" + i + "j" + j), 0);
			if (j == (threadNum - 1)) {
			    if (i == (blockNum - 1)) {
				    startPos[i][j] = 
					    blockSize * j + 
					    subSize[i][j] + 
					    startBlockPos;
				    endPos[i][j] = endBlockPos;
			    }
			    else {
				    startPos[i][j] = 
					    blockSize * j + 
					    subSize[i][j] + 
					    startBlockPos;
				    endPos[i][j] = blockSize * (j + 1) + startBlockPos + remindBlockSize - 1;
			    }
			}
			else {
			    startPos[i][j] = 
				    blockSize * j + 
				    subSize[i][j] + 
				    startBlockPos;
			    endPos[i][j] = blockSize * (j + 1) + startBlockPos - 1;
			}
						
			DownloadThread thread = new DownloadThread(subSize[i][j], startPos[i][j], endPos[i][j],i * blockNum + j);
			threadList.add(thread);
			if (startPos[i][j] < (endPos[i][j] + 1)) {
			    executorService.execute(thread);
			}			
			Log.i(TAG, "DOWNLOADMGR: " + i+ " " + j + " STARTPOS:" + startPos[i][j] + " ENDPOS:" + endPos[i][j]);
		    }
		}

		long errorBlockTimePreviousTime = -1;
		long expireTime = 0;
		while(downloadSize < totalSize) {
		    Thread.sleep(1000);
		    downloadSize = 0;
		    if (interrupt) {
			for(int i = 0; i < threadList.size(); i++) {
			    downloadSize += threadList.get(i).getDownloadSize();
			    threadList.get(i).onCancel();
			}
			break;
		    } else {
			for(int i = 0; i < threadList.size(); i++) {
			    downloadSize += threadList.get(i).getDownloadSize();
			}	
		    }
		    
		    // 每 100 ms 刷新显示一次
		    if (System.currentTimeMillis() - previousTime > 100) {
			networkSpeed = (downloadSize - previousBytes) / (System.currentTimeMillis() - previousTime);
			previousTime = System.currentTimeMillis();
			previousBytes = downloadSize;
			
			downloadPercent = (int)((downloadSize*100)/totalSize);
			publishProgress(""+ downloadPercent);
			Log.i(TAG, "networkSpeed:" + networkSpeed + " kbps");
			
			if (networkSpeed == 0) {
			   if (errorBlockTimePreviousTime > 0) {
			       expireTime = System.currentTimeMillis() - errorBlockTimePreviousTime;
			       if (expireTime > 30000) {
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
		
		long end = System.currentTimeMillis();
		networkSpeed = downloadSize / (end - start);
		
		Log.i(TAG, "networkSpeed:" + networkSpeed + " kbps");

		Log.i(TAG,"downloadFile end");
	    }
	    
	    protected void onProgressUpdate(String... progress) {
	         Log.d("ANDRO_ASYNC",progress[0]);
	         if (listener != null)
	         listener.updateProcess(DownloadMgr.this);
	    }

	    @Override
	    protected void onPostExecute(String unused) {
	        File file = new File(this.filePath); 
	        
	        Log.i(TAG,"onPostExecute" + "totalSize: " + totalSize + 
	        	" file.length():" + file.length() + 
	        	" downloadSize: " + downloadSize);
	        if (!interrupt && (totalSize > 0 && totalSize <= downloadSize)) {
	            clearDownloadRecord();
	            Log.i(TAG,"finish download");
	            if (listener != null)
	    	    listener.finishDownload(DownloadMgr.this); 
	        }
	        else if (!interrupt && (totalSize > 0 && downloadSize < totalSize)) { // 下载文件校检
	            saveDownloadRecord();
		    DownloadMgr.this.start();
	        }
	        else if (totalSize < 0){
//	            saveDownloadRecord();
	            DownloadMgr.this.start();
	        }
	        else if (interrupt) {
	            saveDownloadRecord();
	            if (listener != null)
	            listener.errorDownload(errStausCode);
	    	    Log.i(TAG,"onPostExecute interrrupt true");	
	        }
	        Log.i(TAG,"onPostExecute end");
	    }
	    

	    // 下载线程
	    public class DownloadThread extends Thread {
	        private long startPos;
	        private long endPos;
	        private long downloadSize = -1;
	        private InputStream in = null;
	        private int id;
	        public DownloadThread(long downloadSize, long startPos, long endPos, int id) {
	        	this.startPos = startPos;
	        	this.endPos = endPos;
	        	this.id = id;
	        	this.downloadSize = downloadSize;
	        }
	       
	        public void onCancel() {
	    	interrupt = true;
	    		if (in != null) {
			    try {
				in.close();
			    } catch (IOException e) {
				Log.i(TAG, "Can not close inputstream");
				e.printStackTrace();
			    }
	    		}
	        }
	        
	        public long getDownloadSize() {
	    	return downloadSize;
	        }	        
	        	        
	        /*
	         * 分段下载
	         */
	        private void downloadFile(File file,
	        	long startPos, long endPos
	        	) throws ClientProtocolException, IOException, InterruptedException {
	            
	        	HttpURLConnection con = (HttpURLConnection) url.openConnection();  
	        	
	        	setHttpHeader(con);	        	
	        	
			con.setRequestProperty("Range", "bytes="+ startPos + "-" + endPos);
			con.connect();
			Log.i("Thread","ResponseCode: " + con.getResponseCode() + "thread ID: " + id);
			
			if (HttpStatus.SC_OK == con.getResponseCode() || 
			    HttpStatus.SC_PARTIAL_CONTENT == con.getResponseCode()) {
			    	in = con.getInputStream();		
//			    	RandomAccessFile randomFile = new RandomAccessFile(DownloadMgr.this.file, "rw"); 
//			    	randomFile.seek(startPos);
				FileOutputStream randomFile = new FileOutputStream(DownloadMgr.this.file);
				long len = con.getContentLength();
				Log.i(TAG,"Thread id:" + id + " len:" + len + " endPos - startPos + 1 :" + (endPos - startPos + 1));
				byte b[] = new byte [1024 * 8];
				int j = 0;
				long currentDownloadSize = 0;
				
				while((currentDownloadSize < len) && 
				      !interrupt) {
				    Log.i(TAG,"Thread Read begin " + id + "endPos - startPos + 1 :" + (endPos - startPos + 1) + " currentDownloadSize : " + currentDownloadSize + " len:" + len);
				    Log.i(TAG,"Thread id: " + id + " inputsteam:" + in.available());
				    if (in.available() > 0) {
					j = in.read(b);
					if (j < 0) {
					    Log.i(TAG,"downloadFile break" + id + " startPos:" + startPos + 
							" endPos:" + endPos);
					    break;
					}
					currentDownloadSize += j;
					downloadSize += j;
					randomFile.write(b, 0, j);
					
				    } else {
					in.close();
					randomFile.close();
					con.disconnect();
					Thread.sleep(5000);
					Log.i(TAG,"downloadFile again" + id + " startPos:" + startPos + 
						" endPos:" + endPos);
					downloadFile(file, startPos + currentDownloadSize, endPos);
					break;
				    }
//				    Thread.sleep(1000);		    
				}   
				Log.i(TAG,"Thread Read end " + id);
				randomFile.close();
				in.close();
				con.disconnect();
				Log.i("Thread","Read end ");
			}
			else {
			    interrupt = true;
			}
	        }
	        
	        @Override
	        public void run() {
	    	try {
			    downloadFile(file, startPos, endPos);
			} catch (ClientProtocolException e) {
			    e.printStackTrace();
			    Log.i("Thread","" + id + "err: " + e.getMessage());
			} catch (IOException e) {
			    Log.i("Thread","" + id + "err: " + e.getMessage());
			    e.printStackTrace();
			} catch (InterruptedException e) {
			    Log.i("Thread","" + id + "err: " + e.getMessage());
			    e.printStackTrace();
			}
	        }
	    }
	}

    	/*
    	 * 获取 SD 卡内存
    	 */
	public static long getAvailableStorage() {		

		String storageDirectory = null;		
		storageDirectory = Environment.getExternalStorageDirectory().toString();
		
		Log.d(TAG, "getAvailableStorage. storageDirectory : " + storageDirectory);
		
		try {
			StatFs stat = new StatFs(storageDirectory);
			long avaliableSize = ((long) stat.getAvailableBlocks() * (long) stat.getBlockSize());
			Log.d(TAG, "getAvailableStorage. avaliableSize : " + avaliableSize);
			return avaliableSize;
		} catch (RuntimeException ex) {
			Log.e(TAG, "getAvailableStorage - exception. return 0");
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
