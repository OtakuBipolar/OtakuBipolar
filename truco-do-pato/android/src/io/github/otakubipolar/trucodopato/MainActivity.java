package io.github.otakubipolar.trucodopato;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int MAX_TEXT_ZOOM = 130;
    /** A página usa padding-inline/padding-block e flex gap, que pedem Chromium 87+. */
    static final int MIN_WEBVIEW_VERSION = 87;

    /** Quando o processo que desenha a página morreu pela última vez (pra não entrar em loop). */
    private static long lastRendererDeath;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    /** "pending" enquanto a voz inicia, "ok" com português, "missing" sem voz em português. */
    private volatile String ttsState = "pending";
    private Vibrator vibrator;
    private boolean stateSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android 7-13: abrir pelo "Abrir" do instalador e depois pelo ícone empilhava uma
        // segunda tela com outro WebView, e a de baixo podia sobrescrever o placar salvo.
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                    && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                finish();
                return;
            }
        }
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
        warnIfWebViewTooOld();
    }

    /** Celular com o WebView nunca atualizado abre a página quebrada; melhor avisar como resolver. */
    private void warnIfWebViewTooOld() {
        int version = chromeMajorVersion(web.getSettings().getUserAgentString());
        if (version <= 0 || version >= MIN_WEBVIEW_VERSION) return;
        new AlertDialog.Builder(this)
                .setTitle("Atualize o WebView")
                .setMessage("O componente que desenha o placar (Android System WebView) está muito antigo "
                        + "neste celular, e o app pode não funcionar direito. Atualize o \"Android System "
                        + "WebView\" e o \"Google Chrome\" na Play Store e abra o app de novo.")
                .setPositiveButton("Entendi", null)
                .show();
    }

    static int chromeMajorVersion(String userAgent) {
        if (userAgent == null) return -1;
        Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(userAgent);
        if (!m.find()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
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
        // Respeita o tamanho de fonte do sistema até 130%; acima disso o placar não cabe na tela.
        float fontScale = getResources().getConfiguration().fontScale;
        s.setTextZoom(Math.min(MAX_TEXT_ZOOM, Math.round(fontScale * 100)));
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setGeolocationEnabled(false);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        // Segurar o pato é a zoeira máxima; sem isso o Android abriria seleção de texto.
        // No campo do nome do time o toque longo passa, pra dar pra selecionar e colar.
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return ((WebView) v).getHitTestResult().getType() != WebView.HitTestResult.EDIT_TEXT_TYPE;
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

            /** Sem isto, o app inteiro cai junto quando o processo que desenha a página morre (API 26+). */
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == web) {
                    ViewGroup parent = (ViewGroup) view.getParent();
                    if (parent != null) parent.removeView(view);
                    web = null;
                }
                view.destroy();
                long now = SystemClock.elapsedRealtime();
                boolean diedAgain = lastRendererDeath != 0 && now - lastRendererDeath < 10000;
                lastRendererDeath = now;
                if (!isFinishing()) {
                    // O placar está no localStorage: recriar a tela volta tudo como estava.
                    if (diedAgain) finish();
                    else recreate();
                }
                return true;
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
                if (tts == null) return;
                if (status != TextToSpeech.SUCCESS) {
                    ttsState = "missing";
                    return;
                }
                int result = tts.setLanguage(new Locale("pt", "BR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts.setLanguage(new Locale("pt"));
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsState = "missing";
                    return;
                }
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                tts.setSpeechRate(1.15f);
                tts.setPitch(1.1f);
                ttsReady = true;
                ttsState = "ok";
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
        public String ttsState() {
            return ttsState;
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
        // Voltar fecha o menu ou encerra a edição do nome do time; se não tiver nada aberto, sai.
        web.evaluateJavascript("(window.trucoBack && window.trucoBack()) ? 1 : 0", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                // Se a resposta chegar depois do app sair da tela, ignora: no Android 7 o
                // onBackPressed depois do onSaveInstanceState derruba o app.
                if (!"1".equals(handled) && !isFinishing() && !stateSaved) {
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        stateSaved = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        stateSaved = true;
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
