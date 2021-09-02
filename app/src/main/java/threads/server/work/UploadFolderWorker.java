package threads.server.work;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.InputStream;
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
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;
import threads.server.services.MimeTypeService;


public class UploadFolderWorker extends Worker {
    private static final String WID = "IFW";
    private static final String TAG = UploadFolderWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final THREADS threads;
    private final DOCS docs;
    private final IPFS ipfs;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);


    @SuppressWarnings("WeakerAccess")
    public UploadFolderWorker(@NonNull Context context,
                              @NonNull WorkerParameters params) {
        super(context, params);
        threads = THREADS.getInstance(context);
        docs = DOCS.getInstance(context);
        ipfs = IPFS.getInstance(context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    private static OneTimeWorkRequest getWork(long idx, @NonNull Uri uri) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());
        data.putLong(Content.IDX, idx);

        return new OneTimeWorkRequest.Builder(UploadFolderWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void load(@NonNull Context context, long idx, @NonNull Uri uri) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                WID + uri, ExistingWorkPolicy.KEEP, getWork(idx, uri));

    }

    @Override
    public void onStopped() {
        super.onStopped();
        closeNotification();
    }


    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(getId().hashCode());
        }
    }

    private void reportProgress(@NonNull String title, int percent, int index, int maxIndex) {

        if (!isStopped()) {

            Notification notification = createNotification(title, percent, index, maxIndex);

            if (mNotificationManager != null) {
                mNotificationManager.notify(getId().hashCode(), notification);
            }
        }
    }

    private Notification createNotification(@NonNull String title, int progress, int index, int maxIndex) {
        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentTitle(title);
            builder.setSubText("" + index + "/" + maxIndex);
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
                .setSubText("" + index + "/" + maxIndex)
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
        long root = getInputData().getLong(Content.IDX, 0L);

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + uri);


        try {

            DocumentFile rootDocFile = DocumentFile.fromTreeUri(getApplicationContext(),
                    Uri.parse(uri));
            Objects.requireNonNull(rootDocFile);


            boolean hasChildren = rootDocFile.listFiles().length > 0;

            String name = rootDocFile.getName();
            Objects.requireNonNull(name);


            if (hasChildren) {
                ForegroundInfo foregroundInfo = createForegroundInfo(name);
                setForegroundAsync(foregroundInfo);
            }

            try {
                long parent = createDir(root, name, false);

                threads.setThreadWork(parent, getId());
                threads.setThreadUri(parent, uri);
                threads.setThreadLeaching(parent);

                copyDir(parent, rootDocFile);

                threads.setThreadDone(parent);

                threads.resetThreadWork(parent);

            } finally {
                closeNotification();
            }


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            EVENTS.getInstance(getApplicationContext()).refresh();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title) {
        Notification notification = createNotification(title, 0, 0, 1);
        mLastNotification.set(notification);
        return new ForegroundInfo(getId().hashCode(), notification);
    }

    private long createDir(long parent, @NonNull String name, boolean init) {
        long idx = docs.createDocument(parent, MimeTypeService.DIR_MIME_TYPE,
                Objects.requireNonNull(ipfs.createEmptyDir()).String(),
                null, name, 0L, false, init);
        docs.finishDocument(idx);
        return idx;
    }

    private void copyDir(long parent, @NonNull DocumentFile file) {

        DocumentFile[] filesInDir = file.listFiles();
        int maxIndex = filesInDir.length;
        int index = 0;
        for (DocumentFile docFile : filesInDir) {

            if (!isStopped()) {
                index++;
                if (docFile.isDirectory()) {
                    String name = docFile.getName();
                    Objects.requireNonNull(name);
                    long child = createDir(parent, name, true);
                    threads.setThreadLeaching(child);
                    copyDir(child, docFile);
                    threads.setThreadDone(child);
                    docs.finishDocument(child);
                } else {
                    long child = copyFile(parent, docFile, index, maxIndex);
                    docs.finishDocument(child);
                }
            }
        }
    }

    private long copyFile(long parent, @NonNull DocumentFile file, int index, int maxIndex) {

        if (isStopped()) {
            return 0L;
        }

        IPFS ipfs = IPFS.getInstance(getApplicationContext());
        THREADS threads = THREADS.getInstance(getApplicationContext());

        long idx = createThread(parent, file);

        threads.setThreadLeaching(idx);


        long size = file.length();
        String name = file.getName();
        Objects.requireNonNull(name);

        Uri uri = file.getUri();
        AtomicLong refresh = new AtomicLong(System.currentTimeMillis());
        try (InputStream is = getApplicationContext().getContentResolver().openInputStream(uri)) {
            Objects.requireNonNull(is);

            Cid cid = ipfs.storeInputStream(is, new Progress() {


                @Override
                public void setProgress(int percent) {
                    reportProgress(name, percent, index, maxIndex);
                    threads.setThreadProgress(idx, percent);
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


            }, size);


            threads.setThreadDone(idx, cid.String());
            return idx;

        } catch (Throwable throwable) {
            threads.setThreadsDeleting(idx);
            LogUtils.error(TAG, throwable);
        }

        return 0L;
    }

    private long createThread(long parent, @NonNull DocumentFile file) {

        Uri uri = file.getUri();
        DOCS docs = DOCS.getInstance(getApplicationContext());

        long size = file.length();
        String name = file.getName();
        Objects.requireNonNull(name);
        String mimeType = file.getType();


        return docs.createDocument(parent, mimeType, null, uri, name,
                size, false, true);
    }

}
