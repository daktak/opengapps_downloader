package org.opengappsdownloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

/**
 * Created by daktak on 4/26/16.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String DEBUG_TAG = "AlarmReceiver";
    private static final String LOGTAG = LogUtil
            .makeLogTag(AlarmReceiver.class);
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, Download.class);
        service.putExtra("url",buildPath(context));
        service.putExtra("action",3);
        context.startService(service);
    }
    public String buildPath(Context context) {
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String arch = mySharedPreferences.getString("prefArch","arm64");
        String base = mySharedPreferences.getString("prefBase","").trim();
        Uri builtUri = Uri.parse(base)
                .buildUpon()
                .appendPath("opengapps")
                .appendPath(arch)
                .appendPath("releases")
                .build();
        return builtUri.toString();
    }
}