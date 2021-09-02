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

import java.util.Objects;

import threads.lite.LogUtils;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;

public class RenameFileDialogFragment extends DialogFragment {
    public static final String TAG = RenameFileDialogFragment.class.getSimpleName();

    private static final int CLICK_OFFSET = 500;
    private long mLastClickTime = 0;
    private TextInputEditText mFileName;
    private Context mContext;


    public static RenameFileDialogFragment newInstance(long idx, @NonNull String name) {
        Bundle bundle = new Bundle();
        bundle.putLong(Content.IDX, idx);
        bundle.putString(Content.NAME, name);

        RenameFileDialogFragment fragment = new RenameFileDialogFragment();
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
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        Bundle args = getArguments();
        Objects.requireNonNull(args);
        long idx = args.getLong(Content.IDX, 0);
        String name = args.getString(Content.NAME, "");

        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.rename_file, null);
        mFileName = view.findViewById(R.id.file_name);


        mFileName.setText(name);

        mFileName.requestFocus();
        mFileName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {

                try {
                    AlertDialog alertDialog = ((AlertDialog) getDialog());
                    if (alertDialog != null) {
                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(s.length() > 0);
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
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


                    Editable text = mFileName.getText();
                    Objects.requireNonNull(text);

                    DOCS docs = DOCS.getInstance(mContext);
                    docs.renameDocument(idx, text.toString());

                    EVENTS.getInstance(mContext).refresh();
                })

                .setTitle(R.string.rename_file);


        Dialog dialog = builder.create();


        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private void removeKeyboards() {

        try {
            InputMethodManager imm = (InputMethodManager)
                    mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mFileName.getWindowToken(), 0);
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
