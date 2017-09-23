package com.snark.sumarizemeet;

import android.app.Activity;
import android.os.Bundle;

public class SummarizeText extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_summary);
        make_request();
    }
    


    private void make_request(){
        SpeechToText service = new SpeechToText();
        service.setUsernameAndPassword("b06f630e-1815-4fa5-b35b-3a229829d8b1", "5lIGB3ugweLZ");

        RecognizeOptions options = new RecognizeOptions.Builder()
                .model("en-US_BroadbandModel").contentType("audio/wav")
                .interimResults(true).maxAlternatives(0)
                .build();

        BaseRecognizeCallback callback = new BaseRecognizeCallback() {
            @Override
            public void onTranscription(SpeechResults speechResults) {
                System.out.println(speechResults);




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
}
