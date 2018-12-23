package org.opengappsdownloader;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * daktak
 */

public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks {
    private static final String LOGTAG = LogUtil
            .makeLogTag(MainActivity.class);
    public static String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static String[] perms2 = {Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_PREFS = 99;
    private static final int RC_EXT_WRITE = 1;
    private static final int RC_EXT_READ = 2;
    public static MainActivity instance = null;
    public ArrayList<String> md5check = new ArrayList<>();
    public ArrayList<String> names = new ArrayList<>();
    public ArrayList<String> urls = new ArrayList<>();
    public String directory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
        setApi(this);
        String[] namesA = new String[]{getString(R.string.loading)};
        ListView mainListView = findViewById(R.id.listView);
        ListAdapter listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, namesA);
        // Set the ArrayAdapter as the ListView's adapter.
        mainListView.setAdapter(listAdapter);

        if (!(EasyPermissions.hasPermissions(this, perms))) {
            // Ask for both permissions
            EasyPermissions.requestPermissions(this, getString(R.string.extWritePerm), RC_EXT_WRITE, perms);
            //otherwise use app
        }

        if (!(EasyPermissions.hasPermissions(this, perms2))) {
            // Ask for both permissions
            EasyPermissions.requestPermissions(this, getString(R.string.extReadPerm), RC_EXT_READ, perms2);
            //otherwise use app
        }

        setAlarm(this);
        run(this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        setApi(this);
        setAlarm(this);
        run(this);
    }

    @TargetApi(21)
    public String get64() {
        String out = "";
        if (Build.VERSION.SDK_INT >= 21) {
            if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                out += "64";
            }
        }
        return out;
    }

    public void setApi(Context context) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor prefEdit = mySharedPreferences.edit();
        String android = mySharedPreferences.getString("prefAndroid", getString(R.string.android_val));

        if (android.equalsIgnoreCase(getString(R.string.auto_detect))) {
            String newAndroid = getString(R.string.androiddefault);
            try {
                newAndroid = Build.VERSION.RELEASE.substring(0, 3);
            } catch (Exception e) {
                Log.w(LOGTAG, "Failed to get version");
            }
            //and newAndroid in list?
            Log.d(LOGTAG, "Detected release: " + newAndroid);
            if (!Arrays.asList(getResources().getStringArray(R.array.android)).contains(newAndroid)) {
                newAndroid = getString(R.string.androiddefault);
            }
            prefEdit.remove("prefAndroid");
            prefEdit.putString("prefAndroid", newAndroid);
            Log.d(LOGTAG, "Setting api: " + newAndroid);
        }

        String arch = mySharedPreferences.getString("prefArch", getString(R.string.arch_val));

        if (arch.equalsIgnoreCase(getString(R.string.auto_detect))) {
            String newArch = getString(R.string.archdefault);
            try {
                String arch1 = Build.CPU_ABI;
                newArch = arch1.substring(0, 3).toLowerCase(Locale.ENGLISH);
                newArch += get64();
            } catch (Exception e) {
                Log.w(LOGTAG, "Failed to get arch");
            }
            Log.d(LOGTAG, "Detected arch: " + newArch);
            //and newArc8h in list?
            if (!Arrays.asList(getResources().getStringArray(R.array.arch)).contains(newArch)) {
                newArch = getString(R.string.archdefault);
            }

            prefEdit.remove("prefArch");
            prefEdit.putString("prefArch", newArch);
            Log.d(LOGTAG, "Setting arch: " + newArch);
        }
        prefEdit.apply();
    }


    public void setAlarm(Context context) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean daily = mySharedPreferences.getBoolean("prefDailyDownload", false);
        if (daily) {
            Log.d(LOGTAG, "Setting daily alarm");
            setRecurringAlarm(context);
        } else {
            CancelAlarm(context);
        }
    }

    public SharedPreferences getPref() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    public void setRecurringAlarm(Context context) {

        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int hour = Integer.parseInt(mySharedPreferences.getString("prefHour", getString(R.string.hour_val)));
        int minute = Integer.parseInt(mySharedPreferences.getString("prefMinute", getString(R.string.minute_val)));
        Calendar updateTime = Calendar.getInstance();
        //updateTime.setTimeZone(TimeZone.getTimeZone("GMT"));
        updateTime.set(Calendar.HOUR_OF_DAY, hour);
        updateTime.set(Calendar.MINUTE, minute);

        Intent downloader = new Intent(context, AlarmReceiver.class);
        PendingIntent recurringDownload = PendingIntent.getBroadcast(context,
                0, downloader, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarms = (AlarmManager) getSystemService(
                Context.ALARM_SERVICE);
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                updateTime.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, recurringDownload);

    }

    public void CancelAlarm(Context context) {
        Intent downloader = new Intent(context, AlarmReceiver.class);
        PendingIntent recurringDownload = PendingIntent.getBroadcast(context,
                0, downloader, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarms = (AlarmManager) getSystemService(
                Context.ALARM_SERVICE);
        alarms.cancel(recurringDownload);
    }

    public void run(Context context) {
        //new ParseURL().execute(new String[]{buildPath(context)});
        Intent service = new Intent(context, Download.class);
        service.putExtra("url", buildPath(context));
        service.putExtra("action", 1);
        context.startService(service);
    }

    public String buildPath(Context context) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String arch = mySharedPreferences.getString("prefArch", "arm64");
        String base = mySharedPreferences.getString("prefBase", getString(R.string.base_val)).trim();
        Uri builtUri = Uri.parse(base)
                .buildUpon()
                .appendPath(getString(R.string.subdir))
                .appendPath(arch)
                .appendPath("releases")
                .build();
        return builtUri.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent prefs = new Intent(getBaseContext(), SetPreferenceActivity.class);
            startActivityForResult(prefs, REQUEST_PREFS);
            run(this);
            setAlarm(this);
            return true;
        }
        if (id == R.id.action_reboot) {
            ExecuteAsRootBase e = new ExecuteAsRootBase() {
                @Override
                protected ArrayList<String> getCommandsToExecute() {
                    ArrayList<String> a = new ArrayList<>();
                    a.add("reboot recovery");
                    return a;
                }
            };
            e.execute();
            return true;
        }
        if (id == R.id.action_refresh) {
            run(this);
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Some permissions have been granted
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Some permissions have been denied

    }

    public String getBaseUrl() {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return mySharedPreferences.getString("prefBase", getString(R.string.base_val)).trim();
    }

    public String readFile(String name) {

        StringBuilder out = new StringBuilder();
        try {
            FileInputStream filein = openFileInput(name);
            InputStreamReader inputreader = new InputStreamReader(filein);
            BufferedReader buffreader = new BufferedReader(inputreader);
            String line;

            while ((line = buffreader.readLine()) != null) {
                out.append(line);
            }

            filein.close();
        } catch (Exception e) {
            Log.d(LOGTAG, "Unable to open: " + name);
        }
        return out.toString();
    }

    public void writeFile(String name, String body) {
        try {
            FileOutputStream fileout = openFileOutput(name, MODE_PRIVATE);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
            outputWriter.write(body);
            outputWriter.close();
        } catch (Exception e) {
            Log.w(LOGTAG, "Unable to write: " + name);
        }
    }

    public void setList(List<String> values) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        directory = mySharedPreferences.getString("prefDirectory", Environment.DIRECTORY_DOWNLOADS).trim();
        boolean external = mySharedPreferences.getBoolean("prefExternal", false);
        md5check.clear();
        names.clear();
        urls.clear();
        String md5_ext = getString(R.string.md5_ext);
        final String md5_calc_ext = getString(R.string.md5calc_ext);

        if (external) {
            directory = Environment.DIRECTORY_DOWNLOADS;
        }

        File direct = new File(Environment.getExternalStorageDirectory() + "/" + directory);
        if (!direct.exists()) {
            direct.mkdirs();
        }
        Log.w(LOGTAG, directory);
        File file[] = new File[0];
        if (EasyPermissions.hasPermissions(this, perms2)) {
            try {
                file = direct.listFiles();
            } catch (Exception e) {
                Log.w(LOGTAG, "Cant " + e.getMessage());
            }
        }

        for (String i : values) {
            String md5val = "";
            i = i.trim();
            String name = i;
            try {
                int slash = i.lastIndexOf("/") + 1;
                name = i.substring(slash);
            } catch (Exception e) {
                Log.w(LOGTAG, "Cant find slash in " + i);
            }
            names.add(name);

            //for every result - check if file exists
            // then check if downloaded md5 exists
            // then check if calc exists

            for (File aFile : file) {

                if (name.equals(aFile.getName())) {
                    String md5 = readFile(name + md5_ext);
                    if (!md5.isEmpty()) {
                        String md5calc = readFile(name + md5_calc_ext);
                        if (md5calc.isEmpty()) {
                            md5calc = MD5.calculateMD5(aFile);
                        }
                        if (md5calc.equalsIgnoreCase(md5)) {
                            md5val = "Y";
                            //cache this result
                            writeFile(name + md5_calc_ext, md5calc);
                        } else {
                            md5val = "N";
                            //don't cache, in the event the file is still downloading
                        }

                    } else {
                        md5val = "U";
                    }
                }
            }
            md5check.add(md5val);

            String prefix = "";
            if (!(i.startsWith("http"))) {
                prefix = getBaseUrl();
            }
            urls.add(prefix + i);


        }
        //newest on top
        //Collections.reverse(urls);
        //Collections.reverse(names);
        String[] namesS = new String[names.size()];
        namesS = names.toArray(namesS);
        // Find the ListView resource.
        ListView mainListView = findViewById(R.id.listView);
        String[] md5checkS = new String[md5check.size()];
        md5checkS = md5check.toArray(md5checkS);

        MyCustomAdapter listAdapter = new MyCustomAdapter(this, namesS, file, md5checkS);
        //ListAdapter listAdapter =  new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);

        // Set the ArrayAdapter as the ListView's adapter.
        mainListView.setAdapter(listAdapter);
        SwipeDismissListViewTouchListener touchListener =
                new SwipeDismissListViewTouchListener(
                        mainListView,
                        new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                boolean dis = true;
                                if (md5check.get(position).isEmpty()) {
                                    dis = false;
                                }
                                return dis;
                            }

                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {
                                    final int pos = position;
                                    DialogInterface.OnClickListener yesListener = (dialog, which) -> {
                                        File direct1 = new File(Environment.getExternalStorageDirectory() + "/" + directory + "/" + names.get(pos));
                                        Log.d(LOGTAG, "Delete " + direct1.getName());
                                        if (direct1.exists() && direct1.isFile()) {
                                            direct1.delete();
                                        }
                                        File md5file = new File(getFilesDir(), names.get(pos) + md5_calc_ext);
                                        if (md5file.exists() && md5file.isFile()) {
                                            md5file.delete();
                                        }
                                        if (MainActivity.instance != null) {
                                            run(MainActivity.instance);
                                        }
                                    };
                                    message_dialog_yes_no(getString(R.string.delete) + " " + names.get(pos) + "?", yesListener);
                                }
                            }
                        });
        mainListView.setOnTouchListener(touchListener);

        mainListView.setOnItemClickListener((parent, view, position, id) -> {
            if (view.isEnabled()) {
                String url = urls.get(position);
                Context context = getBaseContext();
                Intent service = new Intent(context, Download.class);
                service.putExtra("url", url);
                service.putExtra("action", 2);
                context.startService(service);

                //new ParseURLDownload().execute(new String[]{url});

            } else {
                Log.d(LOGTAG, "Entry disabled");
            }
        });
    }

    public void message_dialog_yes_no(String msg, DialogInterface.OnClickListener yesListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        builder.setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.yes), yesListener)
                .setNegativeButton(getString(R.string.no), (dialog, id) -> dialog.cancel())
                .show();
    }

    /**
     * Executes commands as root user
     *
     * @author http://muzikant-android.blogspot.com/2011/02/how-to-get-root-access-and-execute.html
     */
    public abstract class ExecuteAsRootBase {
        public final boolean execute() {
            boolean retval = false;
            try {
                ArrayList<String> commands = getCommandsToExecute();
                if (null != commands && commands.size() > 0) {
                    Process suProcess = Runtime.getRuntime().exec("su");

                    DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                    // Execute commands that require root access
                    for (String currCommand : commands) {
                        os.writeBytes(currCommand + "\n");
                        os.flush();
                    }

                    os.writeBytes("exit\n");
                    os.flush();

                    try {
                        int suProcessRetval = suProcess.waitFor();
                        // Root access granted
                        // Root access denied
                        retval = 255 != suProcessRetval;
                    } catch (Exception ex) {
                        Log.e(LOGTAG, "Error executing root action\n" + ex.toString());
                    }
                }
            } catch (IOException ex) {
                Log.w(LOGTAG, "Can't get root access", ex);
            } catch (SecurityException ex) {
                Log.w(LOGTAG, "Can't get root access", ex);
            } catch (Exception ex) {
                Log.w(LOGTAG, "Error executing internal operation", ex);
            }

            return retval;
        }

        protected abstract ArrayList<String> getCommandsToExecute();
    }


    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        instance = null;
    }

}
