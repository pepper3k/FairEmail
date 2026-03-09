package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2026 by Marcel Bokhorst (M66B)
*/

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.util.List;

public class UnifiedPushService extends PushService {
    private static final String TEST_PREFIX = "FairEmail UnifiedPush test";

    @Override
    public void onMessage(@NonNull PushMessage message, @NonNull String instance) {
        String text = new String(message.getContent());
        Log.i("UnifiedPush message instance=" + instance + " text=" + text);

        try {
            Context context = getApplicationContext();

            if (text.startsWith(TEST_PREFIX)) {
                // Show a notification that looks like an email notification
                showTestNotification(context, text);
            } else {
                // Real push: trigger email sync
                Intent intent = new Intent(context, ServiceSynchronize.class)
                        .setAction("unifiedpush");
                ContextCompat.startForegroundService(context, intent);
            }
        } catch (Throwable ex) {
            Log.e("UnifiedPush message error", ex);
        }
    }

    private void showTestNotification(Context context, String text) {
        try {
            Intent intent = new Intent(context, ActivityView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "notification")
                    .setSmallIcon(R.drawable.baseline_mail_24)
                    .setContentTitle("UnifiedPush")
                    .setContentText("Test notification received successfully")
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setCategory(NotificationCompat.CATEGORY_EMAIL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL);

            NotificationManager nm = Helper.getSystemService(context, NotificationManager.class);
            nm.notify("unifiedpush.test", NotificationHelper.NOTIFICATION_TAGGED, builder.build());
        } catch (Throwable ex) {
            Log.e("UnifiedPush test notification error", ex);
        }
    }

    @Override
    public void onNewEndpoint(@NonNull PushEndpoint endpoint, @NonNull String instance) {
        String url = endpoint.getUrl();
        Log.i("UnifiedPush new endpoint=" + url + " instance=" + instance);

        try {
            Context context = getApplicationContext();

            // Always persist endpoint in SharedPreferences as fallback
            android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString("unifiedpush_endpoint", url).apply();

            // Also store on all UnifiedPush-enabled accounts
            DB db = DB.getInstance(context);
            List<EntityAccount> accounts = db.account().getUnifiedPushAccounts();
            for (EntityAccount account : accounts) {
                db.account().setAccountUnifiedPushEndpoint(account.id, url);
                Log.i("UnifiedPush endpoint stored for account=" + account.name);
            }
        } catch (Throwable ex) {
            Log.e("UnifiedPush endpoint error", ex);
        }
    }

    @Override
    public void onRegistrationFailed(@NonNull FailedReason reason, @NonNull String instance) {
        Log.w("UnifiedPush registration failed reason=" + reason + " instance=" + instance);
    }

    @Override
    public void onUnregistered(@NonNull String instance) {
        Log.i("UnifiedPush unregistered instance=" + instance);

        try {
            Context context = getApplicationContext();
            DB db = DB.getInstance(context);

            List<EntityAccount> accounts = db.account().getUnifiedPushAccounts();
            for (EntityAccount account : accounts) {
                db.account().setAccountUnifiedPushEndpoint(account.id, null);
                Log.i("UnifiedPush endpoint cleared for account=" + account.name);
            }
        } catch (Throwable ex) {
            Log.e("UnifiedPush unregistered error", ex);
        }
    }
}
