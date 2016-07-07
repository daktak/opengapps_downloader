package org.opengappsdownloader;

import android.app.DownloadManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by daktak on 4/26/16.
 */
public class Download extends Service {

    /** indicates how to behave if the service is killed */
    int mStartMode;
    /** interface for clients that bind */
    IBinder mBinder;
    /** indicates whether onRebind should be used */
    boolean mAllowRebind;
    private static final String LOGTAG = LogUtil
            .makeLogTag(Download.class);
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent.getStringExtra("url");
        int action = intent.getIntExtra("action",1);
        if (action == 1) {
            new ParseURL().execute(new String[]{url});
        } else if (action ==2 ){
            new ParseURLDownload().execute(new String[]{url});
        } else if (action == 3) {
            new downloadFirstThread().execute(new String[]{url});
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private class ParseURL extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {
            return parseUrl(strings[0]);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            String newS = s.substring(1,s.length()-1);
            List<String> array = Arrays.asList(newS.split(","));
            if (MainActivity.instance != null) {
                MainActivity.instance.setList(array);
            }
        }
    }

    public String parseUrl(String url) {
        Log.d(LOGTAG, "Fetch: "+url);
        ArrayList<String> urls = new ArrayList<String>();
        try {

            Document doc = Jsoup.connect(url).timeout(10*1000).get();
            SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String arch = mySharedPreferences.getString("prefArch","arm64");
            String android = mySharedPreferences.getString("prefAndroid","6.0");
            String variant = mySharedPreferences.getString("prefVariant","nano");
            String selector = "a[rel=nofollow][href$=.zip][href*=open_gapps-"+arch+"-"+android+"-"+variant+"]";
            Log.w(LOGTAG, selector);
            //String selector = mySharedPreferences.getString("prefSelector",getString(R.string.selector_val)).trim();
            //String selector = getString(R.string.selector_val);
            Elements links = doc.select(selector);
            for (Element link : links) {
                urls.add(link.attr("href"));
            }
        } catch (Throwable t) {
            Log.e(LOGTAG,t.getMessage());
        }
        //Collections.reverse(urls);
        return urls.toString();
    }

    public String getFirstUrl(List<String> array){
        String url = "";
        for (String i : array) {
            i = i.trim();
            String prefix = "";
            if (!(i.startsWith("http"))) {
                prefix = getBaseUrl();
            }
            url = prefix + i;
        }
        return url;
    }

    public String getBaseUrl() {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return mySharedPreferences.getString("prefBase",getString(R.string.base_val)).trim();
    }

    public ArrayList<String> getDLUrl(String url){
        Log.d(LOGTAG, "Download parse: " +url);
        ArrayList<String> urls = new ArrayList<String>();
        urls.add(url);
        new dlMd5().execute(new String[]{urls.get(0), url +".md5"});

        return urls;
    }


    private class dlMd5 extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground (String... strings){

            try {
                URL md5Url = new URL(strings[0]+".md5");
                HttpURLConnection connection = (HttpURLConnection) md5Url.openConnection();
                connection.setDoInput(true);
                try {
                    connection.connect();
                } catch (Exception t) {
                    Log.w(LOGTAG, "Connection error: " + t.getMessage());
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"));
                String md5 = br.readLine();
                md5 = md5.substring(0,md5.indexOf(" "));

                br.close();

                int slash = strings[0].lastIndexOf("/");
                String filename = strings[0].substring(slash + 1);

                writeFile(filename+".md5", md5);

            } catch (Exception e) {
                Log.w(LOGTAG, "MD5 Download error: " + e.getMessage());
            }
            return null;
        }
    }

    private class downloadFirstThread extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {
            return parseUrl(strings[0]);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            String newS = s.substring(1,s.length()-1);
            List<String> array = Arrays.asList(newS.split(","));

            String url = getFirstUrl(array);
            new ParseURLDownload().execute(new String[]{url.toString()});

        }
    }

    private class ParseURLDownload extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {
            return getDLUrl(strings[0]).toString();
        }


        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            String newS = s.substring(1,s.length()-1);
            List<String> array = Arrays.asList(newS.split(","));
            String url ="";
            for (String i : array) {
                String prefix = "";
                if (!(i.startsWith("http"))) {
                    prefix = getBaseUrl();
                }
                url = prefix+i;
            }
            if (!(url.isEmpty())) {
                int slash = url.lastIndexOf("/");
                String filename = url.substring(slash + 1);
                download(url, getString(R.string.app_name), filename, filename);
            }

        }
    }


    public void writeFile(String name, String body){
        try {
            FileOutputStream fileout = openFileOutput(name, MODE_PRIVATE);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
            outputWriter.write(body);
            outputWriter.close();
        } catch (Exception e) {
            Log.w(LOGTAG, "Unable to write: "+name);
        }
    }

    public void download(String url, String desc, String title, String filename) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        String selector = mySharedPreferences.getString("prefSelector", getString(R.string.selector_val)).trim();
        String exten = selector.substring(selector.lastIndexOf("."), selector.length() - 1);

        if (url.endsWith(exten)) {

            Log.d(LOGTAG, "Downloading: " + url);
            boolean external = mySharedPreferences.getBoolean("prefExternal", false);


            String directory = mySharedPreferences.getString("prefDirectory", Environment.DIRECTORY_DOWNLOADS).trim();
            if (!(directory.startsWith("/"))) {
                directory = "/" + directory;
            }
            File direct = new File(Environment.getExternalStorageDirectory() + directory);

            if (!direct.exists()) {
                direct.mkdirs();
            }
            boolean fileExists = false;

            //check to see if we already have the file
            //this will make scheduling better
            if (EasyPermissions.hasPermissions(this, MainActivity.perms2)) {
                //have to assume we want to download the file if we can't check the dir
                File f = new File(direct.getAbsolutePath());
                File file[] = f.listFiles();
                for (int i = 0; i < file.length; i++) {
                    if (filename.equals(file[i].getName())) {
                        fileExists = true;
                    }
                }
            }

            if (!fileExists) {
                if (external) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);
                } else if (EasyPermissions.hasPermissions(this, MainActivity.perms)) {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setDescription(desc);
                    request.setTitle(title);

                    // in order for this if to run, you must use the android 3.2 to compile your app
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        request.allowScanningByMediaScanner();
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    }

                    boolean wifionly = mySharedPreferences.getBoolean("prefWIFI", true);
                    //Restrict the types of networks over which this download may proceed.
                    if (wifionly) {
                        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
                    } else {
                        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                    }
                    //Set whether this download may proceed over a roaming connection.
                    request.setAllowedOverRoaming(false);
                    request.setDestinationInExternalPublicDir(directory, filename);

                    // get download service and enqueue file
                    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    manager.enqueue(request);
                } else {
                    Log.d(LOGTAG, "fallout");
                }
            } else {
                Log.d(LOGTAG, "file-exists");
            }
        } else {
            Log.d(LOGTAG, "Not downloading: " + url);
        }
    }

}
