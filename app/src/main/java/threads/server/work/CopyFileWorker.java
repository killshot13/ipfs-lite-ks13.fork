package threads.server.work;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.core.Progress;
import threads.server.R;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;

public class CopyFileWorker extends Worker {
    private static final String WID = "UFW";
    private static final String TAG = CopyFileWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);

    @SuppressWarnings("WeakerAccess")
    public CopyFileWorker(@NonNull Context context,
                          @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    private static OneTimeWorkRequest getWork(@NonNull Uri uri, long idx) {

        Data.Builder data = new Data.Builder();
        data.putLong(Content.IDX, idx);
        data.putString(Content.URI, uri.toString());

        return new OneTimeWorkRequest.Builder(CopyFileWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void copyTo(@NonNull Context context, @NonNull Uri uri, long idx) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                WID + idx, ExistingWorkPolicy.KEEP, getWork(uri, idx));

    }

    private void closeNotification(long idx) {
        if (mNotificationManager != null) {
            mNotificationManager.cancel((int) idx);
        }
    }

    private void reportProgress(long idx, @NonNull String title, int percent) {

        if (!isStopped()) {
            Notification notification = createNotification(title, percent);
            mLastNotification.set(notification);

            if (mNotificationManager != null) {
                mNotificationManager.notify((int) idx, notification);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification createNotification(@NonNull String text, int progress) {

        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentText(text);
            builder.setSubText("" + progress + "%");
            return builder.build();
        } else {
            builder = new Notification.Builder(getApplicationContext(), Settings.CHANNEL_ID);
        }

        builder.setContentText(text)
                .setSubText("" + progress + "%")
                .setProgress(100, progress, false)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.download)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setUsesChronometer(true)
                .setOngoing(true);

        return builder.build();
    }


    @NonNull
    @Override
    public Result doWork() {

        long idx = getInputData().getLong(Content.IDX, -1);
        String uri = getInputData().getString(Content.URI);
        Objects.requireNonNull(uri);

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + idx);

        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            THREADS threads = THREADS.getInstance(getApplicationContext());


            Thread thread = threads.getThreadByIdx(idx);
            Objects.requireNonNull(thread);

            String cid = thread.getContent();
            Objects.requireNonNull(cid);

            AtomicLong refresh = new AtomicLong(System.currentTimeMillis());
            try (OutputStream os = getApplicationContext().getContentResolver().
                    openOutputStream(Uri.parse(uri))) {
                Objects.requireNonNull(os);
                ipfs.storeToOutputStream(os, Cid.decode(cid), new Progress() {


                    @Override
                    public void setProgress(int percent) {
                        reportProgress(idx, thread.getName(), percent);
                    }

                    @Override
                    public boolean doProgress() {

                        long time = System.currentTimeMillis();
                        long diff = time - refresh.get();
                        boolean doProgress = (diff > Settings.REFRESH);
                        if (doProgress) {
                            refresh.set(time);
                        }
                        return doProgress;
                    }

                    @Override
                    public boolean isClosed() {
                        return isStopped();
                    }


                });
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            closeNotification(idx);
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }
}
