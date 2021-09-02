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
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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
import threads.server.provider.FileProvider;

public class UploadFilesWorker extends Worker {

    private static final String TAG = UploadFilesWorker.class.getSimpleName();
    private static final int UTW = 17000;
    private final DOCS docs;
    private final IPFS ipfs;
    private final THREADS threads;
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);

    @SuppressWarnings("WeakerAccess")
    public UploadFilesWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        ipfs = IPFS.getInstance(getApplicationContext());
        threads = THREADS.getInstance(getApplicationContext());
        docs = DOCS.getInstance(getApplicationContext());
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    public static OneTimeWorkRequest getWork(long parent, @NonNull Uri uri) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());
        data.putLong(Content.IDX, parent);

        return new OneTimeWorkRequest.Builder(UploadFilesWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .build();
    }

    public static void load(@NonNull Context context, long parent, @NonNull Uri uri) {
        WorkManager.getInstance(context).enqueue(getWork(parent, uri));
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title) {

        Notification notification = createNotification(title, 0, 0, 0);

        mLastNotification.set(notification);
        return new ForegroundInfo(UTW, notification);
    }

    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(UTW);
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        closeNotification();
    }

    @NonNull
    @Override
    public Result doWork() {

        String uriFile = getInputData().getString(Content.URI);
        long parent = getInputData().getLong(Content.IDX, 0L);
        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ... ");

        try {
            Objects.requireNonNull(uriFile);

            ForegroundInfo foregroundInfo = createForegroundInfo(
                    getApplicationContext().getString(R.string.uploading));
            setForegroundAsync(foregroundInfo);

            List<String> uris = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getApplicationContext().getContentResolver()
                            .openInputStream(Uri.parse(uriFile))))) {
                Objects.requireNonNull(reader);
                while (reader.ready()) {
                    uris.add(reader.readLine());
                }
            }

            int maxIndex = uris.size();
            AtomicInteger index = new AtomicInteger(0);
            for (String uriStr : uris) {
                Uri uri = Uri.parse(uriStr);
                if (!isStopped()) {
                    if (!FileProvider.hasReadPermission(getApplicationContext(), uri)) {
                        EVENTS.getInstance(getApplicationContext()).error(
                                getApplicationContext().getString(
                                        R.string.file_has_no_read_permission));
                        continue;
                    }

                    if (FileProvider.isPartial(getApplicationContext(), uri)) {
                        EVENTS.getInstance(getApplicationContext()).error(
                                getApplicationContext().getString(R.string.file_not_valid));
                        continue;
                    }

                    final int indexValue = index.incrementAndGet();


                    String name = FileProvider.getFileName(getApplicationContext(), uri);
                    String mimeType = FileProvider.getMimeType(getApplicationContext(), uri);

                    long size = FileProvider.getFileSize(getApplicationContext(), uri);

                    long idx = docs.createDocument(parent, mimeType, null, uri,
                            name, size, false, true);


                    if (!threads.isThreadLeaching(idx)) {
                        threads.setThreadLeaching(idx);
                    }

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
                                reportProgress(name, percent, indexValue, maxIndex);
                            }
                        }, size);

                        if (!isStopped()) {
                            Objects.requireNonNull(cid);

                            reportProgress(name, 100, indexValue, maxIndex);

                            threads.setThreadDone(idx, cid.String());

                            docs.finishDocument(idx);

                        } else {
                            threads.setThreadsDeleting(idx);
                        }
                    } catch (Throwable e) {
                        threads.setThreadsDeleting(idx);
                    }
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

    private void reportProgress(@NonNull String info, int percent, int index, int maxIndex) {


        if (!isStopped()) {

            Notification notification = createNotification(info, percent, index, maxIndex);
            if (mNotificationManager != null) {
                mNotificationManager.notify(UTW, notification);
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

}
