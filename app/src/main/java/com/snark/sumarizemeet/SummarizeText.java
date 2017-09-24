package com.snark.sumarizemeet;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsResult;
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
    private static final int RECORDER_BPP = 16;
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static String tmp_file_name = null;
    private static String wav_file_name = null;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recording_thread = null;
    private boolean is_recording = false;

    private BarChart chart;
    List<BarEntry> entries = new ArrayList<>();
    private float startTime = 0f;

    Button mStopButton;
    private boolean completed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_summary);

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)*3;
        tmp_file_name = getExternalCacheDir().getAbsolutePath() + "/audiorecord.3gp";
        wav_file_name = getExternalCacheDir().getAbsolutePath() + "/audiorecord.wav";

        mStopButton = findViewById(R.id.btn_return);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!completed) {
                    stop_recording();
                    make_request();

                    mStopButton.setText(R.string.goback);
                } else {
                    Intent homeActivity = new Intent(SummarizeText.this, HomeActivity.class);
                    startActivity(homeActivity);
                }
                completed = !completed;
            }
        });

        chart = findViewById(R.id.chart);
        initialize_chart();
        start_recording();
    }

    private void update_chart() {
        BarDataSet set = new BarDataSet(entries, "Tones");
        set.setColors(ColorTemplate.PASTEL_COLORS);
        BarData data = new BarData(set);

        data.setBarWidth(0.9f); // set custom bar width
        chart.setData(data);
        chart.setFitBars(true); // make the x-axis fit exactly all bars

        chart.invalidate();
    }
    
    private void initialize_chart() {
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Anger", "Disgust", "Fear", "Joy", "Sadness"}));
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setDrawLabels(true);
        yAxis.setDrawAxisLine(true);
        yAxis.setDrawGridLines(false);
        yAxis.setDrawZeroLine(true);
        yAxis.setAxisMinimum(0f);
        chart.getAxisRight().setEnabled(false); // no right axis

        Description description = new Description();
        description.setText("");
        chart.setDescription(description);

        for (float i=0;i<5;i++) {
            entries.add(new BarEntry(i,0f));
        }

        update_chart();
        chart.invalidate();
    }

    private void update_entries(long ts_long, List<ToneScore> toneScores) {
        entries = new ArrayList<>();
        for (ToneScore ts : toneScores) {
            String name = ts.getId();
            float score = ts.getScore().floatValue();
            if (name.equals("anger")) {
                entries.add(new BarEntry(0, score));
            } else if (name.equals("disgust")) {
                entries.add(new BarEntry(1, score));
            } else if (name.equals("fear")) {
                entries.add(new BarEntry(2, score));
            } else if (name.equals("joy")) {
                entries.add(new BarEntry(3, score));
            } else {
                entries.add(new BarEntry(4, score));
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

                   Long ts_long = System.currentTimeMillis()/1000;
                   System.out.println(ts_long);

                   get_nlp(middle.getTranscript(), ts_long);
                   get_tone(middle.getTranscript(), ts_long);
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

    private void get_nlp(String text, Long ts_long){
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
        List<KeywordsResult> kwr = response.getKeywords();
        List<String> keywordsss = new ArrayList<String>();
        for(KeywordsResult x : kwr){
            keywordsss.add(x.getText());
        }
        System.out.println(keywordsss);
    }

    private void get_tone(String text, Long ts_long) {
        ToneAnalyzer service = new ToneAnalyzer("2016-05-19");
        service.setUsernameAndPassword("dc5d7b0c-e411-45d5-bd0e-d6e7dad51e9c", "FiUH5MYA1Z4r");

        ToneOptions options = new ToneOptions.Builder()
                .addTone(Tone.EMOTION).build();
        ToneAnalysis tone =
                service.getTone(text, options).execute();
        ElementTone el = tone.getDocumentTone();
        ToneCategory tc = (ToneCategory) el.getTones().toArray()[0];
        List<ToneScore> ts = tc.getTones();

        update_entries(ts_long, ts);
        runOnUiThread(new Runnable() {
            public void run() {
                update_chart();
            }
        });
    }


    private void start_recording() {
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize);

        int i = recorder.getState();
        if(i==1) recorder.startRecording();

        is_recording = true;

        recording_thread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");

        recording_thread.start();
    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = tmp_file_name;
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        }

        if(null != os) {
            while(is_recording) {
                if(AudioRecord.ERROR_INVALID_OPERATION != recorder.read(data, 0, bufferSize)) {
                    try {
                        os.write(data);
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stop_recording() {
        if(null != recorder) {
            is_recording = false;

            if(recorder.getState() == 1) {
                recorder.stop();
            }

            recorder.release();
            recorder = null;
            recording_thread.interrupt();
            recording_thread = null;
        }

        copyWaveFile(tmp_file_name, wav_file_name);
        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(tmp_file_name);
        file.delete();
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in;
        FileOutputStream out;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            long totalAudioLen = in.getChannel().size();
            long totalDataLen = totalAudioLen + 36;

            writeWavFileHeader(out, totalAudioLen, totalDataLen, RECORDER_SAMPLERATE, channels, byteRate);

            while(in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void writeWavFileHeader(FileOutputStream out, long totalAudioLen, long totalDataLen,
                                     long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte)(totalDataLen & 0xff);
        header[5] = (byte)((totalDataLen >> 8) & 0xff);
        header[6] = (byte)((totalDataLen >> 16) & 0xff);
        header[7] = (byte)((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte)(longSampleRate & 0xff);
        header[25] = (byte)((longSampleRate >> 8) & 0xff);
        header[26] = (byte)((longSampleRate >> 16) & 0xff);
        header[27] = (byte)((longSampleRate >> 24) & 0xff);
        header[28] = (byte)(byteRate & 0xff);
        header[29] = (byte)((byteRate >> 8) & 0xff);
        header[30] = (byte)((byteRate >> 16) & 0xff);
        header[31] = (byte)((byteRate >> 24) & 0xff);
        header[32] = (byte)(2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte)(totalAudioLen & 0xff);
        header[41] = (byte)((totalAudioLen >> 8) & 0xff);
        header[42] = (byte)((totalAudioLen >> 16) & 0xff);
        header[43] = (byte)((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}
