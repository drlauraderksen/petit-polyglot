package family.petitpolyglot;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.Locale;

/** Shows the game (assets/index.html) full screen and lets it speak through the tablet's text-to-speech. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ready;

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

    /** Back button: exercise, then themes, then language choice, then leave the app. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){return window.goBack ? window.goBack() : 'exit';})()",
            value -> { if (value == null || value.contains("exit")) finish(); });
    }

    @Override
    protected void onPause() {
        if (ready) tts.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
