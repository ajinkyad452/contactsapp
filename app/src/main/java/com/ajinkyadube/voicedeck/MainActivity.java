package com.ajinkyadube.voicedeck;

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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private LinearLayout content;
    private TextView statusText;
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
        initializeSpeechDictionary();
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
        if (aliasSetupLink != null) aliasSetupLink.setVisibility(View.GONE);
        pendingAliasForSetup = null;
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
                    findContact(query, true);
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
        showingDictionaryManager = false;
        showingAliasManager = false;
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

        LinearLayout dictionaryCard = new LinearLayout(this);
        dictionaryCard.setOrientation(LinearLayout.VERTICAL);
        dictionaryCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        dictionaryCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 22));
        LinearLayout.LayoutParams dictionaryParams = new LinearLayout.LayoutParams(-1, -2);
        dictionaryParams.topMargin = dp(14);
        content.addView(dictionaryCard, dictionaryParams);
        dictionaryCard.addView(text("Speech dictionary", 19, true));
        TextView dictionaryHint = text(
                "Manage words and phrases VoiceDeck removes before matching contact names.",
                14, false);
        dictionaryHint.setTextColor(COLOR_SECONDARY);
        LinearLayout.LayoutParams dictionaryHintParams = new LinearLayout.LayoutParams(-1, -2);
        dictionaryHintParams.topMargin = dp(6);
        dictionaryCard.addView(dictionaryHint, dictionaryHintParams);
        Button manageDictionary = new Button(this);
        manageDictionary.setText("Manage vocabulary");
        styleActionButton(manageDictionary, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 54);
        LinearLayout.LayoutParams manageDictionaryParams = new LinearLayout.LayoutParams(-1, dp(56));
        manageDictionaryParams.topMargin = dp(14);
        dictionaryCard.addView(manageDictionary, manageDictionaryParams);
        manageDictionary.setOnClickListener(v -> showDictionaryManager(language));

        LinearLayout aliasCard = new LinearLayout(this);
        aliasCard.setOrientation(LinearLayout.VERTICAL);
        aliasCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        aliasCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 22));
        LinearLayout.LayoutParams aliasCardParams = new LinearLayout.LayoutParams(-1, -2);
        aliasCardParams.topMargin = dp(14);
        content.addView(aliasCard, aliasCardParams);
        aliasCard.addView(text("Contact aliases", 19, true));
        TextView aliasHint = text(
                "Manage the nicknames VoiceDeck remembers for your contacts.", 14, false);
        aliasHint.setTextColor(COLOR_SECONDARY);
        LinearLayout.LayoutParams aliasHintParams = new LinearLayout.LayoutParams(-1, -2);
        aliasHintParams.topMargin = dp(6);
        aliasCard.addView(aliasHint, aliasHintParams);
        Button manageAliases = new Button(this);
        manageAliases.setText("Manage aliases");
        styleActionButton(manageAliases, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 54);
        LinearLayout.LayoutParams manageAliasesParams = new LinearLayout.LayoutParams(-1, dp(56));
        manageAliasesParams.topMargin = dp(14);
        aliasCard.addView(manageAliases, manageAliasesParams);
        manageAliases.setOnClickListener(v -> showAliasManager());

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

    /** Displays all locally saved aliases and their linked Android contacts. */
    private void showAliasManager() {
        showingAliasManager = true;
        showingDictionaryManager = false;
        if (!contactsLoaded) loadContacts();
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(2), dp(12), dp(2), dp(18));

        TextView heading = centeredText("CONTACT ALIASES", 13, true, COLOR_SECONDARY);
        heading.setLetterSpacing(0.12f);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView title = centeredText("Manage aliases", 26, true, COLOR_TEXT);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(
                "Add, edit or remove the nicknames VoiceDeck uses to find your contacts. " +
                        "Aliases are stored only on this device.", 14, false);
        description.setTextColor(COLOR_SECONDARY);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.setMargins(dp(8), dp(8), dp(8), dp(14));
        content.addView(description, descriptionParams);

        Button add = new Button(this);
        add.setText("Add alias");
        styleActionButton(add, COLOR_PRIMARY, Color.WHITE, COLOR_PRIMARY, 52);
        content.addView(add, new LinearLayout.LayoutParams(-1, dp(54)));
        add.setOnClickListener(v -> showAliasEditor(null));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setHint("Search aliases or contacts");
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        search.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 14));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(54));
        searchParams.topMargin = dp(12);
        content.addView(search, searchParams);

        LinearLayout listCard = new LinearLayout(this);
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        listCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 20));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(12);
        content.addView(listCard, listParams);
        listCard.addView(text("Saved aliases", 18, true));
        TextView loading = text("Loading aliases…", 14, false);
        loading.setTextColor(COLOR_SECONDARY);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.topMargin = dp(10);
        listCard.addView(loading, loadingParams);

        Button back = new Button(this);
        back.setText("Back to settings");
        styleActionButton(back, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 54);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(56));
        backParams.topMargin = dp(16);
        content.addView(back, backParams);
        back.setOnClickListener(v -> showSettings());

        if (contactAliasRepository == null) {
            initializeSpeechDictionary();
            loading.setText("Preparing alias storage. Please reopen this screen in a moment.");
            return;
        }
        contactAliasRepository.getAllAliases(new ContactAliasRepository.ResultCallback<List<ContactAlias>>() {
            @Override public void onSuccess(List<ContactAlias> aliases) {
                if (isFinishing() || isDestroyed() || !showingAliasManager) return;
                listCard.removeView(loading);
                TextView empty = text("No aliases saved yet. Add a nickname to get started.", 14, false);
                empty.setTextColor(COLOR_SECONDARY);
                listCard.addView(empty);
                Runnable render = () -> renderAliasRows(listCard, empty, aliases,
                        search.getText() == null ? "" : search.getText().toString());
                search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
                    @Override public void afterTextChanged(Editable s) { }
                });
                render.run();
            }
            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed() || !showingAliasManager) return;
                loading.setText("Couldn't load saved aliases. Please try again.");
            }
        });
    }

    private void renderAliasRows(LinearLayout parent, TextView empty,
                                 List<ContactAlias> aliases, String filter) {
        // Remove rendered rows but retain the heading and empty-state view.
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child != empty && child.getTag() != null && "alias-row".equals(child.getTag())) {
                parent.removeViewAt(i);
            }
        }
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (ContactAlias alias : aliases) {
            ContactItem linked = null;
            for (ContactItem contact : contacts) if (contact.id == alias.contactId) { linked = contact; break; }
            String contactName = linked == null ? "Contact no longer available" : linked.name;
            if (!query.isEmpty() && !alias.alias.toLowerCase(Locale.ROOT).contains(query)
                    && !contactName.toLowerCase(Locale.ROOT).contains(query)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setTag("alias-row");
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(12), 0, dp(12));
            TextView aliasName = text(alias.alias, 17, true);
            row.addView(aliasName);
            TextView details = text("Linked to " + contactName + "  ·  "
                    + dictionaryLanguageName(alias.languageTag), 13, false);
            details.setTextColor(linked == null ? Color.rgb(160, 75, 55) : COLOR_SECONDARY);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
            detailsParams.topMargin = dp(3);
            row.addView(details, detailsParams);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            Button edit = new Button(this);
            edit.setText("Edit");
            styleActionButton(edit, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 40);
            Button remove = new Button(this);
            remove.setText("Delete");
            styleActionButton(remove, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 40);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2, dp(42));
            actionParams.leftMargin = dp(8);
            actions.addView(edit, actionParams);
            actions.addView(remove, actionParams);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
            actionsParams.topMargin = dp(6);
            row.addView(actions, actionsParams);
            edit.setOnClickListener(v -> showAliasEditor(alias));
            remove.setOnClickListener(v -> confirmDeleteAlias(alias));
            parent.addView(row);
            View divider = new View(this);
            divider.setTag("alias-row");
            divider.setBackgroundColor(COLOR_BORDER);
            parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            shown++;
        }
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        empty.setText(aliases.isEmpty() ? "No aliases saved yet. Add a nickname to get started."
                : "No aliases match your search.");
    }

    private void showAliasEditor(ContactAlias existing) {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts with phone numbers are available.", Toast.LENGTH_LONG).show();
            return;
        }
        final ContactItem[] selectedContact = new ContactItem[1];
        if (existing != null) {
            for (ContactItem item : contacts) if (item.id == existing.contactId) { selectedContact[0] = item; break; }
        }
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(existing == null ? "Add alias" : "Edit alias"));
        TextView hint = text("Choose the contact and the name you want to use when speaking to VoiceDeck.", 14, false);
        hint.setTextColor(COLOR_SECONDARY);
        panel.addView(hint);
        Button contactButton = new Button(this);
        contactButton.setText(selectedContact[0] == null ? "Select contact" : selectedContact[0].name);
        styleActionButton(contactButton, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 48);
        LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(-1, dp(52));
        contactParams.topMargin = dp(10);
        panel.addView(contactButton, contactParams);
        contactButton.setOnClickListener(v -> showAliasManagementContactPicker(selectedContact, contactButton));

        EditText aliasInput = new EditText(this);
        aliasInput.setSingleLine(true);
        aliasInput.setTextSize(17);
        aliasInput.setHint("Nickname, for example Aai");
        aliasInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams aliasParams = new LinearLayout.LayoutParams(-1, dp(54));
        aliasParams.topMargin = dp(8);
        panel.addView(aliasInput, aliasParams);
        if (existing != null) aliasInput.setText(existing.alias);

        TextView languageLabel = text("Spoken language", 15, true);
        LinearLayout.LayoutParams languageLabelParams = new LinearLayout.LayoutParams(-1, -2);
        languageLabelParams.topMargin = dp(8);
        panel.addView(languageLabel, languageLabelParams);
        RadioGroup languages = new RadioGroup(this);
        languages.setOrientation(RadioGroup.VERTICAL);
        String[] languageTags = {"en-IN", "hi-IN", "mr-IN"};
        String[] languageLabels = {"English", "हिन्दी", "मराठी"};
        for (int i = 0; i < languageTags.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(100 + i);
            option.setText(languageLabels[i]);
            option.setTextColor(COLOR_TEXT);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(COLOR_PRIMARY));
            option.setChecked((existing == null ? language : existing.languageTag).equals(languageTags[i]));
            languages.addView(option);
        }
        panel.addView(languages);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(
                    roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setTextColor(COLOR_PRIMARY);
            save.setOnClickListener(v -> {
                String aliasText = aliasInput.getText().toString().trim();
                String normalized = SpeechCommandFilter.normalizeTerm(aliasText);
                if (selectedContact[0] == null) {
                    Toast.makeText(this, "Please select a contact.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (normalized.isEmpty() || aliasText.length() > 80 || normalized.split("\\s+").length > 8) {
                    aliasInput.setError("Enter a nickname of up to 80 characters and 8 words.");
                    return;
                }
                int selectedLanguageIndex = Math.max(0, languages.indexOfChild(
                        languages.findViewById(languages.getCheckedRadioButtonId())));
                String selectedLanguage = languageTags[Math.min(selectedLanguageIndex, languageTags.length - 1)];
                ContactAlias entry = existing == null
                        ? new ContactAlias(selectedContact[0].id, aliasText, normalized, selectedLanguage, System.currentTimeMillis())
                        : new ContactAlias(selectedContact[0].id, aliasText, normalized, selectedLanguage, existing.createdAt);
                if (existing != null) entry.id = existing.id;
                if (existing == null) {
                    contactAliasRepository.insert(entry, new ContactAliasRepository.ResultCallback<Long>() {
                        @Override public void onSuccess(Long id) {
                            if (id == null || id <= 0) { Toast.makeText(MainActivity.this, "That nickname is already linked to this contact in this language.", Toast.LENGTH_LONG).show(); return; }
                            dialog.dismiss(); onAliasManagementSaved("Alias added.");
                        }
                        @Override public void onError(Exception error) { Toast.makeText(MainActivity.this, "Couldn't add this alias. Please try again.", Toast.LENGTH_LONG).show(); }
                    });
                } else {
                    contactAliasRepository.update(entry, new ContactAliasRepository.ResultCallback<Integer>() {
                        @Override public void onSuccess(Integer updated) {
                            if (updated == null || updated <= 0) { Toast.makeText(MainActivity.this, "That nickname is already linked to this contact in this language.", Toast.LENGTH_LONG).show(); return; }
                            dialog.dismiss(); onAliasManagementSaved("Alias updated.");
                        }
                        @Override public void onError(Exception error) { Toast.makeText(MainActivity.this, "Couldn't update this alias. Please try again.", Toast.LENGTH_LONG).show(); }
                    });
                }
            });
        });
        dialog.show();
    }

    private void showAliasManagementContactPicker(ContactItem[] selected, Button target) {
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
        showingDictionaryManager = true;
        dictionaryManagerLanguage = selectedLanguage;
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(2), dp(12), dp(2), dp(18));

        TextView heading = centeredText("SPEECH DICTIONARY", 13, true, COLOR_SECONDARY);
        heading.setLetterSpacing(0.12f);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView title = centeredText("Manage vocabulary", 26, true, COLOR_TEXT);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(
                "Enable or disable words, add phrases you use, or restore the original words. " +
                        "Essential call commands stay enabled. Changes stay on this device.", 14, false);
        description.setTextColor(COLOR_SECONDARY);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.setMargins(dp(8), dp(8), dp(8), dp(14));
        content.addView(description, descriptionParams);

        LinearLayout languageCard = new LinearLayout(this);
        languageCard.setGravity(Gravity.CENTER_VERTICAL);
        languageCard.setPadding(dp(14), dp(8), dp(10), dp(8));
        languageCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 18));
        TextView languageLabel = text("Dictionary language", 16, true);
        languageCard.addView(languageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Button languageButton = new Button(this);
        languageButton.setText(dictionaryLanguageName(dictionaryManagerLanguage) + " ▾");
        styleActionButton(languageButton, COLOR_SAGE, COLOR_PRIMARY, COLOR_SAGE, 44);
        languageCard.addView(languageButton);
        languageButton.setOnClickListener(v -> showDictionaryLanguageChooser());
        content.addView(languageCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(12);
        content.addView(actionRow, actionParams);
        Button add = new Button(this);
        add.setText("Add word or phrase");
        styleActionButton(add, COLOR_PRIMARY, Color.WHITE, COLOR_PRIMARY, 50);
        actionRow.addView(add, new LinearLayout.LayoutParams(-1, dp(52)));
        add.setOnClickListener(v -> showAddDictionaryEntryDialog());
        Button restore = new Button(this);
        restore.setText("Restore defaults");
        styleActionButton(restore, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 50);
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, dp(52));
        restoreParams.topMargin = dp(8);
        actionRow.addView(restore, restoreParams);
        restore.setOnClickListener(v -> confirmRestoreDictionaryDefaults());

        LinearLayout listCard = new LinearLayout(this);
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        listCard.setBackground(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 20));
        LinearLayout.LayoutParams listCardParams = new LinearLayout.LayoutParams(-1, -2);
        listCardParams.topMargin = dp(14);
        content.addView(listCard, listCardParams);
        listCard.addView(text("Words and phrases", 18, true));
        TextView loading = text("Loading local dictionary…", 14, false);
        loading.setTextColor(COLOR_SECONDARY);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.topMargin = dp(10);
        listCard.addView(loading, loadingParams);

        Button back = new Button(this);
        back.setText("Back to settings");
        styleActionButton(back, COLOR_SURFACE, COLOR_PRIMARY, COLOR_BORDER, 54);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(56));
        backParams.topMargin = dp(16);
        content.addView(back, backParams);
        back.setOnClickListener(v -> showSettings());

        if (speechDictionaryRepository == null) {
            initializeSpeechDictionary();
            loading.setText("Preparing local dictionary. Please reopen this screen in a moment.");
            return;
        }
        speechDictionaryRepository.getEntriesForLanguage(
                dictionaryManagerLanguage,
                new SpeechDictionaryRepository.ResultCallback<List<SpeechDictionaryEntry>>() {
                    @Override public void onSuccess(List<SpeechDictionaryEntry> entries) {
                        if (isFinishing() || isDestroyed() || !showingDictionaryManager
                                || !dictionaryManagerLanguage.equals(selectedLanguage)) return;
                        listCard.removeView(loading);
                        if (entries.isEmpty()) {
                            TextView empty = text("No entries yet. Add a word or restore defaults.", 14, false);
                            empty.setTextColor(COLOR_SECONDARY);
                            listCard.addView(empty);
                            return;
                        }
                        addDictionaryEntryRows(listCard, entries);
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed() || !showingDictionaryManager) return;
                        loading.setText("Couldn't load the local dictionary. Please try again.");
                    }
                }
        );
    }

    private void addDictionaryEntryRows(LinearLayout parent, List<SpeechDictionaryEntry> entries) {
        String previousCategory = null;
        for (SpeechDictionaryEntry entry : entries) {
            if (!entry.category.equals(previousCategory)) {
                TextView category = text(dictionaryCategoryName(entry.category), 13, true);
                category.setTextColor(COLOR_SECONDARY);
                category.setPadding(0, dp(14), 0, dp(4));
                parent.addView(category);
                previousCategory = entry.category;
            }

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(5), 0, dp(5));
            CheckBox enabled = new CheckBox(this);
            enabled.setText(entry.term + (entry.isDefault ? "  ·  Default" : "  ·  Custom"));
            enabled.setTextSize(15);
            enabled.setTextColor(COLOR_TEXT);
            enabled.setButtonTintList(android.content.res.ColorStateList.valueOf(COLOR_PRIMARY));
            enabled.setChecked(entry.enabled);
            enabled.setEnabled(!entry.isProtected);
            row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
            if (entry.isProtected) {
                TextView protectedLabel = text("Essential", 11, true);
                protectedLabel.setTextColor(COLOR_PRIMARY);
                row.addView(protectedLabel);
            } else if (!entry.isDefault) {
                Button remove = new Button(this);
                remove.setText("Remove");
                styleActionButton(remove, COLOR_SURFACE, COLOR_SECONDARY, COLOR_BORDER, 36);
                remove.setTextSize(12);
                row.addView(remove);
                remove.setOnClickListener(v -> confirmDeleteCustomEntry(entry));
            }
            parent.addView(row);
            View divider = new View(this);
            divider.setTag("alias-row");
            divider.setBackgroundColor(COLOR_BORDER);
            parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            enabled.setOnCheckedChangeListener((button, checked) -> {
                if (entry.enabled == checked) return;
                speechDictionaryRepository.setEnabled(entry.id, checked,
                        new SpeechDictionaryRepository.ResultCallback<Integer>() {
                            @Override public void onSuccess(Integer changed) {
                                if (!showingDictionaryManager || isFinishing() || isDestroyed()) return;
                                if (changed == null || changed == 0) {
                                    Toast.makeText(MainActivity.this,
                                            "This essential command cannot be disabled.",
                                            Toast.LENGTH_SHORT).show();
                                }
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
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("Add vocabulary"));
        TextView hint = text("Enter a word or short phrase in " +
                dictionaryLanguageName(dictionaryManagerLanguage) + ".", 14, false);
        hint.setTextColor(COLOR_SECONDARY);
        panel.addView(hint);
        EditText termInput = new EditText(this);
        termInput.setSingleLine(true);
        termInput.setTextSize(17);
        termInput.setHint("For example: please call");
        termInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(56));
        inputParams.topMargin = dp(10);
        panel.addView(termInput, inputParams);
        panel.addView(text("Category", 15, true));
        RadioGroup categories = new RadioGroup(this);
        categories.setOrientation(RadioGroup.VERTICAL);
        String[] categoryLabels = {"Command", "Greeting", "Filler"};
        String[] categoryValues = {SpeechDictionaryEntry.CATEGORY_COMMAND,
                SpeechDictionaryEntry.CATEGORY_GREETING, SpeechDictionaryEntry.CATEGORY_FILLER};
        for (int i = 0; i < categoryLabels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(categoryLabels[i]);
            option.setId(i + 1);
            option.setTextColor(COLOR_TEXT);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(COLOR_PRIMARY));
            option.setChecked(i == 0);
            categories.addView(option);
        }
        panel.addView(categories);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setPositiveButton("Add", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(roundedBackground(COLOR_SURFACE, COLOR_BORDER, 24));
            }
            Button addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setTextColor(COLOR_PRIMARY);
            addButton.setOnClickListener(v -> {
                String term = termInput.getText().toString().trim();
                String normalized = SpeechCommandFilter.normalizeTerm(term);
                if (normalized.isEmpty()) {
                    termInput.setError("Enter a word or phrase.");
                    return;
                }
                if (term.length() > 80 || normalized.split("\\s+").length > 8) {
                    termInput.setError("Use 80 characters or fewer and no more than 8 words.");
                    return;
                }
                int selectedCategory = categories.getCheckedRadioButtonId() - 1;
                if (selectedCategory < 0 || selectedCategory >= categoryValues.length) selectedCategory = 0;
                String category = categoryValues[selectedCategory];
                SpeechDictionaryEntry entry = new SpeechDictionaryEntry(term, normalized,
                        dictionaryManagerLanguage, category, true, false, false);
                speechDictionaryRepository.insert(entry,
                        new SpeechDictionaryRepository.ResultCallback<Long>() {
                            @Override public void onSuccess(Long id) {
                                if (id == null || id < 0) {
                                    Toast.makeText(MainActivity.this,
                                            "That word or phrase already exists in this category.",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this, "Vocabulary added.", Toast.LENGTH_SHORT).show();
                                if (dictionaryManagerLanguage.equals(language)) loadSpeechDictionary();
                                showDictionaryManager(dictionaryManagerLanguage);
                            }

                            @Override public void onError(Exception error) {
                                Toast.makeText(MainActivity.this,
                                        "Couldn't save this vocabulary entry.", Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });
        dialog.show();
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
        if (showingDictionaryManager) {
            showSettings();
            return;
        }
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
        databaseExecutor.shutdownNow();
        super.onDestroy();
    }
}
