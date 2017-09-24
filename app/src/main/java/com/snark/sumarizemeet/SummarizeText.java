package com.snark.sumarizemeet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final int RECORDER_BPP = 16;
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static String mTmpFileName = null;
    private static String mWavFileName = null;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private LineChart chart;
    private ArrayList<Entry> angerEntries = new ArrayList<>();
    private ArrayList<Entry> disgustEntries = new ArrayList<>();
    private ArrayList<Entry> fearEntries = new ArrayList<>();
    private ArrayList<Entry> joyEntries = new ArrayList<>();
    private ArrayList<Entry> sadnessEntries = new ArrayList<>();
    private float startTime = 0f;

    Button mStopButton;
    private boolean completed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_summary);

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)*3;
        mTmpFileName = getExternalCacheDir().getAbsolutePath() + "/audiorecord.3gp";
        mWavFileName = getExternalCacheDir().getAbsolutePath() + "/audiorecord.wav";

        startRecording();

        mStopButton = findViewById(R.id.btn_return);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!completed) {
                    stopRecording();
                    mStopButton.setText(R.string.goback);
                } else {
                    Intent homeActivity = new Intent(SummarizeText.this, HomeActivity.class);
                    startActivity(homeActivity);
                }
                completed = !completed;
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


    private void startRecording() {
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize);
        int i = recorder.getState();
        if(i==1) recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");

        recordingThread.start();
    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = mTmpFileName;
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        }

        if(null != os) {
            while(isRecording) {
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

    private void stopRecording() {
        if(null != recorder) {
            isRecording = false;

            if(recorder.getState() == 1) {
                recorder.stop();
            }

            recorder.release();
            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(mTmpFileName, mWavFileName);
        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(mTmpFileName);
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

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen, RECORDER_SAMPLERATE, channels, byteRate);

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

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen, long totalDataLen,
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
