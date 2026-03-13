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
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

// UnifiedPush connector service — receives push messages from a distributor app (e.g. ntfy).
// Push flow: Email server → NotiMail (IMAP IDLE) → ntfy.sh → ntfy app → onMessage() → ServiceUI sync.
// Each account uses its own UP instance (account ID as instance name) for per-account endpoints.
public class UnifiedPushService extends PushService {
    // Prefix used to identify test messages sent from FragmentAccount's "Test push" button
    static final String TEST_PREFIX = "FairEmail UnifiedPush test";

    // Called by the distributor app when a push message arrives.
    // Triggers a sync of all UnifiedPush-enabled accounts via ServiceUI.
    @Override
    public void onMessage(@NonNull PushMessage message, @NonNull String instance) {
        String text = new String(message.getContent());
        Log.i("UnifiedPush message instance=" + instance + " text=" + text);

        try {
            Context context = getApplicationContext();

            // Delegate sync to ServiceUI (lightweight IntentService) rather than
            // ServiceSynchronize (persistent foreground service) to avoid lifecycle issues
            Intent intent = new Intent(context, ServiceUI.class)
                    .setAction("unifiedpush");
            context.startService(intent);

            // Show a notification only for test messages (prefixed with TEST_PREFIX)
            if (text.startsWith(TEST_PREFIX))
                showTestNotification(context);

            // Debug toast — shows push content to confirm delivery
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> android.widget.Toast.makeText(context,
                    "UnifiedPush received: " + (text.length() > 40 ? text.substring(0, 40) + "…" : text),
                    android.widget.Toast.LENGTH_SHORT).show());
        } catch (Throwable ex) {
            Log.e("UnifiedPush message error", ex);
        }
    }

    // Shows a high-priority notification to confirm test push delivery.
    // Uses notification ID 991 to avoid colliding with FairEmail's own notification IDs.
    private void showTestNotification(Context context) {
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
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setGroup("unifiedpush");

            NotificationManager nm = Helper.getSystemService(context, NotificationManager.class);
            if (NotificationHelper.areNotificationsEnabled(nm))
                nm.notify(991, builder.build());
            else
                Log.w("UnifiedPush test: notifications not enabled");
        } catch (Throwable ex) {
            Log.e("UnifiedPush test notification error", ex);
        }
    }

    // Called by the distributor when a new endpoint URL is assigned.
    // The instance parameter is the account ID (set during registration in FragmentAccount).
    // Stores the endpoint in SharedPreferences keyed by account ID so FragmentAccount
    // can pick it up via OnSharedPreferenceChangeListener (avoids race with async callback).
    @Override
    public void onNewEndpoint(@NonNull PushEndpoint endpoint, @NonNull String instance) {
        String url = endpoint.getUrl();
        Log.i("UnifiedPush new endpoint=" + url + " instance=" + instance);

        try {
            Context context = getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            // Account-specific key: "unifiedpush_endpoint_<account_id>"
            prefs.edit().putString("unifiedpush_endpoint_" + instance, url).apply();
        } catch (Throwable ex) {
            Log.e("UnifiedPush endpoint error", ex);
        }
    }

    @Override
    public void onRegistrationFailed(@NonNull FailedReason reason, @NonNull String instance) {
        Log.w("UnifiedPush registration failed reason=" + reason + " instance=" + instance);
    }

    // Called when the distributor confirms unregistration.
    // Clears the account-specific endpoint from SharedPreferences.
    @Override
    public void onUnregistered(@NonNull String instance) {
        Log.i("UnifiedPush unregistered instance=" + instance);

        try {
            Context context = getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().remove("unifiedpush_endpoint_" + instance).apply();
        } catch (Throwable ex) {
            Log.e("UnifiedPush unregistered error", ex);
        }
    }
}
