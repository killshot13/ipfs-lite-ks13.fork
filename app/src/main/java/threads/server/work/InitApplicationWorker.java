package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import threads.lite.LogUtils;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;

public class InitApplicationWorker extends Worker {

    private static final String TAG = InitApplicationWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public InitApplicationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static OneTimeWorkRequest getWork() {
        return new OneTimeWorkRequest.Builder(InitApplicationWorker.class).addTag(TAG).build();
    }


    public static void initialize(@NonNull Context context) {
        WorkManager.getInstance(context).enqueue(InitApplicationWorker.getWork());
    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {

            THREADS threads = THREADS.getInstance(getApplicationContext());
            DOCS docs = DOCS.getInstance(getApplicationContext());

            for (long idx : threads.getDeletedThreads()) {
                docs.deleteDocument(idx);
            }
            for (long idx : threads.getDeletedThreads()) {
                docs.deleteContent(idx);
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);

        } finally {
            EVENTS.getInstance(getApplicationContext()).refresh();
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

}

