package com.ajinkyadube.voicedeck;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.ajinkyadube.voicedeck.data.local.VoiceDeckDatabase;
import com.ajinkyadube.voicedeck.data.local.entity.ContactAlias;
import com.ajinkyadube.voicedeck.data.local.entity.SpeechDictionaryEntry;
import com.ajinkyadube.voicedeck.data.repository.ContactAliasRepository;
import com.ajinkyadube.voicedeck.data.repository.SpeechDictionaryRepository;
import com.ajinkyadube.voicedeck.speech.SpeechCommandFilter;
import com.ajinkyadube.voicedeck.speech.SpeechDictionaryDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // VoiceDeck visual design tokens — presentation only.
    private static final int COLOR_BACKGROUND = Color.rgb(244, 239, 229); // #F4EFE5
    private static final int COLOR_SURFACE = Color.rgb(255, 252, 246);     // #FFFCF6
    private static final int COLOR_PRIMARY = Color.rgb(71, 102, 78);       // #47664E
    private static final int COLOR_TEXT = Color.rgb(40, 53, 45);            // #28352D
    private static final int COLOR_SECONDARY = Color.rgb(119, 117, 104);   // #777568
    private static final int COLOR_SAGE = Color.rgb(229, 235, 221);        // #E5EBDD
    private static final int COLOR_BORDER = Color.rgb(229, 222, 209);      // #E5DED1
    private static final int REQ_PERMISSIONS = 100;
    private static final int REQ_CONTACTS = 101;
    private static final int REQ_AUDIO = 102;

    private LinearLayout root;
    private FrameLayout appHost;
    private View splashOverlay;
    private AnimatorSet splashAnimations;
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private Runnable splashFinish;
    private LinearLayout content;
    private TextView statusText;
    private TextView headerBack;
    private TextView aliasSetupLink;
    private String pendingAliasForSetup;
    private TextView heardText;
    private SpeechRecognizer speechRecognizer;
    private String language = "en-IN";
    private final List<ContactItem> contacts = new ArrayList<>();
    private ContactNameMatcher contactNameMatcher;
    private SpeechDictionaryRepository speechDictionaryRepository;
    private ContactAliasRepository contactAliasRepository;
    private boolean contactsLoaded;
    private boolean contactMatcherReady;
    private boolean contactAliasesLoading;
    private String contactMatcherLanguage;
    private String contactAliasesLoadingLanguage;
    private int contactLoadGeneration;
    private int contactAliasesLoadingGeneration;
    private int contactAliasRequestId;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private SpeechCommandFilter speechCommandFilter = SpeechCommandFilter.empty();
    private boolean speechDictionaryReady;
    private boolean showingDictionaryManager;
    private boolean showingAliasManager;
    private String dictionaryManagerLanguage = "en-IN";
    private String pendingContactQuery;
    private boolean pendingAliasCreationAllowed;
    private List<ContactItem> currentMatches = new ArrayList<>();
    private int currentIndex = 0;
    private boolean showingBack = false;
    private boolean cardTransitionRunning = false;
    private boolean permissionsRequested = false;
    private int currentFigmaLayout = 0;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        language = getPreferences(MODE_PRIVATE).getString("language", "en-IN");
        buildBase();
        initializeSpeechDictionary();
        showAnimatedSplash();
    }

    private Typeface manropeTypeface;

    private Typeface getManrope() {
        if (manropeTypeface == null) {
            manropeTypeface = getResources().getFont(R.font.manrope);
        }
        return manropeTypeface;
    }

    private Typeface dmSansTypeface;

    private Typeface getDmSans() {
        if (dmSansTypeface == null) {
            dmSansTypeface = getResources().getFont(R.font.dm_sans);
        }
        return dmSansTypeface;
    }

    private void setDmSans(TextView view, int weight) {
        Typeface font = getDmSans();
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            view.setTypeface(Typeface.create(font, weight, false));
        } else {
            view.setTypeface(font, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void applyManrope(View view) {
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            int style = label.getTypeface() != null ? label.getTypeface().getStyle() : Typeface.NORMAL;
            label.setTypeface(getManrope(), style);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyManrope(group.getChildAt(i));
            }
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildBase() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        //root.setPadding(dp(16), dp(44), dp(16), dp(16));
        root.setPadding(0, dp(44), 0, dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        headerBack = centeredText("‹", 24, false, COLOR_PRIMARY);
        headerBack.setGravity(Gravity.CENTER);
        headerBack.setVisibility(View.GONE);
        headerBack.setContentDescription("Back");
        headerBack.setClickable(true);
        headerBack.setFocusable(true);
        headerBack.setOnClickListener(v -> onBackPressed());
        top.addView(headerBack, new LinearLayout.LayoutParams(dp(42), dp(48)));
        TextView brand = new TextView(this);
        brand.setText("VoiceDeck");
        brand.setTextSize(24);
        setDmSans(brand, 700);
        brand.setTextColor(COLOR_TEXT);
        brand.setTranslationY(dp(10));
        brand.setTranslationX(dp(10));
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView menu = centeredText("⋮", 30, true, COLOR_TEXT);
        menu.setGravity(Gravity.CENTER);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setMinWidth(dp(48));
        menu.setMinHeight(dp(48));
        menu.setContentDescription("More options");
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, menu);
            popup.getMenu().add("Settings").setOnMenuItemClickListener(item -> {
                showSettings();
                return true;
            });
            popup.show();
        });
        top.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, dp(8), 0, dp(12));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        statusText = new TextView(this);
        statusText.setTypeface(getManrope());
        statusText.setTextColor(COLOR_SECONDARY);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(statusText);

        aliasSetupLink = centeredText(
                "Would you like to save this as a nickname for an existing contact?\nSelect a contact to link this nickname.",
                15, true, COLOR_PRIMARY);
        aliasSetupLink.setPadding(dp(18), dp(4), dp(18), dp(14));
        aliasSetupLink.setVisibility(View.GONE);
        aliasSetupLink.setClickable(true);
        aliasSetupLink.setFocusable(true);
        aliasSetupLink.setPaintFlags(aliasSetupLink.getPaintFlags()
                | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        aliasSetupLink.setContentDescription(
                "Save this unmatched name as a nickname for an existing contact");
        aliasSetupLink.setOnClickListener(v -> {
            String alias = pendingAliasForSetup;
            if (alias != null && !alias.trim().isEmpty()) showAliasContactPicker(alias);
        });
        root.addView(aliasSetupLink);

        applyManrope(root);
        setDmSans(brand, 700); // Preserve DM Sans after applying default Manrope.
        appHost = new FrameLayout(this);
        appHost.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(appHost);
        showListeningPrompt(false);
    }

    /** Startup-only animation; never runs the microphone or requests audio access. */
    private void showAnimatedSplash() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(COLOR_BACKGROUND);
        overlay.setClickable(true);
        splashOverlay = overlay;

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.CENTER);
        overlay.addView(center, centerParams);

        FrameLayout artwork = new FrameLayout(this);
        center.addView(artwork, new LinearLayout.LayoutParams(dp(200), dp(200)));

        View[] rings = new View[2];
        for (int i = 0; i < rings.length; i++) {
            View ring = new View(this);
            GradientDrawable ringShape = new GradientDrawable();
            ringShape.setShape(GradientDrawable.OVAL);
            ringShape.setColor(Color.TRANSPARENT);
            ringShape.setStroke(dp(2), COLOR_PRIMARY);
            ring.setBackground(ringShape);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                    dp(94), dp(94), Gravity.CENTER);
            artwork.addView(ring, rp);
            rings[i] = ring;
        }

        FrameLayout micCircle = new FrameLayout(this);
        micCircle.setBackground(roundedBackground(COLOR_SAGE, Color.TRANSPARENT, 100));
        artwork.addView(micCircle, new FrameLayout.LayoutParams(
                dp(82), dp(82), Gravity.CENTER));
        ImageView mic = new ImageView(this);
        mic.setImageResource(R.drawable.ic_mic);
        mic.setContentDescription("VoiceDeck microphone logo");
        micCircle.addView(mic, new FrameLayout.LayoutParams(
                dp(36), dp(36), Gravity.CENTER));

        TextView title = centeredText("VoiceDeck", 30, true, COLOR_TEXT);
        setDmSans(title, 700);
        center.addView(title);
        TextView subtitle = centeredText("Your contacts, just a voice away.",
                14, false, COLOR_SECONDARY);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(8);
        center.addView(subtitle, subParams);

        appHost.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        splashAnimations = new AnimatorSet();
        java.util.ArrayList<android.animation.Animator> animations = new java.util.ArrayList<>();
        for (int i = 0; i < rings.length; i++) {
            View ring = rings[i];
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 0.85f, 2.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 0.85f, 2.0f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.5f, 0f);
            for (ObjectAnimator a : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
                a.setDuration(1500);
                a.setStartDelay(i * 750L);
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setInterpolator(new LinearInterpolator());
                animations.add(a);
            }
        }
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(micCircle, View.SCALE_X, 1f, 1.09f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(micCircle, View.SCALE_Y, 1f, 1.09f, 1f);
        for (ObjectAnimator a : new ObjectAnimator[]{pulseX, pulseY}) {
            a.setDuration(1200);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            animations.add(a);
        }
        splashAnimations.playTogether(animations);
        if (android.provider.Settings.Global.getFloat(getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f) {
            splashAnimations.start();
        }
        splashFinish = () -> {
            if (isFinishing() || isDestroyed()) return;
            overlay.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                if (splashAnimations != null) splashAnimations.cancel();
                appHost.removeView(overlay);
                splashOverlay = null;
                splashAnimations = null;
                ensurePermissionsAndStart();
            }).start();
        };
        splashHandler.postDelayed(splashFinish, 1800);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(COLOR_TEXT);
        if (bold) t.setTypeface(Typeface.create(getManrope(), Typeface.BOLD));
        t.setTypeface(getManrope(), bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private GradientDrawable roundedBackground(int color, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private TextView centeredText(String value, int size, boolean bold, int color) {
        TextView view = text(value, size, bold);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private AlertDialog createStyledDialog(View panel, String positiveLabel,
                                           android.content.DialogInterface.OnClickListener positiveAction,
                                           String negativeLabel,
                                           android.content.DialogInterface.OnClickListener negativeAction) {
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setPositiveButton(positiveLabel, positiveAction)
                .setNegativeButton(negativeLabel, negativeAction).create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            }
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(COLOR_PRIMARY);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_SECONDARY);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(16);
            }
        });
        applyManrope(panel);
        return dialog;
    }

    private LinearLayout dialogPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(8));
        panel.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
        return panel;
    }

    private TextView dialogTitle(String value) {
        TextView title = text(value, 22, true);
        title.setTextColor(COLOR_TEXT);
        title.setPadding(0, 0, 0, dp(16));
        return title;
    }

    private void styleActionButton(Button button, int backgroundColor, int textColor,
                                   int strokeColor, int minHeightDp) {
        button.setTypeface(getManrope(), Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.create(getManrope(), Typeface.BOLD));
        button.setMinHeight(dp(minHeightDp));
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(roundedBackground(backgroundColor, strokeColor, 18));
        if (android.os.Build.VERSION.SDK_INT >= 21) button.setBackgroundTintList(null);
        if (android.os.Build.VERSION.SDK_INT >= 21) button.setElevation(dp(2));
    }

    private View showFigmaScreen(int layoutId) {
        currentFigmaLayout = layoutId;
        if (statusText != null) statusText.setVisibility(layoutId == R.layout.screen_contact ? View.GONE : View.VISIBLE);
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        content.setPadding(0, 0, 0, 0);
        View screen = getLayoutInflater().inflate(layoutId, content, false);
        int availableHeight = (int) (getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density) - 150;
        content.addView(screen, new LinearLayout.LayoutParams(-1, Math.max(dp(480), dp(availableHeight))));
        applyManrope(screen);
        return screen;
    }

    private void setTextIfPresent(View rootView, int id, CharSequence value) {
        TextView view = rootView.findViewById(id);
        if (view != null) view.setText(value);
    }

    private void showListeningPrompt(boolean listening) {
        setSecondaryHeader(false);
        showingAliasManager = false;
        showingDictionaryManager = false;
        if (aliasSetupLink != null) aliasSetupLink.setVisibility(View.GONE);
        pendingAliasForSetup = null;

        View screen = showFigmaScreen(listening ? R.layout.screen_listening : R.layout.screen_home);
        if (!listening) {
            Button speak = screen.findViewById(R.id.action_speak);
            Button manual = screen.findViewById(R.id.action_manual_search);
            if (speak != null) speak.setOnClickListener(v -> startListening());
            if (manual != null) manual.setOnClickListener(v -> showManualSearch());
            statusText.setText("");
        } else {
            TextView heard = screen.findViewById(R.id.listening_heard_text);
            if (heard != null) heardText = heard;
            Button cancel = screen.findViewById(R.id.action_cancel_listening);
            Button stop = screen.findViewById(R.id.action_stop_listening);
            if (cancel != null) cancel.setOnClickListener(v -> {
                finishListeningSession();
                showListeningPrompt(false);
            });
            if (stop != null) stop.setOnClickListener(v -> {
                if (speechRecognizer != null) {
                    try { speechRecognizer.stopListening(); } catch (Exception ignored) { finishListeningSession(); }
                }
            });
            statusText.setText("");
        }
    }

    private void ensurePermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
            showListeningPrompt(false);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERMISSIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ContactItem selected = getSelectedContact();
                if (selected != null) placeDirectCall(selected);
            } else {
                Toast.makeText(this, "Phone permission denied. Opening the dialler instead.", Toast.LENGTH_LONG).show();
                ContactItem selected = getSelectedContact();
                if (selected != null) openDialler(selected);
            }
            return;
        }
        if (requestCode == REQ_AUDIO) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                // Permission granted; user must tap the microphone again to begin listening.
                showListeningPrompt(false);
                statusText.setText("Microphone ready. Tap the microphone when you want to search.");
            } else {
                showListeningPrompt(false);
                statusText.setText("Microphone permission is off. Enable it in Android Settings to use voice search.");
                showMicrophoneSettingsDialog();
            }
            return;
        }
        if (requestCode == REQ_PERMISSIONS) {
            boolean contactsOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (contactsOk) {
                loadContacts();
                showListeningPrompt(false);
                statusText.setText("Ready when you are. Tap the microphone to start voice search.");
            } else {
                showListeningPrompt(false);
                statusText.setText("Allow Contacts access to search your contacts. You can still use manual search when access is granted.");
                Toast.makeText(this, "Allow Contacts access to use VoiceDeck.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadContacts() {
        contacts.clear();
        contactsLoaded = false;
        contactMatcherReady = false;
        contactNameMatcher = null;
        final int generation = ++contactLoadGeneration;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactsLoaded = true;
            loadContactAliases(generation);
            return;
        }
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        };
        Set<Long> seen = new HashSet<>();
        try (Cursor c = getContentResolver().query(uri, projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    if (seen.contains(id)) continue;
                    seen.add(id);
                    ContactItem item = new ContactItem();
                    item.id = id;
                    item.name = safe(c.getString(1));
                    item.phone = safe(c.getString(2));
                    String photo = c.getString(3);
                    if (photo != null) item.photo = Uri.parse(photo);
                    contacts.add(item);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not read contacts.", Toast.LENGTH_LONG).show();
        }
        for (ContactItem item : contacts) loadExtraDetails(item);
        contactsLoaded = true;
        // Rebuild the local name index only after aliases for the active language load.
        loadContactAliases(generation);
    }

    /** Loads only aliases for the active speech language and ignores stale contact IDs. */
    private void loadContactAliases(int contactGeneration) {
        if (contactAliasRepository == null) {
            contactMatcherReady = false;
            return;
        }

        final String requestedLanguage = language;
        if (contactAliasesLoading
                && requestedLanguage.equals(contactAliasesLoadingLanguage)
                && contactGeneration == contactAliasesLoadingGeneration) {
            return;
        }

        contactMatcherReady = false;
        contactAliasesLoading = true;
        contactAliasesLoadingLanguage = requestedLanguage;
        contactAliasesLoadingGeneration = contactGeneration;
        final int requestId = ++contactAliasRequestId;

        final Set<Long> currentContactIds = new HashSet<>();
        for (ContactItem contact : contacts) {
            if (contact != null) currentContactIds.add(contact.id);
        }

        contactAliasRepository.getAliasesForLanguage(
                requestedLanguage,
                new ContactAliasRepository.ResultCallback<List<ContactAlias>>() {
                    @Override public void onSuccess(List<ContactAlias> aliases) {
                        if (isFinishing() || isDestroyed()) return;
                        if (requestId != contactAliasRequestId
                                || contactGeneration != contactLoadGeneration) return;
                        if (!requestedLanguage.equals(language)) {
                            loadContactAliases(contactLoadGeneration);
                            return;
                        }
                        contactAliasesLoading = false;
                        contactNameMatcher = new ContactNameMatcher(
                                contacts, ContactAliasMatcherMap.fromAliases(aliases, currentContactIds));
                        contactMatcherLanguage = requestedLanguage;
                        contactMatcherReady = true;
                        processPendingContactQuery();
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        if (requestId != contactAliasRequestId
                                || contactGeneration != contactLoadGeneration) return;
                        if (!requestedLanguage.equals(language)) {
                            loadContactAliases(contactLoadGeneration);
                            return;
                        }
                        contactAliasesLoading = false;
                        // Keep ordinary contact-name search available if alias storage fails.
                        contactNameMatcher = new ContactNameMatcher(contacts, Collections.emptyMap());
                        contactMatcherLanguage = requestedLanguage;
                        contactMatcherReady = true;
                        processPendingContactQuery();
                    }
                }
        );
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private void loadExtraDetails(ContactItem item) {
        String[] projection = {ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2};
        try (Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection,
                ContactsContract.Data.CONTACT_ID + "=?", new String[]{String.valueOf(item.id)}, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String mime = c.getString(0);
                String value = safe(c.getString(1));
                if (value.isEmpty()) continue;
                if (ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime) && item.email.isEmpty())
                    item.email = value;
                else if (ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime) && item.company.isEmpty())
                    item.company = value;
                else if (ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mime) && item.birthday.isEmpty())
                    item.birthday = value;
                else if (ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime) && item.address.isEmpty())
                    item.address = value;
            }
        } catch (Exception ignored) {}
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showListeningPrompt(false);
            statusText.setText("VoiceDeck needs microphone permission to listen for a contact name.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showListeningPrompt(false);
            statusText.setText("Speech recognition is unavailable on this device. Use manual search.");
            return;
        }
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    showListeningPrompt(true);
                }
                @Override public void onBeginningOfSpeech() {
                    if (heardText != null) heardText.setText("Listening…");
                }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    statusText.setText("Processing your request…");
                }
                @Override public void onError(int error) {
                    finishListeningSession();
                    showListeningPrompt(false);
                    statusText.setText(speechErrorMessage(error));
                }
                @Override public void onResults(Bundle results) {
                    finishListeningSession();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) findContact(matches.get(0), true);
                    else {
                        showListeningPrompt(false);
                        statusText.setText("No speech recognised. Try again.");
                    }

                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> p = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (p != null && !p.isEmpty() && heardText != null) heardText.setText(p.get(0));

                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            speechRecognizer.startListening(intent);
            showListeningPrompt(true);
        } catch (Exception e) {
            showListeningPrompt(false);
            statusText.setText("Could not start voice search. Try manual search.");
        }
    }

    private void showMicrophoneSettingsDialog() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Microphone access")
                .setMessage("To search by voice, allow microphone access for VoiceDeck. Also check that your phone's speech-recognition app (such as Google) is allowed to use the microphone. You can still use manual search.")
                .setPositiveButton("Open VoiceDeck settings", (dialog, which) -> {
                    Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    try { startActivity(settingsIntent); }
                    catch (Exception e) { Toast.makeText(this, "Open VoiceDeck in Android App settings.", Toast.LENGTH_LONG).show(); }
                })
                .setNeutralButton("Try again", (dialog, which) -> startListening())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void finishListeningSession() {
        SpeechRecognizer active = speechRecognizer;
        speechRecognizer = null;
        if (active != null) {
            try { active.cancel(); } catch (Exception ignored) {}
            try { active.destroy(); } catch (Exception ignored) {}
        }
    }

    private String speechErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Microphone audio problem. Check that the microphone is working and try again.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Voice search was interrupted by Android. Tap Start voice search to try again.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                runOnUiThread(() -> showMicrophoneSettingsDialog());
                return "Android's speech service reports that microphone access is blocked. Check VoiceDeck and your phone's speech-recognition app permissions.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Speech service network error. Check your internet connection and try again.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Speech service timed out. Check your internet connection and try again.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "I couldn't identify that name. Please speak clearly or search manually.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Voice recognition is busy. Wait a moment, then try again.";
            case SpeechRecognizer.ERROR_SERVER:
                return "The speech service had a problem. Please try again in a moment.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "I didn't hear anything. Tap Start voice search and say a contact name.";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                return "This speech service does not support the selected language. Choose another language in Settings.";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return "The selected language is not available yet. Check your speech recognition language settings or choose another language.";
            default:
                return "Voice search stopped (error " + error + "). Please try again or search manually.";
        }
    }

    private void initializeSpeechDictionary() {
        speechDictionaryReady = false;
        VoiceDeckDatabase database = VoiceDeckDatabase.getInstance(this);
        speechDictionaryRepository = new SpeechDictionaryRepository(
                database,
                databaseExecutor,
                command -> runOnUiThread(command)
        );
        contactAliasRepository = new ContactAliasRepository(
                database,
                databaseExecutor,
                command -> runOnUiThread(command)
        );

        // INSERT OR IGNORE makes bundled defaults idempotent. Existing user edits,
        // including disabled entries, are preserved by the unique dictionary index.
        speechDictionaryRepository.insertAll(
                SpeechDictionaryDefaults.createEntries(),
                new SpeechDictionaryRepository.ResultCallback<List<Long>>() {
                    @Override public void onSuccess(List<Long> ignored) {
                        if (isFinishing() || isDestroyed()) return;
                        loadSpeechDictionary();
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        applyBundledDictionaryFallback(language);
                    }
                }
        );
    }

    private void loadSpeechDictionary() {
        if (speechDictionaryRepository == null) {
            initializeSpeechDictionary();
            return;
        }

        final String requestedLanguage = language;
        speechDictionaryReady = false;
        speechDictionaryRepository.getEnabledEntries(
                requestedLanguage,
                new SpeechDictionaryRepository.ResultCallback<List<SpeechDictionaryEntry>>() {
                    @Override public void onSuccess(List<SpeechDictionaryEntry> entries) {
                        if (isFinishing() || isDestroyed()) return;
                        if (!requestedLanguage.equals(language)) {
                            loadSpeechDictionary();
                            return;
                        }
                        List<String> terms = SpeechDictionaryDefaults.termsForLanguage(
                                entries, requestedLanguage);
                        speechCommandFilter = new SpeechCommandFilter(terms);
                        speechDictionaryReady = true;
                        processPendingContactQuery();
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        applyBundledDictionaryFallback(requestedLanguage);
                    }
                }
        );
    }

    private void applyBundledDictionaryFallback(String requestedLanguage) {
        if (!requestedLanguage.equals(language)) {
            loadSpeechDictionary();
            return;
        }
        List<String> fallbackTerms = SpeechDictionaryDefaults.termsForLanguage(
                SpeechDictionaryDefaults.createEntries(), requestedLanguage);
        speechCommandFilter = new SpeechCommandFilter(fallbackTerms);
        speechDictionaryReady = true;
        processPendingContactQuery();
    }

    private void processPendingContactQuery() {
        if (pendingContactQuery == null || !speechDictionaryReady
                || !contactMatcherReady || !language.equals(contactMatcherLanguage)) return;
        String query = pendingContactQuery;
        boolean allowAliasCreation = pendingAliasCreationAllowed;
        pendingContactQuery = null;
        pendingAliasCreationAllowed = false;
        findContactNow(query, allowAliasCreation);
    }

    private void findContact(String spoken, boolean allowAliasCreation) {
        if (!speechDictionaryReady) {
            // Speech recognition can finish before the local Room dictionary loads.
            // Hold only the latest query and resume it when loading completes.
            pendingContactQuery = spoken;
            pendingAliasCreationAllowed = allowAliasCreation;
            statusText.setText("Preparing local voice dictionary…");
            return;
        }
        findContactNow(spoken, allowAliasCreation);
    }

    private void findContactNow(String spoken, boolean allowAliasCreation) {
        if (!contactsLoaded) loadContacts();

        if (!contactMatcherReady || !language.equals(contactMatcherLanguage)) {
            pendingContactQuery = spoken;
            pendingAliasCreationAllowed = allowAliasCreation;
            statusText.setText("Loading saved contact names…");
            loadContactAliases(contactLoadGeneration);
            return;
        }

        ContactNameMatcher.Result result = contactNameMatcher.resolve(spoken, speechCommandFilter);

        if (result.getStatus() == ContactNameMatcher.Status.NO_MATCH) {
            String alias = aliasFromSpokenText(spoken);
            showListeningPrompt(false);
            statusText.setText("Contact not found\nWe couldn't find a matching contact for “"
                    + spoken + "”.");
            if (allowAliasCreation && !alias.isEmpty()) {
                pendingAliasForSetup = alias;
                aliasSetupLink.setVisibility(View.VISIBLE);
            }
            return;
        }

        currentMatches = new ArrayList<>(result.getMatches());

        if (result.getStatus() == ContactNameMatcher.Status.MATCH_FOUND) {
            currentIndex = 0;
            showCard(false);
        } else {
            showMatchChooser(spoken, currentMatches);
        }
    }

    /**
     * Extracts the words left after the active language's command/greeting/filler
     * dictionary has been applied. Only voice searches may start alias creation.
     */
    private String aliasFromSpokenText(String spoken) {
        List<String> tokens = speechCommandFilter.filterTokens(spoken);
        if (tokens.isEmpty()) return "";
        StringBuilder alias = new StringBuilder();
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) continue;
            if (alias.length() > 0) alias.append(' ');
            alias.append(token.trim());
        }
        return alias.toString().trim();
    }

    /** First step of voice-led alias creation: choose the real device contact. */
    private void showAliasContactPicker(String alias) {
        if (contacts.isEmpty()) {
            showListeningPrompt(false);
            statusText.setText("No contacts with phone numbers are available to link to this name.");
            return;
        }

        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("Who do you mean?"));
        TextView hint = text("Choose the contact you want VoiceDeck to remember as “"
                + alias + "”.", 15, false);
        hint.setTextColor(COLOR_SECONDARY);
        hint.setPadding(0, 0, 0, dp(10));
        panel.addView(hint);

        EditText search = new EditText(this);
        search.setTypeface(getManrope());
        search.setSingleLine(true);
        search.setTextSize(17);
        search.setHint("Search contacts");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(roundedBackground(COLOR_BACKGROUND, COLOR_BORDER, 14));
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(56)));

        List<ContactItem> visibleContacts = new ArrayList<>(contacts);
        ArrayAdapter<ContactItem> adapter = new ArrayAdapter<ContactItem>(
                this, android.R.layout.simple_list_item_1, visibleContacts) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setText(getItem(position).name);
                row.setTextSize(18);
                row.setTextColor(COLOR_TEXT);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(8), dp(14), dp(8));
                row.setMinHeight(dp(58));
                return row;
            }
        };

        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(new android.graphics.drawable.ColorDrawable(COLOR_BORDER));
        list.setDividerHeight(dp(1));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, dp(390));
        listParams.topMargin = dp(8);
        panel.addView(list, listParams);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setNegativeButton("Cancel", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            ContactItem selected = visibleContacts.get(position);
            dialog.dismiss();
            confirmRememberAlias(alias, selected);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                visibleContacts.clear();
                for (ContactItem contact : contacts) {
                    String name = contact.name == null ? "" : contact.name.toLowerCase(Locale.ROOT);
                    String phone = contact.phone == null ? "" : contact.phone.toLowerCase(Locale.ROOT);
                    if (query.isEmpty() || name.contains(query) || phone.contains(query)) {
                        visibleContacts.add(contact);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_SECONDARY);
        });
        dialog.show();
    }

    /** Ask explicitly before saving the locally spoken name for the selected contact. */
    private void confirmRememberAlias(String alias, ContactItem contact) {
        if (contact == null || alias == null || alias.trim().isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Remember this name?")
                .setMessage("Should I remember “" + alias + "” as “"
                        + contact.name + "”?")
                .setPositiveButton("Yes, remember", (dialog, which) ->
                        saveContactAlias(alias, contact))
                .setNegativeButton("No", null)
                .show();
    }

    private void saveContactAlias(String alias, ContactItem contact) {
        if (contactAliasRepository == null) {
            Toast.makeText(this, "Alias storage is not ready. Please try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String normalized = SpeechCommandFilter.normalizeTerm(alias);
        if (normalized.isEmpty()) {
            Toast.makeText(this, "That spoken name cannot be saved.", Toast.LENGTH_SHORT).show();
            return;
        }

        ContactAlias entry = new ContactAlias(
                contact.id, alias.trim(), normalized, language, System.currentTimeMillis());
        contactAliasRepository.insert(entry,
                new ContactAliasRepository.ResultCallback<Long>() {
                    @Override public void onSuccess(Long id) {
                        if (isFinishing() || isDestroyed()) return;
                        if (id == null || id <= 0) {
                            Toast.makeText(MainActivity.this,
                                    "I already remember that name for this contact.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        // Rebuild the in-memory matcher from the actual Room database.
                        loadContactAliases(contactLoadGeneration);
                        showListeningPrompt(false);
                        statusText.setText("I'll remember “" + alias + "” as “"
                                + contact.name + "”.");
                        Toast.makeText(MainActivity.this, "I'll remember that!",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(MainActivity.this,
                                "Couldn't save that name. Please try again.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showMatchChooser(String spoken, List<ContactItem> matches) {
        setSecondaryHeader(true);
        View screen = showFigmaScreen(R.layout.screen_matches);
        LinearLayout list = screen.findViewById(R.id.match_list);
        if (list != null) {
            list.removeAllViews();
            for (int i = 0; i < matches.size(); i++) {
                final int selectedIndex = i;
                ContactItem match = matches.get(i);
                TextView row = text(match.name + "\n" +
                        (match.phone == null || match.phone.isEmpty()
                                ? "No mobile number saved" : match.phone) + "   ›", 16, true);
                row.setPadding(dp(14), dp(16), dp(14), dp(16));
                row.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 14));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = dp(8);
                list.addView(row, params);
                row.setOnClickListener(v -> {
                    currentMatches = new ArrayList<>(matches);
                    currentIndex = selectedIndex;
                    showCard(false);
                });
            }
        }
        Button none = screen.findViewById(R.id.action_none_matches);
        if (none != null) none.setOnClickListener(v -> showManualSearch());
        statusText.setText("");
    }

    private void showManualSearch() {
        setSecondaryHeader(true);
        View screen = showFigmaScreen(R.layout.screen_search);
        EditText input = screen.findViewById(R.id.search_input);
        LinearLayout results = screen.findViewById(R.id.search_results);
        View empty = screen.findViewById(R.id.search_empty);
        View noResults = screen.findViewById(R.id.search_no_results);
        if (results != null) results.setVisibility(View.GONE);
        if (empty != null) empty.setVisibility(View.VISIBLE);
        if (noResults != null) noResults.setVisibility(View.GONE);
        if (input == null || results == null) return;
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                results.removeAllViews();
                if (q.isEmpty()) {
                    results.setVisibility(View.GONE);
                    if (empty != null) empty.setVisibility(View.VISIBLE);
                    if (noResults != null) noResults.setVisibility(View.GONE);
                    return;
                }
                if (empty != null) empty.setVisibility(View.GONE);
                int found = 0;
                for (ContactItem contact : contacts) {
                    String name = safe(contact.name).toLowerCase(Locale.ROOT);
                    String phone = safe(contact.phone).toLowerCase(Locale.ROOT);
                    if (!name.contains(q) && !phone.contains(q)) continue;
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(11), dp(12), dp(11));
                    TextView avatar = text(initials(contact.name), 13, true);
                    avatar.setGravity(Gravity.CENTER);
                    avatar.setTextColor(COLOR_PRIMARY);
                    avatar.setBackground(roundedBackground(0xFFE5EBDD, 0xFFE5EBDD, 100));
                    row.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
                    LinearLayout labels = new LinearLayout(MainActivity.this);
                    labels.setOrientation(LinearLayout.VERTICAL);
                    labels.setPadding(dp(12), 0, 0, 0);
                    labels.addView(text(contact.name, 15, true));
                    TextView phoneLabel = text(contact.phone == null || contact.phone.isEmpty()
                            ? "No phone number" : contact.phone, 12, false);
                    phoneLabel.setTextColor(COLOR_SECONDARY);
                    labels.addView(phoneLabel);
                    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                    row.addView(iconAction("›", COLOR_PRIMARY, "Open contact"),
                            new LinearLayout.LayoutParams(dp(30), dp(44)));
                    results.addView(row);
                    addThinDivider(results);
                    row.setOnClickListener(v -> {
                        currentMatches = new ArrayList<>();
                        currentMatches.add(contact);
                        currentIndex = 0;
                        showCard(false);
                    });
                    found++;
                }
                results.setVisibility(found > 0 ? View.VISIBLE : View.GONE);
                if (noResults != null) noResults.setVisibility(found == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        statusText.setText("");
    }

    private void showContactPhoto(FrameLayout frame, Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        boolean lowResolution = Math.min(w, h) < 500 || (long) w * h < 300000L;
        if (lowResolution) {
            ImageView blurredBackdrop = new ImageView(this);
            blurredBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            blurredBackdrop.setImageBitmap(bitmap);
            blurredBackdrop.setAlpha(0.58f);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                blurredBackdrop.setRenderEffect(RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP));
            }
            frame.addView(blurredBackdrop, new FrameLayout.LayoutParams(-1, -1));
            View tint = new View(this);
            tint.setBackgroundColor(Color.argb(35, 244, 239, 229));
            frame.addView(tint, new FrameLayout.LayoutParams(-1, -1));
            // Keep the source photo's aspect ratio, but allow small thumbnails to
            // upscale into a useful, readable foreground size instead of remaining
            // at their tiny native pixel dimensions.
            ImageView original = new ImageView(this);
            original.setScaleType(ImageView.ScaleType.FIT_CENTER);
            original.setImageBitmap(bitmap);
            FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.62f),
                    dp(178), Gravity.CENTER);
            centered.setMargins(dp(8), dp(8), dp(8), dp(8));
            frame.addView(original, centered);
        } else {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageBitmap(bitmap);
            frame.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void loadCachedImage(ImageView target, String imageUrl, String cacheKey) {
        File cacheFile = new File(getCacheDir(), cacheKey + ".img");
        if (cacheFile.exists()) {
            Bitmap cached = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (cached != null) { target.setImageBitmap(cached); return; }
        }
        imageExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "VoiceDeck-Android");
                try (java.io.InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(cacheFile)) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                Bitmap downloaded = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (downloaded != null) runOnUiThread(() -> { if (!isFinishing() && target.getParent() != null) target.setImageBitmap(downloaded); });
            } catch (Exception ignored) {
                // Keep the local initials tile as a graceful offline/error fallback.
                if (cacheFile.exists()) cacheFile.delete();
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    /**
     * Animates the contact card as a two-sided physical card. The card view is
     * rebuilt for the selected face, while the surrounding navigation stays put.
     */
    private void flipContactCard(boolean back) {
        if (cardTransitionRunning || currentMatches.isEmpty() || showingBack == back) return;
        if (content == null || content.getChildCount() == 0) {
            showCard(back);
            return;
        }

        View currentCard = content.getChildAt(0);
        if (currentCard == null) {
            showCard(back);
            return;
        }

        cardTransitionRunning = true;
        currentCard.setCameraDistance(dp(2400));
        currentCard.animate()
                .rotationY(88f)
                .alpha(0.92f)
                .setDuration(160)
                .withEndAction(() -> {
                    showCard(back);
                    if (content.getChildCount() == 0) {
                        cardTransitionRunning = false;
                        return;
                    }
                    View nextCard = content.getChildAt(0);
                    nextCard.setCameraDistance(dp(2400));
                    nextCard.setRotationY(-88f);
                    nextCard.setAlpha(0.92f);
                    nextCard.animate()
                            .rotationY(0f)
                            .alpha(1f)
                            .setDuration(190)
                            .withEndAction(() -> cardTransitionRunning = false)
                            .start();
                })
                .start();
    }

    private void addContactDetailRow(LinearLayout parent, int iconRes, String label, String value) {
        if (parent == null || value == null || value.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(8), 0, dp(8));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(roundedBackground(Color.rgb(229, 235, 221), Color.TRANSPARENT, 12));
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1f);
        textLp.leftMargin = dp(12);
        row.addView(texts, textLp);
        TextView title = new TextView(this);
        title.setTypeface(getManrope());
        title.setText(label);
        title.setTextSize(11);
        title.setTextColor(Color.rgb(119, 117, 104));
        texts.addView(title);
        TextView data = new TextView(this);
        data.setTypeface(getManrope());
        data.setText(value.trim());
        data.setTextSize(13);
        data.setTypeface(getManrope(), Typeface.BOLD);
        data.setTextColor(Color.rgb(40, 53, 45));
        data.setMaxLines(3);
        texts.addView(data);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showCard(boolean back) {
        if (currentMatches.isEmpty()) return;
        showingBack = back;
        setSecondaryHeader(false);
        ContactItem item = currentMatches.get(Math.max(0, Math.min(currentIndex, currentMatches.size() - 1)));
        View screen = showFigmaScreen(R.layout.screen_contact);

        View front = screen.findViewById(R.id.contact_front);
        View rear = screen.findViewById(R.id.contact_back);
        if (front != null) front.setVisibility(back ? View.GONE : View.VISIBLE);
        if (rear != null) rear.setVisibility(back ? View.VISIBLE : View.GONE);

        setTextIfPresent(screen, R.id.contact_name, item.name);
        setTextIfPresent(screen, R.id.contact_back_name, item.name);
        setTextIfPresent(screen, R.id.contact_phone,
                item.phone == null || item.phone.isEmpty() ? "No mobile number saved" : item.phone);
        LinearLayout contactRows = screen.findViewById(R.id.contact_detail_rows);
        if (contactRows != null) {
            addContactDetailRow(contactRows, R.drawable.ic_contact_mail, "Email", item.email);
            addContactDetailRow(contactRows, R.drawable.ic_contact_building, "Company", item.company);
            addContactDetailRow(contactRows, R.drawable.ic_contact_calendar, "Birthday", item.birthday);
            addContactDetailRow(contactRows, R.drawable.ic_contact_pin, "Address", item.address);
            View empty = screen.findViewById(R.id.contact_no_details);
            if (empty != null) empty.setVisibility(contactRows.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        }
        setTextIfPresent(screen, R.id.contact_count,
                (currentIndex + 1) + " of " + currentMatches.size());

        ImageView photo = screen.findViewById(R.id.contact_photo);
        if (photo != null) {
            Bitmap bitmap = null;
            if (item.photo != null) {
                try (java.io.InputStream stream = getContentResolver().openInputStream(item.photo)) {
                    bitmap = BitmapFactory.decodeStream(stream);
                } catch (Exception ignored) { }
            }
            if (bitmap != null) {
                photo.setImageBitmap(bitmap);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                photo.setImageResource(R.drawable.voicedeck_default);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }

        Button call = screen.findViewById(R.id.action_call);
        Button whatsapp = screen.findViewById(R.id.action_whatsapp);
        TextView details = screen.findViewById(R.id.action_show_details);
        Button google = screen.findViewById(R.id.action_google_contacts);
        Button backToFront = screen.findViewById(R.id.action_show_front);
        Button previous = screen.findViewById(R.id.action_previous_contact);
        Button next = screen.findViewById(R.id.action_next_contact);
        ImageView mic = screen.findViewById(R.id.action_contact_mic);

        if (call != null) call.setOnClickListener(v -> callContact(item));
        if (whatsapp != null) whatsapp.setOnClickListener(v -> openWhatsApp(item));
        if (details != null) details.setOnClickListener(v -> flipContactCard(true));
        if (backToFront != null) backToFront.setOnClickListener(v -> flipContactCard(false));
        View flipIcon = screen.findViewById(R.id.action_flip_icon);
        if (flipIcon != null) flipIcon.setOnClickListener(v -> flipContactCard(!showingBack));
        if (google != null) google.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, item.id));
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Unable to open this contact.", Toast.LENGTH_SHORT).show();
            }
        });
        boolean canBrowse = currentMatches.size() > 1;
        if (previous != null) {
            previous.setEnabled(canBrowse);
            previous.setAlpha(canBrowse ? 1f : 0.4f);
            previous.setOnClickListener(v -> {
                if (currentMatches.size() > 1) {
                    currentIndex = (currentIndex - 1 + currentMatches.size()) % currentMatches.size();
                    showCard(showingBack);
                }
            });
        }
        if (next != null) {
            next.setEnabled(canBrowse);
            next.setAlpha(canBrowse ? 1f : 0.4f);
            next.setOnClickListener(v -> {
                if (currentMatches.size() > 1) {
                    currentIndex = (currentIndex + 1) % currentMatches.size();
                    showCard(showingBack);
                }
            });
        }
        if (mic != null) mic.setOnClickListener(v -> returnToVoiceSearch());
        statusText.setText(back
                ? "Contact details. Return to the card or record another contact."
                : "Contact found. Choose Call or WhatsApp.");
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
    }

    private void returnToVoiceSearch() {
        currentMatches.clear();
        currentIndex = 0;
        showingBack = false;
        showListeningPrompt(false);
        statusText.setText("Ready to find another contact.");
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setTypeface(getManrope());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setMinHeight(dp(64));
        return b;
    }

    private void addDetail(LinearLayout card, String icon, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedBackground(COLOR_BACKGROUND, COLOR_BORDER, 14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(9);
        card.addView(row, rowParams);

        TextView symbol = centeredText(icon, 21, true, COLOR_PRIMARY);
        row.addView(symbol, new LinearLayout.LayoutParams(dp(38), dp(42)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView title = text(label, 12, true);
        title.setTextColor(COLOR_SECONDARY);
        labels.addView(title);
        TextView detail = text(value == null || value.trim().isEmpty() ? "Not saved" : value, 16, false);
        detail.setTextColor(value == null || value.trim().isEmpty() ? COLOR_SECONDARY : COLOR_TEXT);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
    }

    private ContactItem getSelectedContact() {
        if (currentMatches.isEmpty()) return null;
        int safeIndex = Math.max(0, Math.min(currentIndex, currentMatches.size() - 1));
        return currentMatches.get(safeIndex);
    }

    private void callContact(ContactItem item) {
        if (item.phone.isEmpty()) {
            Toast.makeText(this, "No phone number saved for this contact.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQ_CONTACTS);
            return;
        }
        placeDirectCall(item);
    }

    private void placeDirectCall(ContactItem item) {
        if (item == null || item.phone.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_CALL,
                Uri.parse("tel:" + Uri.encode(item.phone)));
        try {
            startActivity(intent);
        } catch (SecurityException | ActivityNotFoundException e) {
            Toast.makeText(this, "Direct calling is unavailable. Opening the dialler.", Toast.LENGTH_LONG).show();
            openDialler(item);
        }
    }

    private void openDialler(ContactItem item) {
        if (item == null || item.phone.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(item.phone)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No dialler app found.", Toast.LENGTH_LONG).show();
        }
    }

    private void openWhatsApp(ContactItem item) {
        if (item.phone.isEmpty()) {
            Toast.makeText(this, "No phone number saved for this contact.", Toast.LENGTH_LONG).show();
            return;
        }
        String number = item.phone.replaceAll("[^0-9]", "");
        if (number.startsWith("0")) number = number.substring(1);
        if (number.length() == 10) number = "91" + number;
        Uri uri = Uri.parse("https://wa.me/" + number);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.whatsapp");
        try { startActivity(intent); }
        catch (Exception e) {
            intent.setPackage(null);
            try { startActivity(intent); }
            catch (Exception ex) { Toast.makeText(this, "WhatsApp is not available.", Toast.LENGTH_LONG).show(); }
        }
    }

    private void setSecondaryHeader(boolean secondary) {
        if (headerBack != null) headerBack.setVisibility(secondary ? View.VISIBLE : View.GONE);
    }

    private void addSettingsSection(String label) {
        TextView section = text(label.toUpperCase(java.util.Locale.ROOT), 12, true);
        section.setTextColor(COLOR_SECONDARY);
        section.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(20); p.bottomMargin = dp(8);
        content.addView(section, p);
    }

    private void addSettingsRow(String title, String subtitle, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(14), dp(13));
        row.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 17));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(title, 16, true);
        copy.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 13, false);
            detail.setTextColor(COLOR_SECONDARY);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
            detailParams.topMargin = dp(3); copy.addView(detail, detailParams);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null && !value.isEmpty()) {
            TextView trailing = text(value + "  ›", 14, true);
            trailing.setTextColor(COLOR_PRIMARY);
            row.addView(trailing);
        } else {
            TextView arrow = centeredText("›", 25, false, COLOR_PRIMARY);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(36)));
        }
        row.setClickable(true); row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8); content.addView(row, rp);
    }

    private void showSettings() {
        setSecondaryHeader(true);

        headerBack.setVisibility(View.VISIBLE);
        headerBack.setOnClickListener(v -> showListeningPrompt(false));

        showingDictionaryManager = false;
        showingAliasManager = false;

        View screen = showFigmaScreen(R.layout.screen_settings);
        setTextIfPresent(screen, R.id.setting_language_value, currentLanguageName());
        View languageRow = screen.findViewById(R.id.setting_language);
        View vocabularyRow = screen.findViewById(R.id.setting_vocabulary);
        View aliasesRow = screen.findViewById(R.id.setting_aliases);
        View permissionsRow = screen.findViewById(R.id.setting_permissions);
        View legalRow = screen.findViewById(R.id.setting_legal);
        if (languageRow != null) languageRow.setOnClickListener(v -> showLanguageChooser());
        if (vocabularyRow != null) vocabularyRow.setOnClickListener(v -> showDictionaryManager(language));
        if (aliasesRow != null) aliasesRow.setOnClickListener(v -> showAliasManager());
        if (permissionsRow != null) permissionsRow.setOnClickListener(v -> showPermissionsDialog());
        if (legalRow != null) legalRow.setOnClickListener(v -> showLegalDialog());
        statusText.setText("");
    }

    private void showPermissionsDialog() {
        setSecondaryHeader(true);
        View screen = showFigmaScreen(R.layout.screen_permissions);
        android.widget.Switch microphone = screen.findViewById(R.id.permission_microphone);
        android.widget.Switch contactsSwitch = screen.findViewById(R.id.permission_contacts);
        boolean micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        if (microphone != null) {
            microphone.setChecked(micGranted);
            microphone.setOnClickListener(v -> openAppPermissionSettings());
        }
        if (contactsSwitch != null) {
            contactsSwitch.setChecked(contactsGranted);
            contactsSwitch.setOnClickListener(v -> openAppPermissionSettings());
        }
        statusText.setText("");
    }

    private void openAppPermissionSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "Open Android Settings to manage app permissions.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLegalDialog() {
        setSecondaryHeader(true);
        View screen = showFigmaScreen(R.layout.screen_legal);
        View privacy = screen.findViewById(R.id.legal_privacy);
        View terms = screen.findViewById(R.id.legal_terms);
        if (privacy != null) privacy.setOnClickListener(v ->
                openLegalPage("https://ajinkyadube.com/apps/privacy.html"));
        if (terms != null) terms.setOnClickListener(v ->
                openLegalPage("https://ajinkyadube.com/apps/terms.html"));
        statusText.setText("");
    }

    private void showAliasManager() {
        setSecondaryHeader(true);
        showingAliasManager = true;
        showingDictionaryManager = false;
        if (!contactsLoaded) loadContacts();
        View screen = showFigmaScreen(R.layout.screen_aliases);
        statusText.setText("");
        Button add = screen.findViewById(R.id.action_add_alias);
        EditText search = screen.findViewById(R.id.alias_search);
        LinearLayout list = screen.findViewById(R.id.alias_list);
        TextView empty = screen.findViewById(R.id.alias_empty);
        TextView count = screen.findViewById(R.id.alias_count);
        if (add != null) add.setOnClickListener(v -> showAliasEditor(null));
        if (contactAliasRepository == null) {
            initializeSpeechDictionary();
            if (empty != null) empty.setText("Preparing alias storage. Please reopen this screen in a moment.");
            return;
        }
        contactAliasRepository.getAllAliases(new ContactAliasRepository.ResultCallback<List<ContactAlias>>() {
            @Override public void onSuccess(List<ContactAlias> aliases) {
                if (isFinishing() || isDestroyed() || !showingAliasManager || list == null || empty == null) return;
                if (count != null) count.setText(aliases.size() + " SAVED ALIASES");
                Runnable render = () -> renderAliasRows(list, empty, aliases,
                        search == null || search.getText() == null ? "" : search.getText().toString());
                if (search != null) search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int c, int a) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int c) { render.run(); }
                    @Override public void afterTextChanged(Editable s) { }
                });
                render.run();
            }
            @Override public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed() && showingAliasManager && empty != null)
                    empty.setText("Couldn't load saved aliases. Please try again.");
            }
        });
    }

    private void renderAliasRows(LinearLayout list, TextView empty,
                                 List<ContactAlias> aliases, String filter) {
        list.removeAllViews();
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (ContactAlias alias : aliases) {
            ContactItem linked = null;
            for (ContactItem item : contacts) if (item.id == alias.contactId) { linked = item; break; }
            String contactName = linked == null ? "Unknown contact" : safe(linked.name);
            if (!query.isEmpty() && !alias.alias.toLowerCase(Locale.ROOT).contains(query)
                    && !contactName.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (visible++ > 0) addThinDivider(list);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(12), dp(8), dp(12));
            TextView avatar = text(initials(contactName), 13, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setTextColor(COLOR_PRIMARY);
            avatar.setBackground(roundedBackground(0xFFE5EBDD, 0xFFE5EBDD, 100));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(dp(10), 0, dp(4), 0);
            TextView aliasName = text("“" + alias.alias + "”  " + alias.languageTag.substring(0, Math.min(2, alias.languageTag.length())).toUpperCase(Locale.ROOT), 14, true);
            TextView linkedName = text("↗  " + contactName, 12, false);
            linkedName.setTextColor(COLOR_SECONDARY);
            names.addView(aliasName);
            names.addView(linkedName);
            row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            TextView edit = iconAction("✎", COLOR_PRIMARY, "Edit alias");
            row.addView(edit, new LinearLayout.LayoutParams(dp(38), dp(44)));
            edit.setOnClickListener(v -> showAliasEditor(alias));
            TextView delete = iconAction("♜", 0xFF9D5349, "Delete alias");
            delete.setText("✕");
            row.addView(delete, new LinearLayout.LayoutParams(dp(38), dp(44)));
            delete.setOnClickListener(v -> confirmDeleteAlias(alias));
            list.addView(row);
        }
        empty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
        if (visible == 0) {
            empty.setText(query.isEmpty() ? "No aliases saved yet." : "No matching aliases.");
            list.addView(empty);
        }
    }

    private String initials(String name) {
        String[] parts = safe(name).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String result = parts[0].substring(0, 1);
        if (parts.length > 1 && !parts[1].isEmpty()) result += parts[1].substring(0, 1);
        return result.toUpperCase(Locale.ROOT);
    }

    private TextView iconAction(String symbol, int color, String description) {
        TextView action = text(symbol, 20, false);
        action.setTextColor(color);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(description);
        action.setClickable(true);
        action.setFocusable(true);
        return action;
    }

    private void addThinDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(COLOR_BORDER);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void styleFormField(EditText field) {
        field.setSingleLine(true);
        field.setTextSize(16);
        field.setTextColor(COLOR_TEXT);
        field.setHintTextColor(COLOR_SECONDARY);
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        field.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 16));
        if (android.os.Build.VERSION.SDK_INT >= 21) field.setElevation(dp(1));
    }

    private void showAliasEditor(ContactAlias existing) {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts with phone numbers are available.", Toast.LENGTH_LONG).show();
            return;
        }
        final ContactItem[] selected = new ContactItem[1];
        if (existing != null) for (ContactItem item : contacts)
            if (item.id == existing.contactId) { selected[0] = item; break; }
        View screen = showFigmaScreen(R.layout.screen_alias_form);
        EditText nickname = screen.findViewById(R.id.alias_nickname);
        TextView contact = screen.findViewById(R.id.alias_contact_selector);
        TextView languageSelector = screen.findViewById(R.id.alias_language_selector);
        Button save = screen.findViewById(R.id.action_save_alias);
        Button cancel = screen.findViewById(R.id.action_cancel_alias);
        nickname.setText(existing == null ? "" : existing.alias);
        contact.setText(selected[0] == null ? "Select contact  ›" : selected[0].name + "  ›");
        final String[] selectedLanguage = {existing == null ? language : existing.languageTag};
        String[] languageTags = {"en-IN", "hi-IN", "mr-IN"};
        String[] languageLabels = {"English", "हिन्दी", "मराठी"};
        for (int i = 0; i < languageTags.length; i++) if (languageTags[i].equals(selectedLanguage[0]))
            languageSelector.setText(languageLabels[i] + "  ›");
        contact.setOnClickListener(v -> showAliasManagementContactPicker(selected, contact));
        languageSelector.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Spoken language").setSingleChoiceItems(languageLabels,
                        java.util.Arrays.asList(languageTags).indexOf(selectedLanguage[0]),
                        (dialog, which) -> { selectedLanguage[0] = languageTags[which];
                            languageSelector.setText(languageLabels[which] + "  ›"); dialog.dismiss(); })
                .setNegativeButton("Cancel", null).show());
        cancel.setOnClickListener(v -> showAliasManager());
        save.setText(existing == null ? "Add alias" : "Save alias");
        save.setOnClickListener(v -> {
            String aliasText = nickname.getText().toString().trim();
            String normalized = SpeechCommandFilter.normalizeTerm(aliasText);
            if (selected[0] == null) { Toast.makeText(this, "Select a contact.", Toast.LENGTH_SHORT).show(); return; }
            if (normalized.isEmpty() || aliasText.length() > 80 || normalized.split("\\s+").length > 8) {
                nickname.setError("Use up to 80 characters and 8 words."); return;
            }
            ContactAlias entry = new ContactAlias(selected[0].id, aliasText, normalized,
                    selectedLanguage[0], existing == null ? System.currentTimeMillis() : existing.createdAt);
            if (existing != null) entry.id = existing.id;
            if (existing == null) contactAliasRepository.insert(entry, new ContactAliasRepository.ResultCallback<Long>() {
                @Override public void onSuccess(Long id) {
                    if (id == null || id <= 0) { Toast.makeText(MainActivity.this, "Alias already linked to this contact.", Toast.LENGTH_LONG).show(); return; }
                    onAliasManagementSaved("Alias added.");
                }
                @Override public void onError(Exception error) { Toast.makeText(MainActivity.this, "Couldn't add alias.", Toast.LENGTH_LONG).show(); }
            });
            else contactAliasRepository.update(entry, new ContactAliasRepository.ResultCallback<Integer>() {
                @Override public void onSuccess(Integer count) {
                    if (count == null || count <= 0) { Toast.makeText(MainActivity.this, "Alias already linked to this contact.", Toast.LENGTH_LONG).show(); return; }
                    onAliasManagementSaved("Alias updated.");
                }
                @Override public void onError(Exception error) { Toast.makeText(MainActivity.this, "Couldn't update alias.", Toast.LENGTH_LONG).show(); }
            });
        });
    }

    private void showAliasManagementContactPicker(ContactItem[] selected, TextView target) {
        List<ContactItem> visible = new ArrayList<>(contacts);
        ArrayAdapter<ContactItem> adapter = new ArrayAdapter<ContactItem>(this,
                android.R.layout.simple_list_item_1, visible) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setText(getItem(position).name);
                row.setTextSize(17); row.setTextColor(COLOR_TEXT); row.setMinHeight(dp(54));
                return row;
            }
        };
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("Select contact"));
        EditText search = new EditText(this);
        search.setTypeface(getManrope());
        search.setSingleLine(true); search.setHint("Search contacts"); search.setTextSize(16);
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        panel.addView(list, new LinearLayout.LayoutParams(-1, dp(360)));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).setNegativeButton("Cancel", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            selected[0] = visible.get(position); target.setText(selected[0].name); dialog.dismiss();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s == null ? "" : s.toString().toLowerCase(Locale.ROOT).trim();
                visible.clear();
                for (ContactItem item : contacts) if (q.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(q)
                        || item.phone.toLowerCase(Locale.ROOT).contains(q)) visible.add(item);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.show();
    }

    private void confirmDeleteAlias(ContactAlias alias) {
        new AlertDialog.Builder(this).setTitle("Delete alias?")
                .setMessage("Remove “" + alias.alias + "” from VoiceDeck? The Android contact will not be affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> contactAliasRepository.deleteById(alias.id,
                        new ContactAliasRepository.ResultCallback<Integer>() {
                            @Override public void onSuccess(Integer deleted) {
                                onAliasManagementSaved(deleted != null && deleted > 0 ? "Alias deleted." : "Alias was not found.");
                            }
                            @Override public void onError(Exception error) {
                                Toast.makeText(MainActivity.this, "Couldn't delete this alias.", Toast.LENGTH_LONG).show();
                            }
                        })).show();
    }

    private void onAliasManagementSaved(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        loadContactAliases(contactLoadGeneration);
        showAliasManager();
    }

    private void showDictionaryManager(String selectedLanguage) {
        setSecondaryHeader(true);
        showingDictionaryManager = true;
        showingAliasManager = false;
        dictionaryManagerLanguage = selectedLanguage;
        View screen = showFigmaScreen(R.layout.screen_vocabulary);
        statusText.setText("");
        TextView languageRow = screen.findViewById(R.id.vocabulary_language);
        EditText search = screen.findViewById(R.id.vocabulary_search);
        Button add = screen.findViewById(R.id.action_add_vocabulary);
        Button restore = screen.findViewById(R.id.action_restore_vocabulary);
        LinearLayout list = screen.findViewById(R.id.vocabulary_entries);
        if (languageRow != null) {
            languageRow.setText("Current language\n" + dictionaryLanguageName(dictionaryManagerLanguage) + "   ›");
            languageRow.setOnClickListener(v -> showDictionaryLanguageChooser());
        }
        if (add != null) add.setOnClickListener(v -> showAddDictionaryEntryDialog());
        if (restore != null) restore.setOnClickListener(v -> confirmRestoreDictionaryDefaults());
        if (speechDictionaryRepository == null) {
            initializeSpeechDictionary();
            return;
        }
        speechDictionaryRepository.getEntriesForLanguage(
                dictionaryManagerLanguage,
                new SpeechDictionaryRepository.ResultCallback<List<SpeechDictionaryEntry>>() {
                    @Override public void onSuccess(List<SpeechDictionaryEntry> entries) {
                        if (isFinishing() || isDestroyed() || !showingDictionaryManager
                                || !dictionaryManagerLanguage.equals(selectedLanguage) || list == null) return;
                        Runnable render = () -> {
                            list.removeAllViews();
                            String q = search == null || search.getText() == null ? ""
                                    : search.getText().toString().trim().toLowerCase(Locale.ROOT);
                            List<SpeechDictionaryEntry> filtered = new ArrayList<>();
                            for (SpeechDictionaryEntry entry : entries) {
                                if (q.isEmpty() || entry.term.toLowerCase(Locale.ROOT).contains(q)
                                        || entry.category.toLowerCase(Locale.ROOT).contains(q)) filtered.add(entry);
                            }
                            if (filtered.isEmpty()) {
                                TextView empty = text("No matching vocabulary entries.", 14, false);
                                empty.setTextColor(COLOR_SECONDARY);
                                list.addView(empty);
                            } else {
                                addDictionaryEntryRows(list, filtered);
                            }
                        };
                        if (search != null) search.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int start, int c, int a) { }
                            @Override public void onTextChanged(CharSequence s, int start, int before, int c) { render.run(); }
                            @Override public void afterTextChanged(Editable s) { }
                        });
                        render.run();
                    }
                    @Override public void onError(Exception error) {
                        if (!isFinishing() && !isDestroyed() && showingDictionaryManager && list != null) {
                            list.removeAllViews();
                            TextView message = text("Couldn't load the local dictionary. Please try again.", 14, false);
                            list.addView(message);
                        }
                    }
                });
    }

    private void addDictionaryEntryRows(LinearLayout parent, List<SpeechDictionaryEntry> entries) {
        int index = 0;
        for (SpeechDictionaryEntry entry : entries) {
            if (index++ > 0) addThinDivider(parent);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(10), dp(12));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView term = text(entry.term + (entry.isDefault ? "" : "  ·  CUSTOM"), 15, true);
            TextView category = text(dictionaryCategoryName(entry.category), 12, false);
            category.setTextColor(COLOR_SECONDARY);
            labels.addView(term);
            labels.addView(category);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            if (entry.isProtected) {
                TextView essential = text("Essential", 11, true);
                essential.setTextColor(COLOR_PRIMARY);
                essential.setPadding(dp(5), 0, dp(6), 0);
                row.addView(essential);
            } else if (!entry.isDefault) {
                TextView remove = iconAction("✕", 0xFF9D5349, "Delete phrase");
                row.addView(remove, new LinearLayout.LayoutParams(dp(36), dp(44)));
                remove.setOnClickListener(v -> confirmDeleteCustomEntry(entry));
            }
            Switch enabled = new Switch(this);
            enabled.setTypeface(getManrope());
            enabled.setShowText(false);
            enabled.setChecked(entry.enabled);
            enabled.setEnabled(!entry.isProtected);
            enabled.setContentDescription("Enable " + entry.term);
            enabled.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            enabled.setTrackTintList(android.content.res.ColorStateList.valueOf(entry.enabled ? COLOR_PRIMARY : 0xFFB5B4AA));
            row.addView(enabled, new LinearLayout.LayoutParams(-2, -2));
            parent.addView(row);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                if (entry.enabled == checked) return;
                speechDictionaryRepository.setEnabled(entry.id, checked,
                        new SpeechDictionaryRepository.ResultCallback<Integer>() {
                            @Override public void onSuccess(Integer changed) {
                                if (!showingDictionaryManager || isFinishing() || isDestroyed()) return;
                                if (changed == null || changed == 0) Toast.makeText(MainActivity.this,
                                        "This essential command cannot be disabled.", Toast.LENGTH_SHORT).show();
                                if (entry.languageTag.equals(language)) loadSpeechDictionary();
                                showDictionaryManager(dictionaryManagerLanguage);
                            }
                            @Override public void onError(Exception error) {
                                if (!showingDictionaryManager || isFinishing() || isDestroyed()) return;
                                Toast.makeText(MainActivity.this,
                                        "Couldn't update this dictionary entry.", Toast.LENGTH_LONG).show();
                                showDictionaryManager(dictionaryManagerLanguage);
                            }
                        });
            });
        }
    }

    private void showDictionaryLanguageChooser() {
        String[] labels = {"English", "हिन्दी", "मराठी"};
        String[] values = {"en-IN", "hi-IN", "mr-IN"};
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(dictionaryManagerLanguage)) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Dictionary language")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    showDictionaryManager(values[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddDictionaryEntryDialog() {
        View screen = showFigmaScreen(R.layout.screen_vocabulary_form);
        EditText phrase = screen.findViewById(R.id.vocabulary_phrase);
        android.widget.Spinner category = screen.findViewById(R.id.vocabulary_category);
        Button cancel = screen.findViewById(R.id.action_cancel_vocabulary);
        Button save = screen.findViewById(R.id.action_save_vocabulary);
        cancel.setOnClickListener(v -> showDictionaryManager(dictionaryManagerLanguage));
        save.setOnClickListener(v -> {
            String term = phrase.getText().toString().trim();
            String normalized = SpeechCommandFilter.normalizeTerm(term);
            if (normalized.isEmpty() || term.length() > 80 || normalized.split("\\s+").length > 8) {
                phrase.setError("Use a phrase of up to 80 characters and 8 words."); return;
            }
            String[] values = {SpeechDictionaryEntry.CATEGORY_COMMAND,
                    SpeechDictionaryEntry.CATEGORY_GREETING, SpeechDictionaryEntry.CATEGORY_FILLER};
            int index = Math.min(Math.max(category.getSelectedItemPosition(), 0), values.length - 1);
            SpeechDictionaryEntry entry = new SpeechDictionaryEntry(term, normalized,
                    dictionaryManagerLanguage, values[index], true, false, false);
            speechDictionaryRepository.insert(entry, new SpeechDictionaryRepository.ResultCallback<Long>() {
                @Override public void onSuccess(Long id) {
                    if (id == null || id < 0) { Toast.makeText(MainActivity.this, "Phrase already exists.", Toast.LENGTH_LONG).show(); return; }
                    Toast.makeText(MainActivity.this, "Vocabulary added.", Toast.LENGTH_SHORT).show();
                    if (dictionaryManagerLanguage.equals(language)) loadSpeechDictionary();
                    showDictionaryManager(dictionaryManagerLanguage);
                }
                @Override public void onError(Exception error) { Toast.makeText(MainActivity.this, "Couldn't save phrase.", Toast.LENGTH_LONG).show(); }
            });
        });
    }

    private void confirmDeleteCustomEntry(SpeechDictionaryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Remove custom word?")
                .setMessage("Remove “" + entry.term + "” from this device's speech dictionary?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) ->
                        speechDictionaryRepository.deleteCustomEntry(entry.id,
                                new SpeechDictionaryRepository.ResultCallback<Integer>() {
                                    @Override public void onSuccess(Integer deleted) {
                                        Toast.makeText(MainActivity.this,
                                                deleted != null && deleted > 0 ? "Custom word removed." :
                                                        "Default vocabulary cannot be removed.",
                                                Toast.LENGTH_SHORT).show();
                                        if (dictionaryManagerLanguage.equals(language)) loadSpeechDictionary();
                                        showDictionaryManager(dictionaryManagerLanguage);
                                    }

                                    @Override public void onError(Exception error) {
                                        Toast.makeText(MainActivity.this,
                                                "Couldn't remove this word.", Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private void confirmRestoreDictionaryDefaults() {
        new AlertDialog.Builder(this)
                .setTitle("Restore default vocabulary?")
                .setMessage("This re-enables VoiceDeck's original words for " +
                        dictionaryLanguageName(dictionaryManagerLanguage) +
                        ". Your custom words will be kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (dialog, which) ->
                        speechDictionaryRepository.restoreDefaults(dictionaryManagerLanguage,
                                SpeechDictionaryDefaults.createEntries(),
                                new SpeechDictionaryRepository.ResultCallback<Integer>() {
                                    @Override public void onSuccess(Integer restored) {
                                        Toast.makeText(MainActivity.this, "Default vocabulary restored.",
                                                Toast.LENGTH_SHORT).show();
                                        if (dictionaryManagerLanguage.equals(language)) loadSpeechDictionary();
                                        showDictionaryManager(dictionaryManagerLanguage);
                                    }

                                    @Override public void onError(Exception error) {
                                        Toast.makeText(MainActivity.this,
                                                "Couldn't restore default vocabulary.", Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private String dictionaryLanguageName(String languageTag) {
        if ("hi-IN".equals(languageTag)) return "हिन्दी";
        if ("mr-IN".equals(languageTag)) return "मराठी";
        return "English";
    }

    private String dictionaryCategoryName(String category) {
        if (SpeechDictionaryEntry.CATEGORY_GREETING.equals(category)) return "GREETINGS";
        if (SpeechDictionaryEntry.CATEGORY_FILLER.equals(category)) return "FILLER WORDS";
        return "COMMANDS";
    }

    private void openLegalPage(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser is available to open this page.", Toast.LENGTH_LONG).show();
        }
    }

    private String currentLanguageName() {
        if (language.equals("mr-IN")) return "मराठी";
        if (language.equals("hi-IN")) return "हिन्दी";
        return "English";
    }

    private void addPermissionStatus(LinearLayout parent, String label, String permission) {
        boolean granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(4));
        TextView name = text(label, 16, true); row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text(granted ? "Allowed" : "Not allowed", 15, true);
        state.setTextColor(granted ? COLOR_PRIMARY : COLOR_SECONDARY); row.addView(state); parent.addView(row);
    }

    private void showLanguageChooser() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(14));
        panel.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
        panel.addView(text("Voice search language", 22, true));
        RadioGroup group = new RadioGroup(this); group.setOrientation(RadioGroup.VERTICAL);
        String[] labels = {"English", "मराठी", "हिन्दी"};
        String[] values = {"en-IN", "mr-IN", "hi-IN"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this); option.setText(labels[i]);
            option.setTextSize(19); option.setTextColor(COLOR_TEXT);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(COLOR_PRIMARY));
            option.setPadding(dp(4), dp(10), dp(4), dp(10)); option.setChecked(language.equals(values[i]));
            final String selected = values[i];
            option.setOnClickListener(v -> {
                language = selected; getPreferences(MODE_PRIVATE).edit().putString("language", language).apply();
                loadSpeechDictionary();
                loadContactAliases(contactLoadGeneration);
                showSettings();
            });
            group.addView(option, new RadioGroup.LayoutParams(-1, dp(58)));
        }
        panel.addView(group);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).setNegativeButton("Close", null).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_PRIMARY);
        });
        dialog.show();
    }

    @Override public void onBackPressed() {
        if (showingDictionaryManager || showingAliasManager) {
            showSettings();
            return;
        }
        if (showingBack && !currentMatches.isEmpty()) {
            flipContactCard(false);
            return;
        }
        if (currentFigmaLayout == R.layout.screen_alias_form) { showAliasManager(); return; }
        if (currentFigmaLayout == R.layout.screen_vocabulary_form) { showDictionaryManager(dictionaryManagerLanguage); return; }
        if (currentFigmaLayout == R.layout.screen_settings
                || currentFigmaLayout == R.layout.screen_permissions
                || currentFigmaLayout == R.layout.screen_legal) {
            showSettings();
            return;
        }
        if (currentFigmaLayout == R.layout.screen_contact) {
            returnToVoiceSearch();
            return;
        }
        if (currentFigmaLayout == R.layout.screen_search
                || currentFigmaLayout == R.layout.screen_matches) {
            showListeningPrompt(false);
            return;
        }
        if (headerBack != null && headerBack.getVisibility() == View.VISIBLE) {
            showListeningPrompt(false);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (splashFinish != null) splashHandler.removeCallbacks(splashFinish);
        if (splashAnimations != null) splashAnimations.cancel();
        if (splashOverlay != null) splashOverlay.animate().cancel();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        imageExecutor.shutdownNow();
        databaseExecutor.shutdownNow();
        super.onDestroy();
    }
}