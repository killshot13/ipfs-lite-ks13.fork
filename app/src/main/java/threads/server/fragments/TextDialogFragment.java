package threads.server.fragments;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Objects;

import threads.lite.LogUtils;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.events.EVENTS;
import threads.server.services.UploadService;

@SuppressWarnings("WeakerAccess")
public class TextDialogFragment extends BottomSheetDialogFragment {
    public static final String TAG = TextDialogFragment.class.getSimpleName();
    private static final int CLICK_OFFSET = 500;
    private Context mContext;
    private long mLastClickTime = 0;
    private TextView mTextEdit;

    public static TextDialogFragment newInstance(long parent) {


        Bundle bundle = new Bundle();
        bundle.putLong(Content.IDX, parent);
        TextDialogFragment fragment = new TextDialogFragment();
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

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.text_view);

        mTextEdit = dialog.findViewById(R.id.text_edit);
        Objects.requireNonNull(mTextEdit);
        TextView sendAction = dialog.findViewById(R.id.text_action);
        Objects.requireNonNull(sendAction);
        TextView abortAction = dialog.findViewById(R.id.abort_action);
        Objects.requireNonNull(abortAction);


        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);

        long parent = bundle.getLong(Content.IDX);


        mTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                sendAction.setEnabled(s.length() > 0);
            }
        });


        abortAction.setOnClickListener((v) -> dismiss());

        sendAction.setEnabled(false);
        sendAction.setOnClickListener((v) -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                removeKeyboards();

                CharSequence text = mTextEdit.getText();
                String content = "";
                if (text != null) {
                    content = text.toString();
                    content = content.trim();
                }

                if (!content.isEmpty()) {
                    UploadService.storeText(mContext, parent, content, true);
                } else {
                    EVENTS.getInstance(mContext).error(
                            mContext.getString(R.string.empty_text));
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            } finally {
                dismiss();
            }
        });


        dialog.setOnShowListener(dialog1 -> {
            BottomSheetDialog d = (BottomSheetDialog) dialog1;

            FrameLayout bottomSheet = d.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }

        });

        if (mTextEdit.requestFocus()) {
            try {

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }

        return dialog;
    }

    private void removeKeyboards() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mTextEdit.getWindowToken(), 0);
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
    }


}
