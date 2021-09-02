package threads.server.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.services.MimeTypeService;

public class NewFolderDialogFragment extends DialogFragment {
    public static final String TAG = NewFolderDialogFragment.class.getSimpleName();

    private static final int CLICK_OFFSET = 500;
    private final AtomicBoolean notPrintErrorMessages = new AtomicBoolean(false);
    private long mLastClickTime = 0;
    private TextInputEditText mNewFolder;
    private Context mContext;
    private TextInputLayout mFolderLayout;

    public static NewFolderDialogFragment newInstance(long parent) {
        Bundle bundle = new Bundle();
        bundle.putLong(Content.IDX, parent);

        NewFolderDialogFragment fragment = new NewFolderDialogFragment();
        fragment.setArguments(bundle);
        return fragment;
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
    }

    @Override
    public void onResume() {
        super.onResume();
        notPrintErrorMessages.set(true);
        isValidFolderName(getDialog());
        notPrintErrorMessages.set(false);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        Bundle args = getArguments();
        Objects.requireNonNull(args);
        long parent = args.getLong(Content.IDX, 0);

        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.new_folder, null);
        mFolderLayout = view.findViewById(R.id.folder_layout);
        mNewFolder = view.findViewById(R.id.new_folder_text);
        mNewFolder.requestFocus();
        mNewFolder.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                isValidFolderName(getDialog());
            }
        });


        builder.setView(view)
                // Add action buttons
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }

                    mLastClickTime = SystemClock.elapsedRealtime();

                    removeKeyboards();


                    Editable text = mNewFolder.getText();
                    Objects.requireNonNull(text);
                    String name = text.toString();

                    DOCS docs = DOCS.getInstance(mContext);
                    IPFS ipfs = IPFS.getInstance(mContext);
                    long idx = docs.createDocument(parent, MimeTypeService.DIR_MIME_TYPE,
                            Objects.requireNonNull(ipfs.createEmptyDir()).String(),
                            null, name, 0L, true, false);
                    docs.finishDocument(idx);

                    EVENTS.getInstance(mContext).refresh();

                })

                .setTitle(R.string.new_folder);


        Dialog dialog = builder.create();


        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private void isValidFolderName(Dialog dialog) {
        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;
            Editable text = mNewFolder.getText();
            Objects.requireNonNull(text);
            String multi = text.toString();


            boolean result = !multi.isEmpty();


            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(result);


            if (!notPrintErrorMessages.get()) {

                if (multi.isEmpty()) {
                    mFolderLayout.setError(getString(R.string.name_not_valid));
                } else {
                    mFolderLayout.setError(null);
                }

            } else {
                mFolderLayout.setError(null);
            }
        }
    }

    private void removeKeyboards() {

        try {
            InputMethodManager imm = (InputMethodManager)
                    mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mNewFolder.getWindowToken(), 0);
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

}
