package threads.server.work;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
import threads.server.core.threads.Thread;

public class UploadFileWorker extends Worker {

    private static final String TAG = UploadFileWorker.class.getSimpleName();

    private final DOCS docs;

    @SuppressWarnings("WeakerAccess")
    public UploadFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        docs = DOCS.getInstance(context);
    }


    private static OneTimeWorkRequest getWork(long idx) {

        Data.Builder data = new Data.Builder();
        data.putLong(Content.IDX, idx);

        return new OneTimeWorkRequest.Builder(UploadFileWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public static UUID load(@NonNull Context context, long idx) {
        OneTimeWorkRequest request = getWork(idx);
        WorkManager.getInstance(context).enqueue(request);
        return request.getId();
    }


    @NonNull
    @Override
    public Result doWork() {


        long idx = getInputData().getLong(Content.IDX, -1);

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + idx);


        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            THREADS threads = THREADS.getInstance(getApplicationContext());

            Thread thread = threads.getThreadByIdx(idx);
            Objects.requireNonNull(thread);


            if (!threads.isThreadLeaching(idx)) {
                threads.setThreadLeaching(idx);
            }

            if (threads.isThreadInit(idx)) {
                threads.resetThreadInit(idx);
            }

            if (!Objects.equals(thread.getWorkUUID(), getId())) {
                threads.setThreadWork(idx, getId());
            }

            String url = thread.getUri();
            Objects.requireNonNull(url);
            Uri uri = Uri.parse(url);

            {
                // normal case like content of files
                final long size = thread.getSize();
                AtomicLong refresh = new AtomicLong(System.currentTimeMillis());
                try (InputStream inputStream = getApplicationContext().getContentResolver()
                        .openInputStream(uri)) {
                    Objects.requireNonNull(inputStream);


                    Cid cid = ipfs.storeInputStream(inputStream, new Progress() {
                        @Override
                        public boolean isClosed() {
                            return isStopped();
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
                        public void setProgress(int percent) {
                            threads.setThreadProgress(idx, percent);
                        }
                    }, size);
                    if (!isStopped()) {
                        Objects.requireNonNull(cid);

                        threads.setThreadDone(idx, cid.String());
                        docs.finishDocument(idx);

                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                    if (!isStopped()) {
                        threads.setThreadsDeleting(idx);
                        buildFailedNotification(thread.getName());
                    }
                    throw throwable;
                } finally {
                    if (threads.isThreadLeaching(idx)) {
                        threads.resetThreadLeaching(idx);
                    }
                    threads.resetThreadWork(idx);
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            EVENTS.getInstance(getApplicationContext()).refresh();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }


    private void buildFailedNotification(@NonNull String name) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(), Settings.CHANNEL_ID);

        builder.setContentTitle(getApplicationContext().getString(R.string.download_failed, name));
        builder.setSmallIcon(R.drawable.download);
        Intent defaultIntent = new Intent(getApplicationContext(), MainActivity.class);
        int requestID = (int) System.currentTimeMillis();
        PendingIntent defaultPendingIntent = PendingIntent.getActivity(
                getApplicationContext(), requestID, defaultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(defaultPendingIntent);
        Notification notification = builder.build();

        NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(getId().hashCode(), notification);
        }
    }


}
