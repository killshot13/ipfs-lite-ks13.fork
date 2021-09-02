package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import threads.lite.IPFS;
import threads.lite.LogUtils;

public class SwarmConnectWorker extends Worker {


    private static final String TAG = SwarmConnectWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public SwarmConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }


    private static OneTimeWorkRequest getWork() {

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);

        return new OneTimeWorkRequest.Builder(SwarmConnectWorker.class)
                .addTag(TAG)
                .setConstraints(builder.build())
                .build();

    }

    public static void dialing(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.KEEP, getWork());

    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");
        try {

            try {
                IPFS ipfs = IPFS.getInstance(getApplicationContext());


                ipfs.bootstrap();

                ipfs.relays(IPFS.TIMEOUT_BOOTSTRAP);

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }
}

