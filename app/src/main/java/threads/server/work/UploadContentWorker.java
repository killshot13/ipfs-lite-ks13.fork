package threads.server.work;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.core.ClosedException;
import threads.lite.core.Progress;
import threads.lite.utils.Link;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.magic.ContentInfo;
import threads.server.magic.ContentInfoUtil;
import threads.server.provider.FileProvider;
import threads.server.services.MimeTypeService;

public class UploadContentWorker extends Worker {

    private static final String TAG = UploadContentWorker.class.getSimpleName();

    private final DOCS docs;
    private final THREADS threads;
    private final IPFS ipfs;
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);
    private final AtomicBoolean finished = new AtomicBoolean(true);

    @SuppressWarnings("WeakerAccess")
    public UploadContentWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        docs = DOCS.getInstance(context);
        threads = THREADS.getInstance(context);
        ipfs = IPFS.getInstance(context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    private static OneTimeWorkRequest getWork(long idx, boolean bootstrap) {

        Data.Builder data = new Data.Builder();
        data.putLong(Content.IDX, idx);
        data.putBoolean(Content.BOOT, bootstrap);

        return new OneTimeWorkRequest.Builder(UploadContentWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setInitialDelay(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public static UUID load(@NonNull Context context, long idx, boolean bootstrap) {
        OneTimeWorkRequest request = getWork(idx, bootstrap);
        WorkManager.getInstance(context).enqueue(request);
        return request.getId();
    }


    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title, int progress) {
        Notification notification = createNotification(title, progress);
        mLastNotification.set(notification);
        return new ForegroundInfo(getId().hashCode(), notification);
    }

    @NonNull
    @Override
    public Result doWork() {


        long idx = getInputData().getLong(Content.IDX, -1);

        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + idx);
        boolean bootstrap = getInputData().getBoolean(Content.BOOT, true);


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

            if (Objects.equals(uri.getScheme(), Content.HTTPS) ||
                    Objects.equals(uri.getScheme(), Content.HTTP) ||
                    Objects.equals(uri.getScheme(), Content.IPFS) ||
                    Objects.equals(uri.getScheme(), Content.IPNS)) {
                ForegroundInfo foregroundInfo = createForegroundInfo(
                        thread.getName(), thread.getProgress());
                setForegroundAsync(foregroundInfo);
            }

            if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                    Objects.equals(uri.getScheme(), Content.IPNS)) {
                if (bootstrap) {
                    if (!isStopped()) {
                        try {
                            ipfs.bootstrap();
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }
                }
            }

            if (Objects.equals(uri.getScheme(), Content.HTTPS) ||
                    Objects.equals(uri.getScheme(), Content.HTTP)) {
                try {
                    String filename = thread.getName();
                    Objects.requireNonNull(filename);


                    URL u = new URL(uri.toString());
                    HttpURLConnection huc = (HttpURLConnection) u.openConnection();
                    HttpURLConnection.setFollowRedirects(false);
                    huc.setReadTimeout(30 * 1000);
                    huc.connect();

                    final long size = thread.getSize();

                    InputStream is = huc.getInputStream();

                    Cid cid = ipfs.storeInputStream(is, new Progress() {

                        @Override
                        public void setProgress(int percent) {
                            threads.setThreadProgress(idx, percent);
                            reportProgress(filename, percent);
                        }

                        @Override
                        public boolean doProgress() {
                            return true;
                        }

                        @Override
                        public boolean isClosed() {
                            return isStopped();
                        }


                    }, size);

                    threads.setThreadDone(idx, cid.String());

                } catch (Throwable e) {
                    threads.resetThreadLeaching(idx);
                    if (!isStopped()) {
                        threads.setThreadsDeleting(idx);
                        buildFailedNotification(thread.getName());
                    }
                    throw e;
                } finally {
                    if (threads.isThreadLeaching(idx)) {
                        threads.resetThreadLeaching(idx);
                    }
                    threads.resetThreadWork(idx);
                }
            } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                    Objects.equals(uri.getScheme(), Content.IPFS)) {

                try {

                    if (threads.getThreadContent(idx) == null) {

                        String name = docs.getFileName(uri);

                        Cid cid = Cid.decode(docs.getContent(uri, this::isStopped));

                        String mimeType = docs.getMimeType(getApplicationContext(),
                                uri, cid, this::isStopped);

                        List<Thread> names = threads.getThreadsByNameAndParent(name, 0L);
                        names.remove(thread);
                        if (!names.isEmpty()) {
                            name = docs.getUniqueName(name, 0L);
                        }
                        threads.setThreadName(idx, name);
                        threads.setThreadMimeType(idx, mimeType);
                        threads.setThreadContent(idx, cid.String());

                        downloadThread(idx);

                    } else {
                        downloadThread(idx);
                    }

                    if (!isStopped()) {
                        if (!finished.get()) {
                            buildFailedNotification(thread.getName());
                        }
                    }

                } catch (Throwable e) {
                    if (!isStopped()) {
                        buildFailedNotification(thread.getName());
                    }
                    throw e;
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


    private void checkParentSeeding(long parent) {

        if (parent == 0L) {
            return;
        }

        try {
            int allSeeding = 0;

            List<Thread> list = threads.getChildren(parent);
            for (Thread entry : list) {
                if (entry.isSeeding()) {
                    allSeeding++;
                }
            }
            boolean seeding = allSeeding == list.size();

            if (seeding) {
                threads.setThreadDone(parent);
                docs.finishDocument(parent);
                checkParentSeeding(threads.getThreadParent(parent));
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    private void download(@NonNull Thread thread) {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start [" + (System.currentTimeMillis() - start) + "]...");

        try {

            long threadIdx = thread.getIdx();

            Objects.requireNonNull(thread);
            String filename = thread.getName();
            Objects.requireNonNull(filename);

            reportProgress(filename, 0);

            String cid = thread.getContent();
            Objects.requireNonNull(cid);

            AtomicLong started = new AtomicLong(System.currentTimeMillis());

            long parent = thread.getParent();
            if (ipfs.isDir(Cid.decode(cid), this::isStopped)) {
                // empty directory
                threads.setThreadDone(threadIdx);
                threads.setThreadSize(threadIdx, 0L);
                threads.setThreadMimeType(threadIdx, MimeTypeService.DIR_MIME_TYPE);
            } else {
                try {
                    File file = FileProvider.getInstance(
                            getApplicationContext()).createDataFile(threadIdx);

                    AtomicLong refresh = new AtomicLong(System.currentTimeMillis());
                    ipfs.storeToFile(file, Cid.decode(cid),
                            new Progress() {
                                @Override
                                public boolean isClosed() {
                                    return isStopped();
                                }


                                @Override
                                public void setProgress(int percent) {
                                    threads.setThreadProgress(threadIdx, percent);
                                    reportProgress(filename, percent);
                                }

                                @Override
                                public boolean doProgress() {
                                    started.set(System.currentTimeMillis());
                                    long time = System.currentTimeMillis();
                                    long diff = time - refresh.get();
                                    boolean doProgress = (diff > Settings.REFRESH);
                                    if (doProgress) {
                                        refresh.set(time);
                                    }
                                    return doProgress;
                                }


                            });


                    threads.setThreadDone(threadIdx);

                    threads.setThreadSize(threadIdx, file.length());


                    String mimeType = thread.getMimeType();
                    if (mimeType.isEmpty()) {

                        ContentInfo contentInfo = ContentInfoUtil.getInstance(
                                getApplicationContext()).getContentInfo(file);
                        if (contentInfo != null) {
                            String contentInfoMimeType = contentInfo.getMimeType();
                            if (contentInfoMimeType != null) {
                                mimeType = contentInfoMimeType;
                            } else {
                                mimeType = MimeTypeService.OCTET_MIME_TYPE;
                            }
                        } else {
                            mimeType = MimeTypeService.OCTET_MIME_TYPE;
                        }
                        threads.setThreadMimeType(threadIdx, mimeType);
                    }

                    Uri uri = FileProvider.getDataUri(getApplicationContext(), threadIdx);
                    if (uri != null) {
                        threads.setThreadUri(threadIdx, uri.toString());
                    }

                    docs.finishDocument(threadIdx);

                    checkParentSeeding(parent);
                } catch (Throwable throwable) {
                    finished.set(false);
                    threads.resetThreadLeaching(threadIdx);
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
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


    private void reportProgress(@NonNull String info, int percent) {

        if (!isStopped()) {
            Notification notification = createNotification(info, percent);
            if (mNotificationManager != null) {
                mNotificationManager.notify(getId().hashCode(), notification);
            }

        }
    }

    private Notification createNotification(@NonNull String title, int progress) {

        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentTitle(title);
            builder.setSubText("" + progress + "%");
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
                .setSubText("" + progress + "%")
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


    private Thread getFolderThread(long parent, @NonNull Cid cid) {

        List<Thread> entries = threads.getThreadsByContentAndParent(cid, parent);
        if (!entries.isEmpty()) {
            return entries.get(0);
        }
        return null;
    }


    private List<Thread> evalLinks(long parent, @NonNull List<Link> links) throws ClosedException {
        List<Thread> threadList = new ArrayList<>();

        for (Link link : links) {

            Cid cid = link.getCid();
            Thread entry = getFolderThread(parent, cid);
            if (entry != null) {
                if (!entry.isSeeding()) {
                    threadList.add(entry);
                }
            } else {

                long idx = createThread(cid, link, parent);
                entry = threads.getThreadByIdx(idx);
                Objects.requireNonNull(entry);

                threadList.add(entry);
            }
        }

        return threadList;
    }

    private long createThread(@NonNull Cid cid, @NonNull Link link, long parent) throws ClosedException {

        String name = link.getName();
        String mimeType = null;
        if (ipfs.isDir(link.getCid(), this::isStopped)) {
            mimeType = MimeTypeService.DIR_MIME_TYPE;
        }

        return docs.createDocument(parent, mimeType, cid.String(), null,
                name, -1, false, true);
    }


    private void downloadThread(long idx) throws ClosedException {

        Thread thread = threads.getThreadByIdx(idx);
        Objects.requireNonNull(thread);

        String cid = thread.getContent();
        Objects.requireNonNull(cid);

        List<Link> links = ipfs.getLinks(Cid.decode(cid), false, this::isStopped);

        if (links != null) {
            if (links.isEmpty()) {
                if (!isStopped()) {
                    download(thread);
                }
            } else {

                // thread is directory
                if (!thread.isDir()) {
                    threads.setMimeType(thread, DocumentsContract.Document.MIME_TYPE_DIR);
                }

                List<Thread> children = evalLinks(thread.getIdx(), links);

                for (Thread child : children) {
                    if (!isStopped()) {
                        if (child.isDir()) {
                            downloadThread(child.getIdx());
                        } else {
                            download(child);
                        }
                    }
                }
            }
        }
    }
}
