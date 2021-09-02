package threads.server.work;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.core.Progress;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;

public class BackupWorker extends Worker {
    private static final String WID = "BW";
    private static final String TAG = BackupWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final IPFS ipfs;
    private final THREADS threads;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);
    private int mNote;

    @SuppressWarnings("WeakerAccess")
    public BackupWorker(@NonNull Context context,
                        @NonNull WorkerParameters params) {
        super(context, params);
        ipfs = IPFS.getInstance(context);
        threads = THREADS.getInstance(context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    private static OneTimeWorkRequest getWork(@NonNull Uri uri) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());

        return new OneTimeWorkRequest.Builder(BackupWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void backup(@NonNull Context context, @NonNull Uri uri) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                WID + uri, ExistingWorkPolicy.KEEP, getWork(uri));

    }

    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(mNote);
        }
    }

    private void reportProgress(@NonNull String title, int percent) {

        if (!isStopped()) {
            Notification notification = createNotification(title, percent);
            if (mNotificationManager != null) {
                mNotificationManager.notify(mNote, notification);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification createNotification(@NonNull String title, int progress) {


        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentTitle(title);
            return builder.build();
        } else {
            builder = new Notification.Builder(getApplicationContext(), Settings.CHANNEL_ID);
        }

        PendingIntent intent = WorkManager.getInstance(getApplicationContext())
                .createCancelPendingIntent(getId());
        String cancel = getApplicationContext().getString(android.R.string.cancel);

        Intent main = new Intent(getApplicationContext(), MainActivity.class);

        int requestID = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), requestID,
                main, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(getApplicationContext(), R.drawable.pause), cancel,
                intent).build();

        builder.setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setProgress(100, progress, false)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.download)
                .addAction(action)
                .setColor(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPrimary))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setUsesChronometer(true)
                .setOngoing(true);

        return builder.build();
    }


    @NonNull
    @Override
    public Result doWork() {

        String uri = getInputData().getString(Content.URI);
        Objects.requireNonNull(uri);

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + uri);
        mNote = Math.abs(uri.hashCode());
        try {

            DocumentFile rootDocFile = DocumentFile.fromTreeUri(getApplicationContext(),
                    Uri.parse(uri));
            Objects.requireNonNull(rootDocFile);

            List<Thread> children = threads.getVisibleChildren(0);


            if (!children.isEmpty()) {

                ForegroundInfo foregroundInfo = createForegroundInfo(getApplicationContext().getString(R.string.action_backup));
                setForegroundAsync(foregroundInfo);


                DocumentFile docFile = rootDocFile.createDirectory(getApplicationContext().getString(R.string.ipfs));
                Objects.requireNonNull(docFile);


                for (Thread child : children) {
                    if (!isStopped()) {
                        if (!child.isDeleting() && child.isSeeding()) {
                            if (child.isDir()) {
                                DocumentFile file = docFile.createDirectory(child.getName());
                                Objects.requireNonNull(file);
                                copyThreads(child, file);
                            } else {
                                DocumentFile childFile = docFile.createFile(
                                        child.getMimeType(), child.getName());
                                Objects.requireNonNull(childFile);
                                if (!childFile.canWrite()) {
                                    // throw message
                                    LogUtils.error(TAG, "can not write");
                                } else {
                                    copyThread(child, childFile);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            closeNotification();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }

    @Override
    public void onStopped() {
        super.onStopped();
        closeNotification();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title) {
        Notification notification = createNotification(title, 0);
        mLastNotification.set(notification);
        return new ForegroundInfo(mNote, notification);
    }

    private void copyThreads(@NonNull Thread thread, @NonNull DocumentFile file) {


        List<Thread> children = threads.getChildren(thread.getIdx());
        for (Thread child : children) {
            if (!isStopped()) {
                if (!child.isDeleting() && child.isSeeding()) {
                    if (child.isDir()) {
                        DocumentFile docFile = file.createDirectory(child.getName());
                        Objects.requireNonNull(docFile);
                        copyThreads(child, docFile);
                    } else {
                        DocumentFile childFile = file.createFile(
                                child.getMimeType(), child.getName());
                        Objects.requireNonNull(childFile);
                        if (!childFile.canWrite()) {
                            // throw message
                            LogUtils.error(TAG, "can not write");
                        } else {
                            copyThread(child, childFile);
                        }
                    }
                }
            }
        }
    }

    private void copyThread(@NonNull Thread thread, @NonNull DocumentFile file) {

        if (isStopped()) {
            return;
        }


        String cid = thread.getContent();
        Objects.requireNonNull(cid);


        try {
            AtomicLong refresh = new AtomicLong(System.currentTimeMillis());
            try (OutputStream os = getApplicationContext().getContentResolver().
                    openOutputStream(file.getUri())) {
                Objects.requireNonNull(os);

                ipfs.storeToOutputStream(os, Cid.decode(cid), new Progress() {

                    @Override
                    public void setProgress(int percent) {
                        reportProgress(thread.getName(), percent);
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

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            if (isStopped()) {
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }
}
