package threads.server.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.Content;
import threads.server.work.PageWorker;
import threads.server.work.SwarmConnectWorker;

public class DaemonService extends Service {

    private static final String TAG = DaemonService.class.getSimpleName();
    private ConnectivityManager.NetworkCallback networkCallback;

    public static void start(@NonNull Context context) {

        try {
            Intent intent = new Intent(context, DaemonService.class);
            intent.putExtra(Content.REFRESH, true);
            ContextCompat.startForegroundService(context, intent);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public void unRegisterNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void registerNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    SwarmConnectWorker.dialing(getApplicationContext());
                }

                @Override
                public void onLost(Network network) {
                }
            };


            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            LogUtils.error(TAG, e);
        }
    }

    private void createChannel(@NonNull Context context) {


        try {
            CharSequence name = context.getString(R.string.daemon_channel_name);
            String description = context.getString(R.string.daemon_channel_description);
            NotificationChannel mChannel = new NotificationChannel(TAG, name,
                    NotificationManager.IMPORTANCE_HIGH);

            mChannel.setDescription(description);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }

    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {

            if (intent.getBooleanExtra(Content.REFRESH, false)) {

                IPFS ipfs = IPFS.getInstance(getApplicationContext());

                NotificationCompat.Builder builder = new NotificationCompat.Builder(
                        getApplicationContext(), TAG);

                Intent notifyIntent = new Intent(getApplicationContext(), MainActivity.class);
                int viewID = (int) System.currentTimeMillis();
                PendingIntent viewIntent = PendingIntent.getActivity(getApplicationContext(),
                        viewID, notifyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


                Intent stopIntent = new Intent(getApplicationContext(), DaemonService.class);
                stopIntent.putExtra(Content.REFRESH, false);
                int requestID = (int) System.currentTimeMillis();
                PendingIntent stopPendingIntent = PendingIntent.getService(
                        getApplicationContext(), requestID, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                String cancel = getApplicationContext().getString(android.R.string.cancel);
                NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                        R.drawable.pause, cancel,
                        stopPendingIntent).build();
                builder.setSmallIcon(R.drawable.access_point_network);
                builder.addAction(action);
                builder.setUsesChronometer(true);
                builder.setOnlyAlertOnce(true);
                String port = String.valueOf(ipfs.getPort());
                builder.setContentText(getString(R.string.service_is_running, port));
                builder.setContentIntent(viewIntent);
                builder.setSubText(getApplicationContext().getString(
                        R.string.port) + " " + ipfs.getPort());
                builder.setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary));
                builder.setCategory(Notification.CATEGORY_SERVICE);


                Notification notification = builder.build();
                startForeground(TAG.hashCode(), notification);

                registerNetworkCallback();

                PageWorker.publish(getApplicationContext());

            } else {
                try {
                    stopForeground(true);

                } finally {
                    stopSelf();
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        return START_NOT_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unRegisterNetworkCallback();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            registerNetworkCallback();
            createChannel(getApplicationContext());
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

}
