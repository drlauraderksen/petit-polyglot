package family.petitpolyglot;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.util.Locale;

/**
 * Shows the game (assets/index.html) full screen, reads words aloud with the tablet's
 * text-to-speech, and records the child's voice for the "say it" step. Recordings stay in the
 * app's cache folder, are overwritten each time, and never leave the tablet.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MIC_REQUEST = 7;
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ready;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File recFile;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.setBackgroundColor(0xFFDDF0FF);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // progress and profiles are kept in the app's own storage
        tts = new TextToSpeech(getApplicationContext(), this);
        web.addJavascriptInterface(new Bridge(), "AndroidTTS");
        web.addJavascriptInterface(new RecBridge(), "AndroidRec");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onInit(int status) {
        ready = status == TextToSpeech.SUCCESS;
    }

    /** Norwegian is "nb-NO" on some speech engines and "no-NO" on others. */
    private Locale pick(String lang) {
        Locale wanted = Locale.forLanguageTag(lang);
        if (lang.startsWith("nb") && tts.isLanguageAvailable(wanted) < TextToSpeech.LANG_AVAILABLE) {
            Locale other = Locale.forLanguageTag("no-NO");
            if (tts.isLanguageAvailable(other) >= TextToSpeech.LANG_AVAILABLE) return other;
        }
        return wanted;
    }

    private class Bridge {
        @JavascriptInterface
        public void speak(String text, String lang) {
            speakRate(text, lang, 0.9f);
        }

        @JavascriptInterface
        public void speakRate(String text, String lang, float rate) {
            if (!ready) return;
            try {
                tts.setLanguage(pick(lang));
                tts.setSpeechRate(rate);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word");
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public boolean isReady() { return ready; }

        @JavascriptInterface
        public boolean hasVoice(String lang) {
            if (!ready) return false;
            try {
                return tts.isLanguageAvailable(pick(lang)) >= TextToSpeech.LANG_AVAILABLE;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void stop() {
            if (ready) try { tts.stop(); } catch (Exception ignored) { }
        }
    }

    /** Lets the page record a few seconds of the child's voice and play it back. */
    private class RecBridge {
        @JavascriptInterface
        public boolean canRecord() {
            return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void askPermission() {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST));
        }

        @JavascriptInterface
        public void start() { runOnUiThread(MainActivity.this::startRec); }

        @JavascriptInterface
        public void stop() { runOnUiThread(MainActivity.this::stopRec); }

        @JavascriptInterface
        public void play() { runOnUiThread(MainActivity.this::playRec); }

        @JavascriptInterface
        public void halt() { runOnUiThread(() -> { stopRec(); stopPlay(); }); }
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private void startRec() {
        stopRec();
        stopPlay();
        if (ready) tts.stop();
        try {
            recFile = new File(getCacheDir(), "say.m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            releaseRec();
            js("window.onRecError&&window.onRecError()");
        }
    }

    private void stopRec() {
        if (recorder == null) return;
        try { recorder.stop(); } catch (Exception ignored) { }
        releaseRec();
    }

    private void releaseRec() {
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private void stopPlay() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void playRec() {
        final String done = "window.onPlayDone&&window.onPlayDone()";
        stopPlay();
        try {
            if (recFile == null || !recFile.exists() || recFile.length() < 200) { js(done); return; }
            player = new MediaPlayer();
            player.setDataSource(recFile.getAbsolutePath());
            player.setOnCompletionListener(mp -> { stopPlay(); js(done); });
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopPlay();
            js(done);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == MIC_REQUEST) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            js("window.onMicPermission&&window.onMicPermission(" + ok + ")");
        }
    }

    /** Back button: exercise, then themes, then language choice, then players, then leave the app. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){return window.goBack ? window.goBack() : 'exit';})()",
            value -> { if (value == null || value.contains("exit")) finish(); });
    }

    @Override
    protected void onPause() {
        if (ready) tts.stop();
        stopRec();
        stopPlay();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        stopRec();
        stopPlay();
        super.onDestroy();
    }
}
