package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import net.luminis.quic.QuicConnection;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.utils.Link;
import threads.server.InitApplication;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.pages.Page;
import threads.server.services.LiteService;

public class PageWorker extends Worker {

    private static final String TAG = PageWorker.class.getSimpleName();

    private final IPFS ipfs;
    private final DOCS docs;

    @SuppressWarnings("WeakerAccess")
    public PageWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);

        ipfs = IPFS.getInstance(context);
        docs = DOCS.getInstance(context);
    }


    private static PeriodicWorkRequest getWork(@NonNull Context context) {

        int time = LiteService.getPublishServiceTime(context);

        return new PeriodicWorkRequest.Builder(PageWorker.class, time, TimeUnit.HOURS)
                .addTag(TAG)
                .build();

    }

    public static void publish(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG, ExistingPeriodicWorkPolicy.REPLACE, getWork(context));
    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, "Start " + getId().toString() + " ...");

        try {
            if (Settings.isPublisherEnabled(getApplicationContext())) {
                publishPage();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.info(TAG, "Finish " + getId().toString() +
                    " onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

    private void publishSequence(@NonNull String content, long sequence) {


        Set<PeerId> swarm = ipfs.getPeers();
        PeerId self = ipfs.getPeerID();

        for (PeerId peerId : swarm) {

            try {
                QuicConnection conn = ipfs.connect(peerId,
                        IPFS.MAX_STREAMS, true, false);
                if (conn != null) {
                    HashMap<String, String> hashMap = new HashMap<>();
                    hashMap.put(Content.IPNS, content);
                    hashMap.put(Content.PID, self.toBase58());
                    hashMap.put(Content.SEQ, "" + sequence);
                    Gson gson = new Gson();
                    String msg = gson.toJson(hashMap);
                    boolean success = ipfs.notify(conn, msg);

                    LogUtils.error(TAG, "pushing " +
                            peerId.toBase58() + " [" + success + "]");
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        }
    }

    private void publishPage() {


        Page page = docs.getPinsPage();
        if (page != null) {

            String content = page.getContent();
            Objects.requireNonNull(content);

            Executors.newSingleThreadExecutor().execute(() -> publishContent(Cid.decode(content)));


            LogUtils.error(TAG, "Start publish name " + content);

            int seq = Settings.getSequence(getApplicationContext());
            seq++;
            Settings.setSequence(getApplicationContext(), seq);

            publishSequence(content, seq);

            ipfs.publishName(Cid.decode(content), seq, this::isStopped);
        }

    }

    private void publishContent(@NonNull Cid content) {
        try {
            List<Link> links = ipfs.getLinks(content, true, this::isStopped);

            if (links != null) {
                for (Link linkInfo : links) {
                    if (linkInfo.isFile() || linkInfo.isDirectory()) {
                        LogUtils.error(TAG, "publishContent " + linkInfo.getName());
                        Executors.newSingleThreadExecutor().execute(() ->
                                publishContent(linkInfo.getCid()));
                    }
                }
            }

            ipfs.provide(content, this::isStopped);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

}

