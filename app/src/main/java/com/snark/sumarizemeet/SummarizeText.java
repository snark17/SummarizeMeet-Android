package com.snark.sumarizemeet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.github.mikephil.charting.charts.BarChart;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechAlternative;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.Transcript;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.ToneAnalyzer;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ElementTone;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.Tone;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneAnalysis;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneCategory;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneOptions;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneScore;

public class SummarizeText extends Activity {
    Button mReturnButton;
    private WebView wv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_summary);

        mReturnButton = findViewById(R.id.btn_return);
        mReturnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent extAudioRecorder = new Intent(SummarizeText.this, ExtAudioRecorder.class);
                startActivity(extAudioRecorder);
            }
        });

        create_chart();
        make_request();
    }

    private void create_chart() {


        BarChart chart = findViewById(R.id.chart);
    }
    private void make_request(){
        SpeechToText service = new SpeechToText();
        service.setUsernameAndPassword("b06f630e-1815-4fa5-b35b-3a229829d8b1", "5lIGB3ugweLZ");

        RecognizeOptions options = new RecognizeOptions.Builder()
                .model("en-US_BroadbandModel").contentType("audio/wav")
                .interimResults(true)
                .maxAlternatives(0)
                .build();

        BaseRecognizeCallback callback = new BaseRecognizeCallback() {
            @Override
            public void onTranscription(SpeechResults speechResults) {
               if (speechResults.isFinal()) {

                  Transcript value = (Transcript) speechResults.getResults().toArray()[0];
                   SpeechAlternative middle = (SpeechAlternative) value.getAlternatives().toArray()[0];
                   System.out.println(middle.getTranscript());

                   Long tsLong = System.currentTimeMillis()/1000;
                   System.out.println(tsLong);

                   get_nlp(middle.getTranscript(), tsLong);
                   get_tone(middle.getTranscript(), tsLong);





               }
            }

            @Override
            public void onDisconnected() {
                //System.exit(0);
            }
        };

        try {
            service.recognizeUsingWebSocket
                    (new FileInputStream(getExternalCacheDir().getAbsolutePath() + "/audiorecord.wav"), options, callback);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }
    private void get_nlp(String text, Long tsLong){
        NaturalLanguageUnderstanding service = new NaturalLanguageUnderstanding(
                NaturalLanguageUnderstanding.VERSION_DATE_2017_02_27,
                "87dd97fe-25de-4ea0-97ff-765925ac659f",
                "oShSDWvUoTeY"
        );

        EntitiesOptions entitiesOptions = new EntitiesOptions.Builder()
                .emotion(true)
                .sentiment(true)
                .limit(2)
                .build();

        KeywordsOptions keywordsOptions = new KeywordsOptions.Builder()
                .emotion(true)
                .sentiment(true)
                .limit(2)
                .build();

        Features features = new Features.Builder()
                .entities(entitiesOptions)
                .keywords(keywordsOptions)
                .build();

        AnalyzeOptions parameters = new AnalyzeOptions.Builder()
                .text(text)
                .features(features)
                .build();

        AnalysisResults response = service
                .analyze(parameters)
                .execute();
        System.out.println(response.toString());

    }
    private void get_tone(String text, Long tsLong) {
        ToneAnalyzer service = new ToneAnalyzer("2016-05-19");
        service.setUsernameAndPassword("dc5d7b0c-e411-45d5-bd0e-d6e7dad51e9c", "FiUH5MYA1Z4r");

        ToneOptions options = new ToneOptions.Builder()
                .addTone(Tone.EMOTION).build();
        ToneAnalysis tone =
                service.getTone(text, options).execute();
        ElementTone el = tone.getDocumentTone();
        ToneCategory tc = (ToneCategory) el.getTones().toArray()[0];
        List<ToneScore> ts = tc.getTones();
        System.out.println(ts);
    }
}
