package threads.server.services;

import android.content.Context;

import androidx.annotation.NonNull;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;

public class LocalConnectService {

    private static final String TAG = LocalConnectService.class.getSimpleName();

    public static void connect(@NonNull Context context, @NonNull String pid,
                               @NonNull String host, int port, boolean inet6) {

        try {
            IPFS ipfs = IPFS.getInstance(context);

            PeerId peerId = PeerId.fromBase58(pid);
            ipfs.swarmEnhance(peerId);

            String pre = "/ip4";
            if (inet6) {
                pre = "/ip6";
            }
            String multiAddress = pre + host + "/udp/" + port + "/quic";

            ipfs.addMultiAddress(peerId, new Multiaddr(multiAddress));

            LogUtils.error(TAG, "Success " + pid + " " + multiAddress);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

}

