package org.mercycorps.translationcards.activity.addTranslation;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.mercycorps.translationcards.MainApplication;
import org.mercycorps.translationcards.R;
import org.mercycorps.translationcards.activity.translations.TranslationsActivity;
import org.mercycorps.translationcards.model.Translation;
import org.mercycorps.translationcards.exception.AudioFileException;
import org.mercycorps.translationcards.media.DecoratedMediaManager;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static org.mercycorps.translationcards.fragment.TranslationTabsFragment.*;

public class SummaryActivity extends AddTranslationActivity {
    private static final String TAG = "SummaryActivity";
    public static final int DISABLED_OPACITY = 250;
    public static final int DEFAULT_OPACITY = 255;
    public static final int DEFAULT_BITMAP_OPACITY = 255;
    public static final int DISABLED_BITMAP_OPACITY = 100;
    @Bind(R.id.origin_translation_text)TextView sourceTextView;
    @Bind(R.id.translated_text)TextView translatedTextView;
    @Bind(R.id.translation_child)LinearLayout translationChildLayout;
    @Bind(R.id.translation_grandchild)LinearLayout translationGrandchildLayout;
    @Bind(R.id.summary_detail)TextView summaryDetail;
    @Bind(R.id.summary_progress_bar)ProgressBar progressBar;
    @Bind(R.id.indicator_icon)ImageView indicatorIcon;
    @Bind(R.id.audio_icon)ImageView audioIcon;
    @Bind(R.id.translation_card_parent)LinearLayout translationCardParent;

    @Override
    public void inflateView() {
        setContentView(R.layout.activity_summary);
    }

    @Override
    public void setBitmapsForActivity() {
        setBitmap(R.id.enter_source_language_image, R.drawable.enter_source_language_image);
    }

    @Override
    public void initStates(){
        inflateLanguageTabsFragment();
        setOnLanguageTabClickListener();
        setTranslationCardChildrenVisibility();
        updateTranslatedTextView();
        greyOutCardIfNoAudioTranslation();
        updateSummaryTextView();
        indicatorIcon.setBackgroundResource(R.drawable.collapse_arrow);
    }


    @OnClick(R.id.save_translation_button)
    protected void summaryDoneClicked() {
        saveTranslation();
        stopMediaManager();
        startNextActivity(SummaryActivity.this, TranslationsActivity.class);
    }

    @OnClick(R.id.summary_translation_card)
    protected void translationCardClicked() {
        Translation translation = getLanguageTabsFragment().getCurrentTranslation().getTranslation();
        try {
            DecoratedMediaManager mediaManager = getDecoratedMediaManager();
            if(mediaManager.isPlaying()) {
                mediaManager.stop();
            } else {
                mediaManager.play(translation.getFilename(), progressBar, translation.getIsAsset());
            }
        } catch (AudioFileException e) {
            String message = String.format(getString(R.string.could_not_play_audio_message),
                    getLanguageTabsFragment().getCurrentTranslation().getDictionary().getLanguage());
            showToast(message);
            Log.d(TAG, e.getMessage());
        }
    }

    @OnClick(R.id.summary_activity_back)
    protected void summaryBackClicked(){
        stopMediaManager();
        startNextActivity(SummaryActivity.this, RecordAudioActivity.class);
    }

    @OnClick(R.id.translation_indicator_layout)
    protected void indicatorLayoutClicked() {
        int visibility = isTranslationChildVisible() ? View.GONE : View.VISIBLE;
        int backgroundResource = isTranslationChildVisible() ? R.drawable.expand_arrow : R.drawable.collapse_arrow;
        translationChildLayout.setVisibility(visibility);
        indicatorIcon.setBackgroundResource(backgroundResource);
    }

    private void updateSummaryTextView() {
        boolean audioRecorded = getLanguageTabsFragment().getCurrentTranslation().getTranslation().isAudioFilePresent();
        int detailText = audioRecorded ? R.string.activity_summary_instructions : R.string.summary_detail_no_audio;
        summaryDetail.setText(detailText);
    }

    private void setOnLanguageTabClickListener() {
        getLanguageTabsFragment().setOnLanguageTabSelectedListener(new OnLanguageTabSelectedListener() {
            @Override
            public void onLanguageTabSelected(NewTranslation previousTranslation) {
                updateTranslatedTextView();
                updateSummaryTextView();
                greyOutCardIfNoAudioTranslation();
                stopMediaManager();
            }
        });
    }

    private void setTranslationCardChildrenVisibility() {
        translationChildLayout.setVisibility(View.VISIBLE);
        translationGrandchildLayout.setVisibility(View.GONE);
    }

    static final ButterKnife.Setter<View, Boolean> INVISIBLE = new ButterKnife.Setter<View, Boolean>() {
        @Override public void set(View view, Boolean value, int index) {
            view.setEnabled(value);
        }
    };

    private void updateTranslatedTextView() {
        String translatedText = getLanguageTabsFragment().getCurrentTranslation().getTranslation().getTranslatedText();
        if (translatedText.isEmpty()) {
            translatedTextView.setHint(String.format("Add %s translation", getLanguageTabsFragment().getCurrentTranslation().getDictionary().getLanguage()));
        }

        updateTextInTextView(translatedTextView, translatedText);
    }
    private void greyOutCardIfNoAudioTranslation() {
        Drawable audioIconDrawable=audioIcon.getDrawable();
        View v = findViewById(R.id.translation_card_parent);
        LayerDrawable bgDrawable = (LayerDrawable)v.getBackground();
        GradientDrawable cardTopBackgroundDrawable = (GradientDrawable)   bgDrawable.findDrawableByLayerId(R.id.card_top_background);
        if(!getLanguageTabsFragment().getCurrentTranslation().getTranslation().isAudioFilePresent()){
            cardTopBackgroundDrawable.setAlpha(DISABLED_OPACITY);
            sourceTextView.setTextColor(ContextCompat.getColor(this, R.color.textDisabled));
            audioIconDrawable.setAlpha(DISABLED_BITMAP_OPACITY);
        }
        else{
            cardTopBackgroundDrawable.setAlpha(DEFAULT_OPACITY);
            sourceTextView.setTextColor(ContextCompat.getColor(this, R.color.primaryTextColor));
            audioIconDrawable.setAlpha(DEFAULT_BITMAP_OPACITY);
        }
        updateTextInTextView(sourceTextView, getContextFromIntent().getSourcePhrase());
    }



    private void updateTextInTextView(TextView textView, String textToBeUpdated){
        textView.setText(textToBeUpdated);
    }

    private void stopMediaManager() {
        DecoratedMediaManager mediaManager = getDecoratedMediaManager();
        if (mediaManager.isPlaying()) {
            mediaManager.stop();
        }
    }

    private boolean isTranslationChildVisible() {
        return translationChildLayout.getVisibility() == View.VISIBLE;
    }

    private DecoratedMediaManager getDecoratedMediaManager(){
        return getMainApplication().getDecoratedMediaManager();
    }

    private void saveTranslation() {
        getContextFromIntent().save();
    }
}
