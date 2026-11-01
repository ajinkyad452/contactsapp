package com.voicedeck.app;

import android.Manifest;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

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
    private LinearLayout content;
    private TextView statusText;
    private TextView heardText;
    private SpeechRecognizer speechRecognizer;
    private String language = "en-IN";
    private final List<ContactItem> contacts = new ArrayList<>();
    private ContactNameMatcher contactNameMatcher;
    private List<ContactItem> currentMatches = new ArrayList<>();
    private int currentIndex = 0;
    private boolean showingBack = false;
    private boolean permissionsRequested = false;
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
        showListeningPrompt(false);
        ensurePermissionsAndStart();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildBase() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setPadding(dp(16), dp(28), dp(16), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = new TextView(this);
        brand.setText("VoiceDeck");
        brand.setTextSize(24);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setTextColor(COLOR_TEXT);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button settings = new Button(this);
        settings.setText("Settings");
        styleActionButton(settings, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 46);
        settings.setTextSize(15);
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, dp(8), 0, dp(12));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        statusText = new TextView(this);
        statusText.setTextColor(COLOR_SECONDARY);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(statusText);

        setContentView(root);
        showListeningPrompt(false);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(COLOR_TEXT);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
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
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setMinHeight(dp(minHeightDp));
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(roundedBackground(backgroundColor, strokeColor, 18));
        if (android.os.Build.VERSION.SDK_INT >= 21) button.setElevation(dp(2));
    }

    private void showListeningPrompt(boolean listening) {
        content.removeAllViews();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(2), dp(14), dp(2), dp(18));

        TextView eyebrow = centeredText("VOICE-FIRST CONTACTS", 12, true, COLOR_SECONDARY);
        eyebrow.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(-1, dp(28));
        content.addView(eyebrow, eyebrowParams);

        TextView title = centeredText(language.equals("mr-IN") ? "कोणाशी बोलायचे आहे?" :
                language.equals("hi-IN") ? "आप किससे जुड़ना चाहते हैं?" :
                "Who would you like to\nconnect with?", 29, true, COLOR_TEXT);
        title.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        content.addView(title, titleParams);

        TextView subtitle = centeredText(language.equals("mr-IN") ? "फक्त नाव सांगा. आम्ही संपर्क शोधू." :
                language.equals("hi-IN") ? "बस नाम बोलिए। हम आपका संपर्क ढूँढेंगे।" :
                "Just say a name. We'll find the contact for you.", 16, false, COLOR_SECONDARY);
        subtitle.setPadding(dp(12), dp(8), dp(12), dp(18));
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout voicePanel = new LinearLayout(this);
        voicePanel.setOrientation(LinearLayout.VERTICAL);
        voicePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        voicePanel.setPadding(dp(20), dp(22), dp(20), dp(22));
        voicePanel.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 26));
        if (android.os.Build.VERSION.SDK_INT >= 21) voicePanel.setElevation(dp(3));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.topMargin = dp(8);
        content.addView(voicePanel, panelParams);

        TextView mic = centeredText(listening ? "🎙" : "🎤", 43, false, COLOR_PRIMARY);
        mic.setBackground(roundedBackground(COLOR_SAGE, Color.TRANSPARENT, 48));
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        micParams.bottomMargin = dp(16);
        voicePanel.addView(mic, micParams);

        TextView prompt = centeredText(listening ?
                (language.equals("mr-IN") ? "ऐकत आहे…" : language.equals("hi-IN") ? "सुन रहा हूँ…" : "I'm listening…") :
                (language.equals("mr-IN") ? "बोलण्यासाठी तयार" : language.equals("hi-IN") ? "बोलने के लिए तैयार" : "Ready when you are"),
                20, true, COLOR_TEXT);
        voicePanel.addView(prompt, new LinearLayout.LayoutParams(-1, -2));

        heardText = centeredText(listening ?
                (language.equals("mr-IN") ? "संपर्काचे नाव सांगा" : language.equals("hi-IN") ? "कॉन्टैक्ट का नाम बोलिए" : "Say the name of your contact") :
                (language.equals("mr-IN") ? "मायक्रोफोन बटण दाबा आणि नाव सांगा" : language.equals("hi-IN") ? "माइक बटन दबाएँ और नाम बोलें" : "Tap the button below, then say a contact's name"),
                15, false, COLOR_SECONDARY);
        heardText.setPadding(dp(4), dp(8), dp(4), dp(2));
        voicePanel.addView(heardText, new LinearLayout.LayoutParams(-1, -2));

        Button listen = new Button(this);
        listen.setText(listening ?
                (language.equals("mr-IN") ? "ऐकत आहे…" : language.equals("hi-IN") ? "सुन रहा हूँ…" : "Listening…") :
                (language.equals("mr-IN") ? "बोलण्यासाठी टॅप करा" : language.equals("hi-IN") ? "बोलने के लिए टैप करें" : "Tap to speak"));
        styleActionButton(listen, COLOR_PRIMARY, Color.WHITE, COLOR_PRIMARY, 64);
        listen.setOnClickListener(v -> startListening());
        LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(-1, dp(66));
        listenParams.topMargin = dp(22);
        content.addView(listen, listenParams);

        Button manual = new Button(this);
        manual.setText(language.equals("mr-IN") ? "नाव टाइप करा" :
                language.equals("hi-IN") ? "नाम टाइप करें" : "Search by typing instead");
        styleActionButton(manual, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 58);
        manual.setOnClickListener(v -> showManualSearch());
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(-1, dp(60));
        manualParams.topMargin = dp(12);
        content.addView(manual, manualParams);

        TextView hint = centeredText("Your contacts stay on this phone", 13, false, COLOR_SECONDARY);
        hint.setPadding(0, dp(20), 0, dp(4));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        statusText.setText(listening ? "Voice search is active. Speak a contact name." :
                "Your simple way to reach the people who matter.");
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
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
        // Rebuild the local name index whenever the device contact list is loaded.
        contactNameMatcher = new ContactNameMatcher(contacts, Collections.emptyMap());
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
                    if (matches != null && !matches.isEmpty()) findContact(matches.get(0));
                    else {
                        showListeningPrompt(false);
                        statusText.setText("No speech recognised. Try again.");
                    }

                    if (matches != null) {
                        for (String result : matches) {
                            android.util.Log.d("VoiceDeckSpeech", "HEARD: " + result);
                        }
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> p = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (p != null && !p.isEmpty() && heardText != null) heardText.setText(p.get(0));

                    if (p != null && !p.isEmpty()) {
                        android.util.Log.d("VoiceDeckSpeech", "PARTIAL: " + p.get(0));
                    }
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

    private void findContact(String spoken) {
        if (contacts.isEmpty()) loadContacts();

        if (contactNameMatcher == null) {
            contactNameMatcher = new ContactNameMatcher(contacts, Collections.emptyMap());
        }

        ContactNameMatcher.Result result = contactNameMatcher.resolve(spoken);

        if (result.getStatus() == ContactNameMatcher.Status.NO_MATCH) {
            showListeningPrompt(false);
            statusText.setText("No contact name found in “" + spoken
                    + "”. Try again or search manually.");
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

    private void showMatchChooser(String spoken, List<ContactItem> matches) {
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("Which contact did you mean?"));
        TextView subtitle = text("Choose the person you want to reach.", 15, false);
        subtitle.setTextColor(COLOR_SECONDARY);
        subtitle.setPadding(0, 0, 0, dp(12));
        panel.addView(subtitle);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < matches.size(); i++) {
            final int selectedIndex = i;
            ContactItem match = matches.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(13), dp(16), dp(13));
            row.setBackground(roundedBackground(COLOR_BACKGROUND, COLOR_BORDER, 16));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(9);
            rows.addView(row, rowParams);

            TextView name = text(match.name, 18, true);
            row.addView(name);
            TextView phone = text(match.phone == null || match.phone.isEmpty()
                    ? "No mobile number saved" : match.phone, 15, false);
            phone.setTextColor(COLOR_SECONDARY);
            LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(-1, -2);
            phoneParams.topMargin = dp(4);
            row.addView(phone, phoneParams);

            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                // Keep the complete result set so Previous / Next can browse these matches.
                dialogHolder[0].dismiss();
                currentMatches = new ArrayList<>(matches);
                currentIndex = selectedIndex;
                showCard(false);
            });
        }
        listScroll.addView(rows);
        panel.addView(listScroll, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setNegativeButton("Search again", (d, w) -> startListening())
                .setNeutralButton("Cancel", null).create();
        dialogHolder[0] = dialog;
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_PRIMARY);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(COLOR_SECONDARY);
        });
        dialog.show();
    }

    private void showManualSearch() {
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("Search contacts"));
        TextView hint = text("Type the name of the person you're looking for.", 15, false);
        hint.setTextColor(COLOR_SECONDARY);
        hint.setPadding(0, 0, 0, dp(12));
        panel.addView(hint);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(19);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_SECONDARY);
        input.setHint("Enter contact name");
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(roundedBackground(COLOR_BACKGROUND, COLOR_BORDER, 14));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        panel.addView(input, new LinearLayout.LayoutParams(-1, dp(60)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setPositiveButton("Search", null)
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            }
            Button search = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            search.setTextColor(COLOR_PRIMARY);
            cancel.setTextColor(COLOR_SECONDARY);
            search.setOnClickListener(v -> {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) {
                    input.setError("Enter a contact name");
                    input.requestFocus();
                } else {
                    dialog.dismiss();
                    findContact(query);
                }
            });
            input.requestFocus();
        });
        dialog.show();
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

    private void showCard(boolean back) {
        if (currentMatches.isEmpty()) return;
        showingBack = back;
        ContactItem item = currentMatches.get(Math.max(0, Math.min(currentIndex, currentMatches.size() - 1)));
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(2), dp(10), dp(2), dp(16));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        card.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 26));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dp(3));

        if (!back) {
            TextView eyebrow = centeredText("CONTACT CARD", 12, true, COLOR_SECONDARY);
            eyebrow.setLetterSpacing(0.12f);
            card.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(28)));

            LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(-1, dp(230));
            photoParams.topMargin = dp(8);
            FrameLayout photoFrame = new FrameLayout(this);
            photoFrame.setBackground(roundedBackground(COLOR_SAGE, Color.TRANSPARENT, 22));
            photoFrame.setClipToOutline(true);
            card.addView(photoFrame, photoParams);
            Bitmap contactBitmap = null;
            if (item.photo != null) {
                try (java.io.InputStream stream = getContentResolver().openInputStream(item.photo)) {
                    contactBitmap = BitmapFactory.decodeStream(stream);
                } catch (Exception ignored) { }
            }
            if (contactBitmap != null) {
                showContactPhoto(photoFrame, contactBitmap);
            } else {
                ImageView fallback = new ImageView(this);
                fallback.setScaleType(ImageView.ScaleType.CENTER_CROP);
                fallback.setImageResource(R.drawable.voicedeck_default);

                photoFrame.addView(
                    fallback,
                    new FrameLayout.LayoutParams(-1, -1)
                );
            }

            TextView name = centeredText(item.name, 28, true, COLOR_TEXT);
            name.setPadding(dp(4), dp(18), dp(4), dp(6));
            card.addView(name, new LinearLayout.LayoutParams(-1, -2));

            TextView phone = centeredText(item.phone.isEmpty() ? "No mobile number saved" : item.phone,
                    19, false, COLOR_SECONDARY);
            phone.setPadding(0, 0, 0, dp(18));
            card.addView(phone, new LinearLayout.LayoutParams(-1, -2));

            View divider = new View(this);
            divider.setBackgroundColor(COLOR_BORDER);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.bottomMargin = dp(16);
            card.addView(divider, dividerParams);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);
            Button call = new Button(this);
            call.setText("☎  Call");
            styleActionButton(call, COLOR_PRIMARY, Color.WHITE, COLOR_PRIMARY, 68);
            Button whatsapp = new Button(this);
            whatsapp.setText("◉  WhatsApp");
            styleActionButton(whatsapp, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 68);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(68), 1);
            actionParams.setMargins(dp(3), 0, dp(6), 0);
            actions.addView(call, actionParams);
            LinearLayout.LayoutParams whatsappParams = new LinearLayout.LayoutParams(0, dp(68), 1);
            whatsappParams.setMargins(dp(6), 0, dp(3), 0);
            actions.addView(whatsapp, whatsappParams);
            call.setOnClickListener(v -> callContact(item));
            whatsapp.setOnClickListener(v -> openWhatsApp(item));
            card.addView(actions);

            TextView hint = centeredText("Tap for email, birthday and other details", 13,
                    false, COLOR_SECONDARY);
            hint.setPadding(0, dp(14), 0, dp(2));
            card.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            card.setOnClickListener(v -> showCard(true));
        } else {
            TextView eyebrow = centeredText("CONTACT DETAILS", 12, true, COLOR_SECONDARY);
            eyebrow.setLetterSpacing(0.12f);
            card.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(30)));

            TextView name = centeredText(item.name, 25, true, COLOR_TEXT);
            name.setPadding(dp(4), dp(8), dp(4), dp(16));
            card.addView(name, new LinearLayout.LayoutParams(-1, -2));

            addDetail(card, "✉", "Email", item.email);
            addDetail(card, "▣", "Company / Designation", item.company);
            addDetail(card, "▦", "Birthday", item.birthday);
            addDetail(card, "⌖", "Address", item.address);
            addDetail(card, "☎", "Mobile", item.phone);

            View divider = new View(this);
            divider.setBackgroundColor(COLOR_BORDER);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.topMargin = dp(14);
            dividerParams.bottomMargin = dp(14);
            card.addView(divider, dividerParams);

            Button google = new Button(this);
            google.setText("Open in Google Contacts");
            styleActionButton(google, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 58);
            google.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, item.id));
                try { startActivity(i); }
                catch (Exception e) { Toast.makeText(this, "Contacts app unavailable.", Toast.LENGTH_SHORT).show(); }
            });
            card.addView(google, new LinearLayout.LayoutParams(-1, dp(58)));

            Button backToCard = new Button(this);
            backToCard.setText("← Back to contact card");
            styleActionButton(backToCard, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 56);
            backToCard.setOnClickListener(v -> showCard(false));
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(58));
            backParams.topMargin = dp(10);
            card.addView(backToCard, backParams);
        }

        // Keep front and back card shells identical in height so navigation never jumps.
        content.addView(card, new LinearLayout.LayoutParams(-1, dp(635)));


        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(72));
        navParams.topMargin = dp(12);
        Button prev = new Button(this);
        prev.setText("‹  Previous");
        styleActionButton(prev, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 54);
        Button next = new Button(this);
        next.setText("Next  ›");
        styleActionButton(next, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 54);
        Button record = new Button(this);
        record.setText("🎙");
        record.setContentDescription("Record another contact");
        record.setTextSize(27);
        record.setGravity(Gravity.CENTER);
        record.setPadding(0, 0, 0, 0);
        record.setMinWidth(dp(68));
        record.setMinHeight(dp(68));
        record.setBackground(roundedBackground(COLOR_PRIMARY, COLOR_PRIMARY, 100));
        record.setTextColor(Color.WHITE);
        record.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        if (android.os.Build.VERSION.SDK_INT >= 21) record.setElevation(dp(3));
        nav.addView(prev, new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams recordParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        recordParams.leftMargin = dp(10);
        recordParams.rightMargin = dp(10);
        nav.addView(record, recordParams);
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(56), 1));
        record.setOnClickListener(v -> returnToVoiceSearch());
        TextView count = centeredText((currentIndex + 1) + " of " + currentMatches.size(), 13, true, COLOR_SECONDARY);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, dp(28));
        boolean canBrowseMatches = currentMatches.size() > 1;
        prev.setEnabled(canBrowseMatches);
        next.setEnabled(canBrowseMatches);
        if (!canBrowseMatches) {
            prev.setAlpha(0.45f);
            next.setAlpha(0.45f);
        }
        prev.setOnClickListener(v -> {
            if (currentMatches.size() > 1) {
                currentIndex = (currentIndex - 1 + currentMatches.size()) % currentMatches.size();
                showCard(showingBack);
            }
        });
        next.setOnClickListener(v -> {
            if (currentMatches.size() > 1) {
                currentIndex = (currentIndex + 1) % currentMatches.size();
                showCard(showingBack);
            }
        });
        content.addView(nav, navParams);
        content.addView(count, countParams);
        statusText.setText(back ? "Contact details. Return to the card or record another contact." : "Contact found. Choose Call or WhatsApp.");
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

    private void showSettings() {
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(2), dp(12), dp(2), dp(18));
        TextView heading = centeredText("SETTINGS", 13, true, COLOR_SECONDARY);
        heading.setLetterSpacing(0.12f);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView title = centeredText("Make VoiceDeck yours", 27, true, COLOR_TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8); titleParams.bottomMargin = dp(18);
        content.addView(title, titleParams);

        LinearLayout languageCard = new LinearLayout(this);
        languageCard.setOrientation(LinearLayout.VERTICAL);
        languageCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        languageCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 22));
        languageCard.addView(text("Voice search language", 19, true));
        TextView languageValue = text(currentLanguageName(), 16, false);
        languageValue.setTextColor(COLOR_SECONDARY);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.topMargin = dp(6); languageCard.addView(languageValue, valueParams);
        Button changeLanguage = new Button(this); changeLanguage.setText("Change language");
        styleActionButton(changeLanguage, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 54);
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(-1, dp(56));
        changeParams.topMargin = dp(14); languageCard.addView(changeLanguage, changeParams);
        changeLanguage.setOnClickListener(v -> showLanguageChooser());
        content.addView(languageCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout permissionCard = new LinearLayout(this);
        permissionCard.setOrientation(LinearLayout.VERTICAL);
        permissionCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        permissionCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 22));
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(-1, -2);
        permissionParams.topMargin = dp(14); content.addView(permissionCard, permissionParams);
        permissionCard.addView(text("App permissions", 19, true));
        addPermissionStatus(permissionCard, "Contacts", Manifest.permission.READ_CONTACTS);
        addPermissionStatus(permissionCard, "Microphone", Manifest.permission.RECORD_AUDIO);
        TextView hint = text("VoiceDeck accesses contacts to find people. The microphone is used only after you tap the microphone button.", 14, false);
        hint.setTextColor(COLOR_SECONDARY); hint.setPadding(0, dp(12), 0, 0); permissionCard.addView(hint);

        LinearLayout legalCard = new LinearLayout(this);
        legalCard.setOrientation(LinearLayout.VERTICAL);
        legalCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        legalCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 22));
        LinearLayout.LayoutParams legalParams = new LinearLayout.LayoutParams(-1, -2);
        legalParams.topMargin = dp(14);
        content.addView(legalCard, legalParams);
        legalCard.addView(text("Legal", 19, true));

        Button privacy = new Button(this);
        privacy.setText("Privacy Policy  ↗");
        styleActionButton(privacy, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 52);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, dp(54));
        privacyParams.topMargin = dp(10);
        legalCard.addView(privacy, privacyParams);
        privacy.setOnClickListener(v -> openLegalPage("https://ajinkyadube.com/apps/privacy.html"));

        Button terms = new Button(this);
        terms.setText("Terms & Conditions  ↗");
        styleActionButton(terms, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 52);
        LinearLayout.LayoutParams termsParams = new LinearLayout.LayoutParams(-1, dp(54));
        termsParams.topMargin = dp(8);
        legalCard.addView(terms, termsParams);
        terms.setOnClickListener(v -> openLegalPage("https://ajinkyadube.com/apps/terms.html"));

        Button done = new Button(this); done.setText("Done");
        styleActionButton(done, COLOR_PRIMARY, Color.WHITE, COLOR_PRIMARY, 58);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(60));
        doneParams.topMargin = dp(18); content.addView(done, doneParams);
        done.setOnClickListener(v -> { if (!currentMatches.isEmpty()) showCard(showingBack); else showListeningPrompt(false); });
        statusText.setText("Settings. Choose your voice search language and review permissions.");
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
        if (showingBack && !currentMatches.isEmpty()) {
            showCard(false);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}
