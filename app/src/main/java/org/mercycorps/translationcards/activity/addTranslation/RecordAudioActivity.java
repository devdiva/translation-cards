package org.mercycorps.translationcards.activity.addTranslation;

import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.mercycorps.translationcards.R;
import org.mercycorps.translationcards.data.Translation;
import org.mercycorps.translationcards.exception.AudioFileException;
import org.mercycorps.translationcards.exception.RecordAudioException;
import org.mercycorps.translationcards.media.MediaConfig;
import org.mercycorps.translationcards.media.AudioRecorderManager;

import java.io.IOException;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class RecordAudioActivity extends AddTranslationActivity {
    private static final String TAG = "RecordAudioActivity";

    @Bind(R.id.play_audio_button)
    RelativeLayout playAudioButton;
    @Bind(R.id.record_audio_button)
    RelativeLayout recordAudioButton;
    @Bind(R.id.origin_translation_text)
    TextView originTranslationText;
    @Bind(R.id.record_activity_back)
    LinearLayout backButton;
    @Bind(R.id.record_activity_next)
    LinearLayout nextButton;
    @Bind(R.id.recording_audio_next_text)
    TextView nextButtonText;
    @Bind(R.id.recording_audio_save_image)
    ImageView nextButtonArrow;
    @Bind(R.id.indicator_icon)
    ImageView translationCardIndicatorIcon;
    @Bind(R.id.translation_child)
    LinearLayout translationChild;
    @Bind(R.id.translation_grandchild)
    LinearLayout translationGrandChild;
    @Bind(R.id.translated_text)
    TextView translatedTextView;
    @Bind(R.id.audio_icon_layout)
    FrameLayout audioIconLayout;
    @Bind({R.id.record_activity_back, R.id.record_activity_next})
    List<LinearLayout> backAndNext;

    @Override
    public void inflateView() {
        setContentView(R.layout.activity_record_audio);
    }

    @Override
    public void initStates() {
        updatePlayButtonState();
        showTranslationSourcePhrase();
        updateTranslatedTextView();
        updateNextButtonState();
        expandTranslationCard();
        hideGrandchildAndAudioIcon();
    }

    @OnClick(R.id.record_activity_next)
    public void recordActivityNextClick() {
        stopAudioIfPlaying();
        stopIfRecording();
        startNextActivity(RecordAudioActivity.this, SummaryActivity.class);
    }

    @OnClick(R.id.record_activity_back)
    public void recordActivityBackClick() {
        stopAudioIfPlaying();
        stopIfRecording();
        startNextActivity(RecordAudioActivity.this, EnterTranslatedPhraseActivity.class);
    }

    @OnClick(R.id.record_audio_button)
    public void recordAudioButtonClick() {
        stopAudioIfPlaying();
        tryToRecord();
        updateRecordButtonState();
        updateBackAndNextButtonStates();
        updatePlayButtonState();
    }

    @OnClick(R.id.play_audio_button)
    public void playAudioButtonClick() {
        try {
            stopIfRecording();
            playAudioFile();
        } catch (AudioFileException e) {
            Log.d(TAG, "Error getting audio asset: " + e);
            showToast(e.getLocalizedMessage());
        }
    }

    @OnClick(R.id.translation_indicator_layout)
    protected void translationIndicatorLayoutClick(){
        int visibility = isTranslationChildVisible() ? View.GONE : View.VISIBLE;
        int backgroundResource = isTranslationChildVisible() ? R.drawable.expand_arrow : R.drawable.collapse_arrow;
        translationChild.setVisibility(visibility);
        translationCardIndicatorIcon.setBackgroundResource(backgroundResource);
    }

    protected void expandTranslationCard() {
        translationCardIndicatorIcon.setBackgroundResource(R.drawable.collapse_arrow);
        translationChild.setVisibility(View.VISIBLE);
    }

    protected void hideGrandchildAndAudioIcon() {
        translationGrandChild.setVisibility(View.GONE);
        audioIconLayout.setVisibility(View.GONE);
    }

    private void updateTranslatedTextView() {
        String translatedText = getContextFromIntent().getTranslation().getTranslatedText();
        if (translatedText.isEmpty()) {
            translatedTextView.setHint(String.format("Add %s translation", getContextFromIntent().getDictionary().getLabel()));
        } else {
            translatedTextView.setText(translatedText);
        }
    }

    private void updateNextButtonState() {
        Boolean isAudioFilePresent = getContextFromIntent().getTranslation().isAudioFilePresent();
        nextButton.setClickable(isAudioFilePresent);
        int nextButtonTextColor = isAudioFilePresent ? R.color.primaryTextColor : R.color.textDisabled;
        nextButtonText.setTextColor(ContextCompat.getColor(this, nextButtonTextColor));
        int nextButtonArrowColor = isAudioFilePresent ? R.drawable.forward_arrow : R.drawable.forward_arrow_40p;
        nextButtonArrow.setBackgroundResource(nextButtonArrowColor);
    }

    protected void showBackButton() {
        backButton.setVisibility(View.VISIBLE);
    }

    private void showTranslationSourcePhrase() {
        originTranslationText.setText(getContextFromIntent().getTranslation().getLabel());
    }

    private void updateBackAndNextButtonStates() {
        ButterKnife.apply(backAndNext, VISIBILITY, getVisibility());
        updateNextButtonState();
    }

    private int getVisibility() {
        boolean translationHasAudioFile = getContextFromIntent().getTranslation().isAudioFilePresent();
        return translationHasAudioFile && !getAudioRecorderManager().isRecording() ? View.VISIBLE : View.GONE;
    }

    private void updatePlayButtonState() {
        boolean translationHasAudioFile = getContextFromIntent().getTranslation().isAudioFilePresent();
        if (translationHasAudioFile) {
            playAudioButton.setBackgroundResource(R.color.green);
        }
        playAudioButton.setClickable(translationHasAudioFile);
    }

    private void stopIfRecording() {
        if (getAudioRecorderManager().isRecording()) {
            getAudioRecorderManager().stop();
            updateBackAndNextButtonStates();
            updateRecordButtonState();
        }
    }

    private void updateRecordButtonState() {
        if (getAudioRecorderManager().isRecording()) {
            recordAudioButton.setBackgroundResource(R.color.deep_red);
        } else {
            recordAudioButton.setBackgroundResource(R.color.red);
        }
    }

    private boolean isTranslationChildVisible() {
        return translationChild.getVisibility() == View.VISIBLE;
    }

    protected void stopAudioIfPlaying() {
        if (getAudioPlayerManager().isPlaying()) {
            getAudioPlayerManager().stop();
        }
    }


    private void tryToRecord() {
        try {
            handleIsRecordingState();
        } catch (RecordAudioException e) {
            Log.d(TAG, "Error creating file for recording: " + e);
            showToast(getString(R.string.unable_to_record_message));
        }
    }

    private void playAudioFile() throws AudioFileException {
        try {
            Translation translation = getContextFromIntent().getTranslation();
            getAudioPlayerManager().play(translation.getFilename(), translation.getIsAsset());
        } catch (IOException e) {
            throw new AudioFileException("Unable to play audio file", e);
        }
    }

    private void handleIsRecordingState() throws RecordAudioException {
        AudioRecorderManager audioRecorderManager = getAudioRecorderManager();
        if (audioRecorderManager.isRecording()) {
            audioRecorderManager.stop();
        } else {
            MediaConfig mediaConfig = MediaConfig.createMediaConfig();
            getContextFromIntent().setAudioFile(mediaConfig.getAbsoluteFilePath());
            updateIsAudioFileAsset();
            audioRecorderManager.record(mediaConfig);
        }
    }

    private void updateIsAudioFileAsset() {
        if (getContextFromIntent().getTranslation().getIsAsset()) {
            getContextFromIntent().getTranslation().setIsAsset(false);
        }
    }

}
