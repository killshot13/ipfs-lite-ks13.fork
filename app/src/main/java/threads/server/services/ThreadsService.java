package threads.server.services;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.lite.LogUtils;
import threads.server.core.DeleteOperation;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;

public class ThreadsService {


    private static final String TAG = ThreadsService.class.getSimpleName();


    public static void removeThreads(@NonNull Context context, long... indices) {


        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            long start = System.currentTimeMillis();

            try {
                THREADS threads = THREADS.getInstance(context);
                EVENTS events = EVENTS.getInstance(context);
                threads.setThreadsDeleting(indices);
                Gson gson = new Gson();
                DeleteOperation deleteOperation = new DeleteOperation();
                deleteOperation.indices = indices;

                String content = gson.toJson(deleteOperation, DeleteOperation.class);
                events.delete(content);

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            } finally {
                LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
            }

        });
    }


}

