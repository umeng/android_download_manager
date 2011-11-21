package example.filedownload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * AsyncTask + HttpClient (多线程断点)
 * @author SuetMing
 *
 */

public class FileDownloadActivity extends ListActivity {
    /** Called when the activity is first created. */
    private static final String TAG = "FileDownloadActivity";
    private static final int MSG_START_DOWNLOAD 	= 1;
    private static final int MSG_STOP_DOWNLOAD 	= 2;
    private static final int MSG_PAUSE_DOWNLOAD 	= 3;
    private static final int MSG_CONTINUE_DOWNLOAD 	= 4;
    private static final int MSG_INSTALL_APK	 	= 5;
    
    private DownloadFileAsync[] tasks;
    private ListAdapter adapter;
    private Handler handler = new Handler() {
	public void handleMessage(Message msg) { 
	    switch (msg.what) { 
	    case MSG_START_DOWNLOAD:
		startDownload(msg.arg1);
		break;
	    case MSG_STOP_DOWNLOAD:
		stopDownload(msg.arg1);
		break;
	    case MSG_PAUSE_DOWNLOAD:
		pauseDownload(msg.arg1);
		break;
	    case MSG_CONTINUE_DOWNLOAD:
		continueDownload(msg.arg1);
		break;
	    case MSG_INSTALL_APK:
		Button btnStart = (Button)adapter.viewList.get(msg.arg1).findViewById(R.id.btn_start);
		Button btnPause = (Button)adapter.viewList.get(msg.arg1).findViewById(R.id.btn_pause);
		Button btnStop = (Button)adapter.viewList.get(msg.arg1).findViewById(R.id.btn_stop);
		Button btnContinue = (Button)adapter.viewList.get(msg.arg1).findViewById(R.id.btn_continue);

	        btnStart.setVisibility(0);
	        btnPause.setVisibility(8);
	        btnStop.setVisibility(8);
	        btnContinue.setVisibility(8);
		installAPK(msg.arg1);
		break;
	    }
	}
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        adapter = new ListAdapter(this);
        setListAdapter(adapter);
        
        tasks = new DownloadFileAsync[Utils.url.length];
    }
    
    public void startDownload(int viewPos) {
	    if (!Utils.isSDCardPresent()) {
		Toast.makeText(this, "未发现SD卡", Toast.LENGTH_LONG);
		return;
	    }
	    
	    if (!Utils.isSdCardWrittenable()) {
		Toast.makeText(this, "SD卡不能读写", Toast.LENGTH_LONG);
		return;
	    }
	    
	    if (tasks[viewPos] != null) {
		tasks[viewPos].onCancelled();
		tasks[viewPos] = null;
	    }
	    File file = new File(Utils.APK_ROOT + Utils.getFileNameFromUrl(Utils.url[viewPos]));
	    if (file.exists()) file.delete();			
	    tasks[viewPos] = new DownloadFileAsync(viewPos);
	    tasks[viewPos].execute(Utils.url[viewPos]);
    }
    
    public void pauseDownload(int viewPos) {
	    if (tasks[viewPos] != null) {
		tasks[viewPos].onCancelled();
	    }
	    
	    tasks[viewPos] = null;
    }
    
    public void stopDownload(int viewPos) {
	    File file = new File(Utils.APK_ROOT + Utils.getFileNameFromUrl(Utils.url[viewPos]));
	    if (file.exists()) file.delete();
	    
	    if (tasks[viewPos] != null) {
		tasks[viewPos].onCancelled();
	    }
	    
	    tasks[viewPos] = null;
    }
    
    public void continueDownload(int viewPos) {
	    if (tasks[viewPos] == null) {
		tasks[viewPos] = new DownloadFileAsync(viewPos);
		tasks[viewPos].execute(Utils.url[viewPos]);	 
	    }	
    }
    
    public void installAPK(int viewPos) {
	if (tasks[viewPos] != null) {
	    tasks[viewPos] = null;
	}
//	Utils.installAPK(FileDownloadActivity.this, Utils.url[viewPos]);
//	View convertView = adapter.viewList.get(viewPos);
//	ImageView view = (ImageView) convertView.findViewById(R.id.imageView);
//        
//	Bitmap bitmap = Utils.getLoacalBitmap(Utils.APK_ROOT + Utils.getFileNameFromUrl(Utils.url[viewPos]));
//	view.setImageBitmap(bitmap);
	
	Intent intent = new Intent(FileDownloadActivity.this, ImageActivity.class);
	intent.putExtra("url", viewPos);
	startActivity(intent);
    }

    
    class DownloadFileAsync extends AsyncTask<String, String, String> {
	private int taskId;		// 下载任务ID
        private int threadNum = 5;
        private long totalSize = -1;
        private URL	url;
        private String urlStr;		
        private String fileName;	// 下载文件名
        private String fileDir;		// 下载目录
        
        private boolean interrupt = false;
        
        
        private List<DownloadThread> threadList = new ArrayList<DownloadThread>();
        
        public DownloadFileAsync(int id) {
	    this.taskId = id;
	}
	
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            interrupt = true;          
        }
        
        @Override
        protected String doInBackground(String... urls) {
            try {
        	Log.i(TAG,"doInBackground");
        	for(String url : urls) {
        	    downloadFile(url);
        	}
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
        
        private void downloadFile(String url) throws ClientProtocolException, IOException, InterruptedException {
        	Log.i(TAG,"downloadFile");
        	this.urlStr = url;
    	    	this.fileDir = Utils.APK_ROOT;
    	    	this.fileName = Utils.getFileNameFromUrl(urlStr);
    	    	this.url = new URL(url);
        	long downloadSize = 0;
        	
        	HttpURLConnection con = null;
        	// 打开URL连接
        	con = (HttpURLConnection) this.url.openConnection();
        	
        	// 设置Http Header
		setHttpHeader(con);
		
		totalSize = con.getContentLength();
		
		// 检查是否SD卡够用
		long storage = Utils.getAvailableStorage();
		Log.i(TAG, "storage:" + storage);
		if (totalSize > storage) {
		    Toast.makeText(FileDownloadActivity.this, "SD 卡内存不足", Toast.LENGTH_LONG);
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
//		    interrupt = true;
		    downloadFile(url);
		    return;
		}
		
		long blockSize = totalSize / threadNum;
		 
		// 开启分段下载线程
		for(int i = 0; i < threadNum; i++) {
		    long startPos = blockSize * i;
		    long endPos = (((i + 1) / threadNum) == 1) ? totalSize - 1 : blockSize * (i + 1) - 1;
		    File file = new File(this.fileDir + this.fileName + ".part" + i);
		    if (file.exists()) {
			startPos += file.length();
			downloadSize += file.length();
		    }
			
		    DownloadThread thread = new DownloadThread(file, startPos, endPos,i);
		    threadList.add(thread);
		    if (startPos < (endPos + 1)) thread.start();
		}
					
		while(downloadSize < totalSize) {
		    Thread.sleep(1000);
		    downloadSize = 0;
		    if (interrupt) {
			for(int i = 0; i < threadNum; i++) {
			    downloadSize += threadList.get(i).getDownloadSize();
			    threadList.get(i).onCancel();
			}
			publishProgress(""+(int)((downloadSize*100)/totalSize));
			break;
		    } else {
			for(int i = 0; i < threadNum; i++) {
			    downloadSize += threadList.get(i).getDownloadSize();
			}
			publishProgress(""+(int)((downloadSize*100)/totalSize));
		    }
		    
		}
		if (!interrupt) { // 分段文件整合
		    File file = new File(this.fileDir + this.fileName);
		    if (file.exists()) file.delete();
		    FileOutputStream randomFile = new FileOutputStream(file, true);
			
		    Log.i(TAG,"downloadFile writFile");
		    for(int i = 0; i < threadNum; i++) {     			           			
		    byte b[] = new byte [4096];
    		    int j = 0;           			
    		    InputStream input = new FileInputStream(threadList.get(i).getFile());
    		    while((j = input.read(b)) > 0) {
    			randomFile.write(b, 0, j);			    
    		    }           			
    		    input.close();
    		    threadList.get(i).getFile().delete();
		    }
			
		    randomFile.close(); 
		}
 
    		Log.i(TAG,"downloadFile end");
        }
        
	protected void onProgressUpdate(String... progress) {
             Log.d("ANDRO_ASYNC",progress[0]);
             View convertView = adapter.getView(this.taskId, getListView(), null);
             ProgressBar pb = (ProgressBar)convertView.findViewById(R.id.progressBar);
             pb.setProgress(Integer.parseInt(progress[0]));
             Log.i(TAG,taskId + " " + progress[0]);
        }

        @Override
        protected void onPostExecute(String unused) {
            File file = new File(Utils.APK_ROOT + Utils.getFileNameFromUrl(urlStr));
            
            Log.i(TAG,"onPostExecute" + "totalSize: " + totalSize + "file.length():" + file.length());
            if (!interrupt && (totalSize > 0 && totalSize == file.length())) {
        	Message message = new Message();  
        	message.what = MSG_INSTALL_APK;
        	message.arg1 = taskId;
        	handler.sendMessage(message);
            }
            else if (!interrupt && (totalSize > 0 && totalSize < file.length())) {
        	Message message = new Message();  
//	        message.what = MSG_START_DOWNLOAD;  
        	message.what = MSG_START_DOWNLOAD;
	        message.arg1 = taskId;
	        handler.sendMessage(message);
            }
            else if (totalSize < 0){
        	Message message = new Message();  
	        message.what = MSG_START_DOWNLOAD;  
	        message.arg1 = taskId;
	        handler.sendMessage(message);
            }
            else if (interrupt) {
        	Log.i(TAG,"onPostExecute interrrupt true");
        	
            }
            
            Log.i(TAG,"onPostExecute end");
        }
        
        // 下载线程
        public class DownloadThread extends Thread {
            public final static int TIMEOUT = 30;
            private File file;
            private long startPos;
            private long endPos;
            private long downloadSize = -1;
            private InputStream in = null;
            private int id;
            public DownloadThread(File file, long startPos, long endPos, int id) {
            	this.file = file;
            	this.startPos = startPos;
            	this.endPos = endPos;
            	this.id = id;
            	downloadSize = this.file.length();
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
            
            public void setDownloadSize(long size) {
        	downloadSize = size;
            }
            
            public File getFile() {
        	return file;
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
    		    	RandomAccessFile randomFile = new RandomAccessFile(file, "rw");  
			randomFile.seek(randomFile.length());
			
			long len = con.getContentLength();
			Log.i(TAG,"Thread id:" + id + " len:" + len + " endPos - startPos + 1 :" + (endPos - startPos + 1));
			byte b[] = new byte [4096];
			int j = 0;
			long currentDownloadSize = 0;
			
			while((currentDownloadSize < (endPos - startPos + 1)) && 
			      !interrupt) {
			    Log.i(TAG,"Thread Read begin " + id + "endPos - startPos + 1 :" + (endPos - startPos + 1) + " currentDownloadSize : " + currentDownloadSize);
			    Log.i(TAG,"Thread id: " + id + " inputsteam:" + in.available());
			    if (in.available() > 0) {
				j = in.read(b);
				    
				currentDownloadSize += j;
				downloadSize += j;
				randomFile.write(b, 0, j);
				
			    } else {
				in.close();
				randomFile.close();
				con.disconnect();
				Thread.sleep(5000);
				downloadFile(file, startPos, endPos);
				break;
			    }
			    Thread.sleep(1000);
			    Log.i(TAG,"Thread id" + id + "read:" + j);
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
    
    private class ListAdapter extends BaseAdapter {
	private Context context;
	public List<View> viewList = new ArrayList<View>();
	
	public ListAdapter(Context context) {
	    this.context = context;
	}
	
	@Override
	public int getCount() {
	    return Utils.url.length;
	}

	@Override
	public Object getItem(int position) {
	    return position;
	}

	@Override
	public long getItemId(int position) {
	    return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
	    if (position < viewList.size()) {
		return viewList.get(position);
	    }
	    
	    if (convertView == null) {
		convertView = View.inflate(this.context, R.layout.list_item, null);
		viewList.add(convertView);
		
		Button btnStart = (Button)convertView.findViewById(R.id.btn_start);
		Button btnPause = (Button)convertView.findViewById(R.id.btn_pause);
		Button btnStop = (Button)convertView.findViewById(R.id.btn_stop);
		Button btnContinue = (Button)convertView.findViewById(R.id.btn_continue);

	        btnStart.setVisibility(0);
	        btnPause.setVisibility(8);
	        btnStop.setVisibility(8);
	        btnContinue.setVisibility(8);
		
		btnStart.setOnClickListener(new BtnListener(position));
		btnPause.setOnClickListener(new BtnListener(position));
		btnStop.setOnClickListener(new BtnListener(position));
		btnContinue.setOnClickListener(new BtnListener(position));		
	    }
	
	    return convertView;
	}
	
	private class BtnListener implements View.OnClickListener {
	    int viewPos;
	    public BtnListener(int pos) {
		this.viewPos = pos;
	    }
	    
	    @Override
	    public void onClick(View v) {
		Message message;
		switch(v.getId()) {
		case R.id.btn_continue:
		{
		    message = new Message();  
	            message.what = MSG_CONTINUE_DOWNLOAD;  
	            message.arg1 = viewPos;
	            handler.sendMessage(message);
	            
	            Button btnStart = (Button)viewList.get(viewPos).findViewById(R.id.btn_start);
	            Button btnPause = (Button)viewList.get(viewPos).findViewById(R.id.btn_pause);
	            Button btnStop = (Button)viewList.get(viewPos).findViewById(R.id.btn_stop);
	            Button btnContinue = (Button)viewList.get(viewPos).findViewById(R.id.btn_continue);

	            // 设置按钮控件的可见性  0 可见，4 不占位不可见 ，8  占位不可见
	            btnStart.setVisibility(8);
	            btnPause.setVisibility(0);
	            btnStop.setVisibility(8);
	            btnContinue.setVisibility(8);
		}

		    break;
		case R.id.btn_pause:
		{
		    message = new Message();  
	            message.what = MSG_PAUSE_DOWNLOAD;  
	            message.arg1 = viewPos;
	            handler.sendMessage(message);
	            
	            Button btnStart = (Button)viewList.get(viewPos).findViewById(R.id.btn_start);
	            Button btnPause = (Button)viewList.get(viewPos).findViewById(R.id.btn_pause);
	            Button btnStop = (Button)viewList.get(viewPos).findViewById(R.id.btn_stop);
	            Button btnContinue = (Button)viewList.get(viewPos).findViewById(R.id.btn_continue);

	            btnStart.setVisibility(8);
	            btnPause.setVisibility(8);
	            btnStop.setVisibility(8);
	            btnContinue.setVisibility(0);
		}	            
		    break;
		case R.id.btn_start:
		{
		    message = new Message();  
	            message.what = MSG_START_DOWNLOAD;  
	            message.arg1 = viewPos;
	            handler.sendMessage(message);
	            Button btnStart = (Button)viewList.get(viewPos).findViewById(R.id.btn_start);
	            Button btnPause = (Button)viewList.get(viewPos).findViewById(R.id.btn_pause);
	            Button btnStop = (Button)viewList.get(viewPos).findViewById(R.id.btn_stop);
	            Button btnContinue = (Button)viewList.get(viewPos).findViewById(R.id.btn_continue);

	            btnStart.setVisibility(8);
	            btnPause.setVisibility(0);
	            btnStop.setVisibility(8);
	            btnContinue.setVisibility(8);
		}
		    break;
		case R.id.btn_stop:
		{
		    message = new Message();  
	            message.what = MSG_STOP_DOWNLOAD;  
	            message.arg1 = viewPos;
	            handler.sendMessage(message);
	            Button btnStart = (Button)viewList.get(viewPos).findViewById(R.id.btn_start);
	            Button btnPause = (Button)viewList.get(viewPos).findViewById(R.id.btn_pause);
	            Button btnStop = (Button)viewList.get(viewPos).findViewById(R.id.btn_stop);
	            Button btnContinue = (Button)viewList.get(viewPos).findViewById(R.id.btn_continue);

	            btnStart.setVisibility(0);
	            btnPause.setVisibility(0);
	            btnStop.setVisibility(8);
	            btnContinue.setVisibility(8);
		}
		    break;
		}
	    } 
	}
	
    }
}