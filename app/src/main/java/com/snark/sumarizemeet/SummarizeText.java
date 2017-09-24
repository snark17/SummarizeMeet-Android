package com.snark.sumarizemeet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
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
    private LineChart chart;
    private ArrayList<Entry> angerEntries = new ArrayList<>();
    private ArrayList<Entry> disgustEntries = new ArrayList<>();
    private ArrayList<Entry> fearEntries = new ArrayList<>();
    private ArrayList<Entry> joyEntries = new ArrayList<>();
    private ArrayList<Entry> sadnessEntries = new ArrayList<>();
    private float startTime = 0f;

    Button mReturnButton;

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

        initialize_data();
        make_request();
        chart = findViewById(R.id.chart);
        update_chart();
        initialize_chart();
    }

    private void initialize_data() {
        angerEntries.add(new Entry(0f, 0f));
        disgustEntries.add(new Entry(0f, 0f));
        fearEntries.add(new Entry(0f, 0f));
        joyEntries.add(new Entry(0f, 0f));
        sadnessEntries.add(new Entry(0f, 0f));
    }

    private void update_chart() {
        LineDataSet angerDataSet = new LineDataSet(angerEntries, "Anger");
        angerDataSet.setColor(Color.BLUE);
        LineDataSet disgustDataSet = new LineDataSet(disgustEntries, "Disgust");
        disgustDataSet.setColor(Color.RED);
        LineDataSet fearDataSet = new LineDataSet(fearEntries, "Fear");
        fearDataSet.setColor(Color.GREEN);
        LineDataSet joyDataSet = new LineDataSet(joyEntries, "Joy");
        joyDataSet.setColor(Color.YELLOW);
        LineDataSet sadnessDataSet = new LineDataSet(sadnessEntries, "Sadness");
        sadnessDataSet.setColor(Color.MAGENTA);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(angerDataSet);
        dataSets.add(disgustDataSet);
        dataSets.add(fearDataSet);
        dataSets.add(joyDataSet);
        dataSets.add(sadnessDataSet);

        LineData lineData = new LineData(dataSets);
        chart.setData(lineData);
        chart.invalidate();
    }
    
    private void initialize_chart() {
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setAxisMinimum(0f);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setDrawLabels(true);
        yAxis.setDrawAxisLine(true);
        yAxis.setDrawGridLines(false);
        yAxis.setDrawZeroLine(true);
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(1f);
        chart.getAxisRight().setEnabled(false); // no right axis
    }

    private void update_entries(long tsLong, List<ToneScore> toneScores) {
        if (startTime == 0) {
            startTime = tsLong;
        }
        tsLong -= startTime;
        for (ToneScore ts : toneScores) {
            String name = ts.getId();
            System.out.println("NAME " + name);
            float score = ts.getScore().floatValue();
            if (name.equals("anger")) {
                System.out.println("adding anger");
                angerEntries.add(new Entry(tsLong, score));
            } else if (name.equals("disgust")) {
                disgustEntries.add(new Entry(tsLong, score));
            } else if (name.equals("fear")) {
                fearEntries.add(new Entry(tsLong, score));
            } else if (name.equals("joy")) {
                joyEntries.add(new Entry(tsLong, score));
            } else {
                sadnessEntries.add(new Entry(tsLong, score));
            }
        }
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
            public void onDisconnected() {}
        };

        try {
            service.recognizeUsingWebSocket(new FileInputStream(getExternalCacheDir().getAbsolutePath() + "/audiorecord.wav"), options, callback);
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
        update_entries(tsLong, ts);
        System.out.println(ts);
    }
}
