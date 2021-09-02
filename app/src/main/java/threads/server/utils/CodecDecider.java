package threads.server.utils;

import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.server.core.Content;


public class CodecDecider {

    private static final String TAG = CodecDecider.class.getSimpleName();

    private String multihash = null;
    private Codec codex = Codec.UNKNOWN;

    private CodecDecider() {
    }

    public static CodecDecider evaluate(@NonNull Context context, @NonNull String code) {
        CodecDecider codecDecider = new CodecDecider();

        IPFS ipfs = IPFS.getInstance(context);

        try {
            Uri uri = Uri.parse(code);
            if (uri != null) {
                if (Objects.equals(uri.getScheme(), Content.IPFS)) {
                    String multihash = uri.getHost();
                    if (ipfs.isValidCID(multihash)) {
                        codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPFS_URI);
                        return codecDecider;
                    }
                } else if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                    String multihash = uri.getHost();

                    codecDecider.setMultihash(multihash);
                    codecDecider.setCodex(Codec.IPNS_URI);
                    return codecDecider;

                }
            }
        } catch (Throwable e) {
            // ignore exception
        }

        try {


            try {
                code = code.trim();
                if (code.startsWith("\"") && code.endsWith("\"")) {
                    code = code.substring(1, code.length() - 1);
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

            // check if multihash is valid

            if (ipfs.isValidCID(code)) {
                codecDecider.setMultihash(code);
                codecDecider.setCodex(Codec.MULTIHASH);
                return codecDecider;
            }

            if (URLUtil.isValidUrl(code)) {
                // ok now it is a URI, but is the content is an ipfs multihash

                URI uri = new URI(code);
                String path = uri.getPath();
                if (path.startsWith("/" + Content.IPFS + "/")) {
                    String multihash = path.replaceFirst("/" + Content.IPFS + "/", "");
                    multihash = trim(multihash);
                    if (ipfs.isValidCID(multihash)) {
                        codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPFS_URI);
                        return codecDecider;
                    }
                } else if (path.startsWith("/" + Content.IPNS + "/")) {
                    String multihash = path.replaceFirst("/" + Content.IPNS + "/", "");
                    multihash = trim(multihash);
                    if (ipfs.isValidCID(multihash)) {
                        codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPNS_URI);
                        return codecDecider;
                    }
                }

            }
        } catch (Throwable e) {
            // ignore exception
        }


        codecDecider.setCodex(Codec.UNKNOWN);
        return codecDecider;
    }


    private static String trim(@NonNull String data) {
        int index = data.indexOf("/");
        if (index > 0) {
            return data.substring(0, index);
        }
        return data;
    }

    public String getMultihash() {
        return multihash;
    }

    private void setMultihash(String multihash) {
        this.multihash = multihash;
    }

    public Codec getCodex() {
        return codex;
    }

    private void setCodex(Codec codex) {
        this.codex = codex;
    }


    public enum Codec {
        UNKNOWN, MULTIHASH, IPFS_URI, IPNS_URI
    }
}
