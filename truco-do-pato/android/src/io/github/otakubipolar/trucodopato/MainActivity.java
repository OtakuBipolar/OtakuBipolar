package io.github.otakubipolar.trucodopato;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;

/**
 * Casca nativa do Truco do Pato: um WebView em tela cheia com o app web empacotado
 * em assets/www, mais o que o navegador não dá dentro do APK (voz, vibração, tela acesa).
 */
public class MainActivity extends Activity {

    static final String APP_URL = "file:///android_asset/www/index.html";
    private static final String APP_PREFIX = "file:///android_asset/www/";
    private static final int FELT_DEEP = 0xFF072A1D;
    private static final int MAX_VIBRATION_STEPS = 12;
    private static final long MAX_VIBRATION_MS = 1000;
    private static final int MAX_SPEECH_CHARS = 60;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Placar de mesa: a tela não apaga no meio da partida.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Botões de volume mexem no volume de mídia, que é onde o quá toca.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(FELT_DEEP);
        web = new WebView(this);
        web.setBackgroundColor(FELT_DEEP);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        fitInsideSystemBars(root);

        configureWebView();
        vibrator = findVibrator();
        startSpeech();
        web.loadUrl(APP_URL);
    }

    /**
     * No Android 15+ o app desenha atrás das barras do sistema. O WebView não repassa isso
     * pro CSS, então o espaço das barras, do entalhe e do teclado vira padding nativo.
     */
    private void fitInsideSystemBars(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return WindowInsets.CONSUMED;
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);            // placar salvo no localStorage
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);             // assets continuam acessíveis
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);                      // layout de tela única não aguenta fonte gigante
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setGeolocationEnabled(false);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        // Segurar o pato é a zoeira máxima; sem isso o Android abriria seleção de texto.
        web.setHapticFeedbackEnabled(false);
        web.setLongClickable(false);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAppUrl(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAppUrl(url);
            }
        });
        web.addJavascriptInterface(new Bridge(), "TrucoAndroid");
    }

    static boolean isAppUrl(String url) {
        return url != null && url.startsWith(APP_PREFIX) && !url.contains("..");
    }

    /* ---------- Voz do narrador ---------- */

    private void startSpeech() {
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS || tts == null) return;
                int result = tts.setLanguage(new Locale("pt", "BR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts.setLanguage(new Locale("pt"));
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    return;
                }
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                tts.setSpeechRate(1.15f);
                tts.setPitch(1.1f);
                ttsReady = true;
            }
        });
    }

    /* ---------- Vibração ---------- */

    @SuppressWarnings("deprecation")
    private Vibrator findVibrator() {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm == null ? null : vm.getDefaultVibrator();
        } else {
            v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        return v != null && v.hasVibrator() ? v : null;
    }

    /** "60,40,90" = vibra 60 ms, pausa 40, vibra 90 (mesmo formato do navigator.vibrate). */
    static long[] parsePattern(String pattern) {
        if (pattern == null) return null;
        String[] parts = pattern.split(",");
        int n = Math.min(parts.length, MAX_VIBRATION_STEPS);
        long[] out = new long[n];
        long total = 0;
        for (int i = 0; i < n; i++) {
            long ms;
            try {
                ms = Long.parseLong(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
            out[i] = Math.max(0, Math.min(MAX_VIBRATION_MS, ms));
            total += out[i];
        }
        return total > 0 ? out : null;
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long[] steps) {
        if (vibrator == null || steps == null) return;
        // O Android começa o padrão com uma pausa; o navegador começa vibrando.
        long[] timings = new long[steps.length + 1];
        System.arraycopy(steps, 0, timings, 1, steps.length);
        if (Build.VERSION.SDK_INT >= 26) {
            if (steps.length == 1) {
                vibrator.vibrate(VibrationEffect.createOneShot(steps[0], VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
            }
        } else {
            vibrator.vibrate(timings, -1);
        }
    }

    /** O que o JavaScript enxerga como window.TrucoAndroid. Roda fora da thread principal. */
    final class Bridge {
        @JavascriptInterface
        public boolean canSpeak() {
            return ttsReady;
        }

        @JavascriptInterface
        public void speak(String text) {
            final TextToSpeech engine = tts;
            if (!ttsReady || engine == null || text == null) return;
            String phrase = text.length() > MAX_SPEECH_CHARS ? text.substring(0, MAX_SPEECH_CHARS) : text;
            engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "truco");
        }

        @JavascriptInterface
        public void vibrate(String pattern) {
            final long[] steps = parsePattern(pattern);
            if (steps == null) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.vibrate(steps);
                }
            });
        }
    }

    /* ---------- Ciclo de vida ---------- */

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        // Voltar fecha o menu ou o teclado do nome do time; se não tiver nada aberto, sai.
        web.evaluateJavascript("(window.trucoBack && window.trucoBack()) ? 1 : 0", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                if (!"1".equals(handled) && !isFinishing()) {
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();
            web.pauseTimers();
        }
        if (tts != null) tts.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ttsReady = false;
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (web != null) {
            web.removeJavascriptInterface("TrucoAndroid");
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
