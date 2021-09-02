package threads.server;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.luminis.quic.QuicConnection;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.pages.PAGES;
import threads.server.core.pages.Page;
import threads.server.work.InitApplicationWorker;

public class InitApplication extends Application {
    public static final int USER_GRACE_PERIOD = 60 * 60 * 24;

    private static final String TAG = InitApplication.class.getSimpleName();
    private final Gson gson = new Gson();

    private static void createChannel(@NonNull Context context) {

        try {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            NotificationChannel mChannel = new NotificationChannel(
                    Settings.CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            mChannel.setDescription(description);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }


    @Override
    public void onCreate() {
        super.onCreate();

        createChannel(getApplicationContext());

        long time = System.currentTimeMillis();
        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            ipfs.relays();
            ipfs.setPusher(this::onMessageReceived);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.error(TAG, "finish start daemon ... " +
                    (System.currentTimeMillis() - time));
        }

        InitApplicationWorker.initialize(getApplicationContext());

    }

    public void onMessageReceived(@NonNull QuicConnection conn, @NonNull String content) {

        try {
            Type hashMap = new TypeToken<HashMap<String, String>>() {
            }.getType();

            Objects.requireNonNull(conn);
            IPFS ipfs = IPFS.getInstance(getApplicationContext());

            Objects.requireNonNull(content);
            Map<String, String> data = gson.fromJson(content, hashMap);

            LogUtils.error(TAG, "Push Message : " + data.toString());

            String ipns = data.get(Content.IPNS);
            Objects.requireNonNull(ipns);
            String pid = data.get(Content.PID);
            Objects.requireNonNull(pid);
            String seq = data.get(Content.SEQ);
            Objects.requireNonNull(seq);

            PeerId peerId = PeerId.fromBase58(pid);
            long sequence = Long.parseLong(seq);
            if (sequence >= 0) {
                if (ipfs.isValidCID(ipns)) {
                    PAGES pages = PAGES.getInstance(getApplicationContext());
                    Page page = pages.createPage(peerId.toBase58());
                    page.setContent(ipns);
                    page.setSequence(sequence);
                    pages.storePage(page);
                }
            }

            DOCS.getInstance(getApplicationContext()).addResolves(peerId, ipns);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
