package mpi.v23;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocalActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ViewSwitcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import android.util.Log;

class Rooted {
	static String TAG="Rooted";
	static public void exec(String[] cmds) {
		try {
			// String[] cmds = {"sync", "sync",  "reboot"};
			Process p = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(p.getOutputStream()); 
			DataInputStream is = new DataInputStream(p.getInputStream());  
			for (String tmpCmd : cmds) {
				os.writeBytes(tmpCmd+"\n");

			}           
			os.writeBytes("exit\n");  
			os.flush();
		}
		catch (Exception e) { 
		}
	}

	static public void exec(String cmd) {
		String[] cmds = new String[1];
		cmds[0] = cmd;
		exec(cmds);
	}
	static public boolean isRooted() {
		String buildTags = android.os.Build.TAGS;
		String test = android.os.Build.MODEL;
		if (test != null && (test.contains("Kindle") || test.contains("Nook") || test.contains("Blue"))) {
			return true;
		}
		return false;
	}

}
public class AMI extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		m_context = getApplicationContext();
		m_Q = new LinkedList<PkgInfo>();
		registerForPackageChanges();
		getInstallerClass();
		new CopyAssets().execute();
	}

	private void registerForPackageChanges() {
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.intent.action.PACKAGE_INSTALL");
		filter.addAction("android.intent.action.PACKAGE_ADDED");
		filter.addAction("android.intent.action.PACKAGE_CHANGED");
		filter.addAction("android.intent.action.PACKAGE_REMOVED");
		filter.addAction("android.intent.action.PACKAGE_REPLACED");
		filter.addCategory("android.intent.action.DEFAULT");
		filter.addDataScheme("package");

		registerReceiver( new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				//do something based on the intent's action
				if (intent.getAction().equalsIgnoreCase("android.intent.action.PACKAGE_ADDED")) {
					verifyAPKInstall(intent.getDataString());	

				}}}, filter);
	}

	private void verifyAPKInstall(String pkg) {
		PkgInfo pi = m_Q.peek();
		if (pi == null) {
			return;
		}
		Log.d(TAG,"Install Request:"+pi.pkgname+" Intent Data:"+pkg);
		if (pkg.contains(pi.pkgname)) {
			pi = m_Q.pop();
			installNextAPK();
		}
	}
	private void installAPK(String filename, int maxTries) {
		PkgInfo pi = new PkgInfo(filename, maxTries);
		ApkReader a = new ApkReader();

		a.init(filename);

		pi.pkgname = a.getPackage();

		m_Q.add(pi);


	}

	private void installNextAPK(){
		PkgInfo pi = m_Q.peek();
		if (pi == null) {
			// bring us to the front
			Intent intent = new Intent(this, AMI.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );//| Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
			startActivity(intent);

			// restart the machine
			new AlertDialog.Builder(AMI.this)
			.setTitle("Attempting to Re-Initialize")
			.setMessage("Press OK to Continue")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {reboot();}
			})
			.setNegativeButton("", null)
			.show();		 
			return;
		}
		Log.d(TAG,pi.apkfile+" Try#:" + pi.numtries+" Max:"+pi.maxtries);
		fireIntent(pi);
	}

	private void getInstallerClass() {
		Intent intent = new Intent();
		intent.addCategory("android.intent.category.DEFAULT");
		//intent.setType("application/vnd.android.package-archive");
		intent.setDataAndType(Uri.parse("file:///dummy"),"application/vnd.android.package-archive");


		PackageManager packageManager = getApplicationContext().getPackageManager();
		// final Intent intent = new Intent(action);
		List resolveInfo =
				packageManager.queryIntentActivities(intent,0);
		if ((resolveInfo != null) && (resolveInfo.size() > 0)) {

			for (Object l: resolveInfo){
				ResolveInfo ri = (ResolveInfo)l;
				Log.d("FILTEROUT",ri.activityInfo.packageName +":"+ri.activityInfo.name);
				if (ri.activityInfo.packageName.contains("package")) {
					m_installerPkg = ri.activityInfo.packageName;
					m_installerActivity = ri.activityInfo.name;
				}

			}
		}

	}

	private void fireIntent(PkgInfo pi) {
		pi.lastInstall = SystemClock.uptimeMillis();
		pi.waiting = 1;
		pi.numtries++;

		if (Rooted.isRooted()) {
			Rooted.exec("pm install -r "+pi.apkfile);
		} else {
			Intent intent= new Intent();


			if (m_installerPkg != null)
				intent.setClassName(m_installerPkg, m_installerActivity);
			else
				intent.setClassName("com.android.packageinstaller","com.android.packageinstaller.PackageInstallerActivity");
			intent.addCategory("android.intent.category.DEFAULT");
			intent.setDataAndType(Uri.fromFile(new File(pi.apkfile)),
					"application/vnd.android.package-archive");
			//intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK );
			try {
				startActivity(intent);
			} catch (Exception e) {
				Log.d(TAG,"Installation Failed:"+e.toString());
			}
		}
	}
	private String copyAssetToFile(String asset){
		try {
			InputStream is = m_context.getAssets().open(asset);
			String PATH = Environment.getExternalStorageDirectory()
					+ "/ami/";
			File file = new File(PATH);
			file.mkdirs();
			String filename = asset.hashCode()+".apk";
			File outputFile = new File(file, filename);

			FileOutputStream fos = new FileOutputStream(outputFile);

			byte[] buffer = new byte[2048];
			int len1 = 0;
			while ((len1 = is.read(buffer)) != -1) {
				fos.write(buffer, 0, len1);
			}
			fos.close();
			is.close();
			// sdcard in download file
			return PATH + filename;
		} catch (Exception e) {
			Log.d(TAG,e.toString());
			return null;
		}
	}

	private void reboot() {
		try {
			String[] cmds = {"sync", "sync",  "reboot -f"};
			Process p = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(p.getOutputStream());            
			for (String tmpCmd : cmds) {
				os.writeBytes(tmpCmd+"\n");
			}           
			os.writeBytes("exit\n");  
			os.flush();
		}
		catch (Exception e) { 
			Log.d("XXX","reboot failed");
		}
	}
	private class CopyAssets extends AsyncTask<String, Integer, Long> {
		protected void onPreExecute( ){	
			m_pd = new ProgressDialog(AMI.this);
			m_pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

			m_pd.setMax(m_apklist.length);
			m_pd.setTitle("Copying Files ....");
			m_pd.show();
		}

		protected Long doInBackground(String... apklist) {
			int count = m_apklist.length;
			int i = 0;

			for (String f:m_apklist) {
			  	Log.d(TAG, "copying:" + f);
				String tmp = copyAssetToFile(f);
				if (tmp == null)
					continue;
				Log.d(TAG, "added to install queue: " + tmp);
				installAPK(tmp,1);
				publishProgress(i++);
			}


			return new Long(i);
		}

		protected void onProgressUpdate(Integer... progress) {
			m_pd.setTitle(m_apklist[progress[0]].replace(".apk", ""));
			m_pd.incrementProgressBy(1);
		}

		protected void onPostExecute(Long result) {
			// showDialog("Downloaded " + result + " bytes");
			m_pd.dismiss();
			installNextAPK();
		}
	}


	// --------------------------------------------------------------------
	String TAG = "AndroidMarketInstallerActivity";
	private LinkedList<PkgInfo> m_Q;
	Context m_context;
	ProgressDialog m_pd;
	static String[] m_apklist = {
		"GoogleServicesFramework.apk",
		"OneTimeInitializer.apk",
		"Vending.apk",
	};
	String m_installerPkg, m_installerActivity;
	ViewSwitcher m_switcher;

}
