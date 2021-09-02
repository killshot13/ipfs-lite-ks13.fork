package threads.server.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.LogUtils;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.THREADS;
import threads.server.services.MimeTypeService;
import threads.server.utils.CodecDecider;
import threads.server.work.UploadContentWorker;

public class EditContentDialogFragment extends DialogFragment {
    public static final String TAG = EditContentDialogFragment.class.getSimpleName();

    private static final int CLICK_OFFSET = 500;
    private final AtomicBoolean notPrintErrorMessages = new AtomicBoolean(false);
    private final AtomicBoolean ipns = new AtomicBoolean(false);
    private long mLastClickTime = 0;
    private TextInputLayout mEditMultihashLayout;
    private TextInputEditText mMultihash;
    private Context mContext;
    private final ActivityResultLauncher<Intent> mScanRequestForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    IntentResult resultIntent = IntentIntegrator.parseActivityResult(
                            IntentIntegrator.REQUEST_CODE, result.getResultCode(), result.getData());
                    if (resultIntent != null) {
                        if (resultIntent.getContents() != null) {
                            CodecDecider codecDecider = CodecDecider.evaluate(mContext,
                                    resultIntent.getContents());
                            String codec = handleCodec(codecDecider);
                            if (!codec.isEmpty()) {
                                mMultihash.setText(codec);
                            } else {
                                EVENTS.getInstance(mContext).error(getString(R.string.codec_not_supported));
                            }
                        }
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

            });
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    invokeScan();
                } else {
                    EVENTS.getInstance(mContext).permission(
                            getString(R.string.permission_camera_denied));
                }
            });
    private boolean hasCamera;

    public static EditContentDialogFragment newInstance(@Nullable String cid, boolean ipns) {
        Bundle bundle = new Bundle();
        if (cid != null) {
            bundle.putString(Content.CID, cid);
        }
        bundle.putBoolean(Content.IPNS, ipns);
        EditContentDialogFragment fragment = new EditContentDialogFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    public static void download(@NonNull Context context, @NonNull Uri uri) {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                DOCS docs = DOCS.getInstance(context);
                THREADS threads = THREADS.getInstance(context);

                String host = uri.getHost();
                Objects.requireNonNull(host);


                long idx = docs.createDocument(0L, MimeTypeService.OCTET_MIME_TYPE,
                        null, uri, host, 0, false, true);

                UUID work = UploadContentWorker.load(context, idx, false);
                threads.setThreadWork(idx, work);

                EVENTS.getInstance(context).warning(host);


            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            } finally {
                LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        PackageManager pm = mContext.getPackageManager();
        hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private void clickInvokeScan() {

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        invokeScan();
    }

    private void invokeScan() {
        try {
            PackageManager pm = mContext.getPackageManager();

            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
                integrator.setOrientationLocked(false);
                Intent intent = integrator.createScanIntent();
                mScanRequestForResult.launch(intent);
            } else {
                EVENTS.getInstance(mContext).permission(
                        getString(R.string.feature_camera_required));
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @NonNull
    private String handleCodec(@NonNull CodecDecider codecDecider) {
        if (codecDecider.getCodex() == CodecDecider.Codec.MULTIHASH) {
            return codecDecider.getMultihash();
        } else if (codecDecider.getCodex() == CodecDecider.Codec.IPFS_URI) {
            ipns.set(false);
            return codecDecider.getMultihash();
        } else if (codecDecider.getCodex() == CodecDecider.Codec.IPNS_URI) {
            ipns.set(true);
            return codecDecider.getMultihash();
        }
        return "";
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);
        String content = bundle.getString(Content.CID);

        ipns.set(bundle.getBoolean(Content.IPNS, false));


        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.content_view, null);
        mEditMultihashLayout = view.findViewById(R.id.edit_content_layout);
        mMultihash = view.findViewById(R.id.multihash);

        if (content != null) {
            mMultihash.setText(content);
        }

        mMultihash.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                isValidMultihash(getDialog());
            }
        });

        TextView mScanContent = view.findViewById(R.id.scan_content);
        if (!hasCamera) {
            mScanContent.setVisibility(View.GONE);
        } else {
            mScanContent.setOnClickListener((v) -> clickInvokeScan());
        }

        builder.setView(view)
                // Add action buttons
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }

                    mLastClickTime = SystemClock.elapsedRealtime();

                    removeKeyboards();


                    Editable text = mMultihash.getText();
                    Objects.requireNonNull(text);
                    String hash = getValidMultihash(text.toString());


                    String uri;
                    if (ipns.get()) {
                        uri = Content.IPNS + "://" + hash;
                    } else {
                        uri = Content.IPFS + "://" + hash;
                    }

                    download(mContext, Uri.parse(uri));

                })

                .setTitle(getString(R.string.content_url));


        Dialog dialog = builder.create();


        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }


        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        return dialog;
    }

    private void isValidMultihash(Dialog dialog) {
        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;
            Editable text = mMultihash.getText();
            Objects.requireNonNull(text);
            String multi = text.toString();


            boolean result = !getValidMultihash(multi).isEmpty();


            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(result);


            if (!notPrintErrorMessages.get()) {

                if (!result) {
                    mEditMultihashLayout.setError(getString(R.string.cid_not_valid));
                } else {
                    mEditMultihashLayout.setError(null);
                }

            } else {
                mEditMultihashLayout.setError(null);
            }
        }
    }

    @NonNull
    private String getValidMultihash(@Nullable String multi) {

        String multihash = "";

        if (multi != null && !multi.isEmpty()) {
            CodecDecider decider = CodecDecider.evaluate(mContext, multi);
            return handleCodec(decider);
        }

        return multihash;

    }

    private void removeKeyboards() {

        try {
            InputMethodManager imm = (InputMethodManager)
                    mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mMultihash.getWindowToken(), 0);
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        removeKeyboards();
    }

    @Override
    public void onResume() {
        super.onResume();
        notPrintErrorMessages.set(true);
        isValidMultihash(getDialog());
        notPrintErrorMessages.set(false);
    }

}
