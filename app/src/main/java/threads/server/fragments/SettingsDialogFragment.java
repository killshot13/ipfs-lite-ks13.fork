package threads.server.fragments;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Objects;

import threads.server.R;
import threads.server.Settings;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.services.LiteService;

public class SettingsDialogFragment extends BottomSheetDialogFragment {
    public static final String TAG = SettingsDialogFragment.class.getSimpleName();

    private Context mContext;

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
        dialog.setContentView(R.layout.settings_view);

        SwitchMaterial enableRedirectUrl = dialog.findViewById(R.id.enable_redirect_url);
        Objects.requireNonNull(enableRedirectUrl);
        enableRedirectUrl.setChecked(Settings.isRedirectUrlEnabled(mContext));
        enableRedirectUrl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setRedirectUrlEnabled(mContext, isChecked);
                    DOCS.getInstance(mContext).refreshRedirectOptions(mContext);
                }
        );

        SwitchMaterial enableRedirectIndex = dialog.findViewById(R.id.enable_redirect_index);
        Objects.requireNonNull(enableRedirectIndex);
        enableRedirectIndex.setChecked(Settings.isRedirectIndexEnabled(mContext));
        enableRedirectIndex.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setRedirectIndexEnabled(mContext, isChecked);
                    DOCS.getInstance(mContext).refreshRedirectOptions(mContext);
                }
        );


        SwitchMaterial enableJavascript = dialog.findViewById(R.id.enable_javascript);
        Objects.requireNonNull(enableJavascript);
        enableJavascript.setChecked(Settings.isJavascriptEnabled(mContext));
        enableJavascript.setOnCheckedChangeListener((buttonView, isChecked) ->
                Settings.setJavascriptEnabled(mContext, isChecked)
        );


        TextView publisher_service_time_text = dialog.findViewById(R.id.publisher_service_time_text);
        Objects.requireNonNull(publisher_service_time_text);
        SeekBar publisher_service_time = dialog.findViewById(R.id.publisher_service_time);
        Objects.requireNonNull(publisher_service_time);


        publisher_service_time.setMin(2);
        publisher_service_time.setMax(12);
        int time = 0;
        int pinServiceTime = LiteService.getPublishServiceTime(mContext);
        if (pinServiceTime > 0) {
            time = (pinServiceTime);
        }
        publisher_service_time_text.setText(getString(R.string.publisher_service_time,
                String.valueOf(time)));
        publisher_service_time.setProgress(time);
        publisher_service_time.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                LiteService.setPublisherServiceTime(mContext, progress);

                publisher_service_time_text.setText(
                        getString(R.string.publisher_service_time,
                                String.valueOf(progress)));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }
        });

        boolean publisherEnabled = Settings.isPublisherEnabled(mContext);
        SwitchMaterial enablePublisher = dialog.findViewById(R.id.enable_publisher);
        Objects.requireNonNull(enablePublisher);
        enablePublisher.setChecked(publisherEnabled);
        enablePublisher.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setPublisherEnabled(mContext, isChecked);
                    publisher_service_time.setEnabled(isChecked);
                    publisher_service_time_text.setEnabled(isChecked);
                    EVENTS.getInstance(mContext).home();
                }
        );

        if (publisherEnabled) {
            publisher_service_time.setEnabled(true);
            publisher_service_time_text.setEnabled(true);
        } else {
            publisher_service_time.setEnabled(false);
            publisher_service_time_text.setEnabled(false);
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        return dialog;
    }

}
