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
import android.graphics.Typeface;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
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
    private List<ContactItem> currentMatches = new ArrayList<>();
    private int currentIndex = 0;
    private boolean showingBack = false;
    private boolean permissionsRequested = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        language = getPreferences(MODE_PRIVATE).getString("language", "en-IN");
        buildBase();
        ensurePermissionsAndStart();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildBase() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(255,255,255));
        root.setPadding(dp(16), dp(12), dp(16), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = new TextView(this);
        brand.setText("VoiceDeck");
        brand.setTextSize(24);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setTextColor(Color.rgb(25,25,25));
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setAllCaps(false);
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
        statusText.setTextColor(Color.DKGRAY);
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
        t.setTextColor(Color.rgb(30,30,30));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void showListeningPrompt(boolean listening) {
        content.removeAllViews();
        content.setGravity(Gravity.CENTER);
        TextView mic = text("🎙", 64, false);
        mic.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(150));
        content.addView(mic, mp);

        TextView title = text(language.equals("mr-IN") ? "कोणाशी बोलायचे आहे?" :
                language.equals("hi-IN") ? "आप किससे जुड़ना चाहते हैं?" :
                "Who would you like to connect with?", 24, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        heardText = text(listening ? "ऐकत आहे… / सुन रहा हूँ…" : " ", 18, false);
        heardText.setGravity(Gravity.CENTER);
        heardText.setPadding(0, dp(16), 0, dp(12));
        content.addView(heardText);

        Button listen = new Button(this);
        listen.setText(listening ? "Listening…" : "Start voice search");
        listen.setAllCaps(false);
        listen.setTextSize(18);
        listen.setMinHeight(dp(60));
        listen.setOnClickListener(v -> startListening());
        content.addView(listen, new LinearLayout.LayoutParams(-1, dp(64)));

        Button manual = new Button(this);
        manual.setText("Search manually");
        manual.setAllCaps(false);
        manual.setTextSize(17);
        manual.setOnClickListener(v -> showManualSearch());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(56));
        bp.topMargin = dp(12);
        content.addView(manual, bp);

        statusText.setText(listening ? "Voice search is active. Speak a contact name." :
                "Voice-first contact companion");
    }

    private void ensurePermissionsAndStart() {
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.READ_CONTACTS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO);
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            loadContacts();
            startListening();
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
                startListening();
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
            if (contactsOk) loadContacts();
            if (audioOk && contactsOk) startListening();
            else {
                showListeningPrompt(false);
                statusText.setText("Contacts and microphone access are needed for voice search. Manual search is available when contacts access is allowed.");
                if (!contactsOk) Toast.makeText(this, "Allow Contacts access to use VoiceDeck.", Toast.LENGTH_LONG).show();
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

    private String normalise(String value) {
        String s = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        return s.replaceAll("\\s+", " ");
    }

    private void findContact(String spoken) {
        if (contacts.isEmpty()) loadContacts();
        String query = normalise(spoken);
        if (query.isEmpty()) {
            showListeningPrompt(false);
            statusText.setText("Please say a contact name.");
            return;
        }
        List<ContactItem> exact = new ArrayList<>();
        List<ContactItem> partial = new ArrayList<>();
        for (ContactItem c : contacts) {
            String name = normalise(c.name);
            if (name.equals(query)) exact.add(c);
            else if (name.contains(query) || query.contains(name)) partial.add(c);
        }
        currentMatches = !exact.isEmpty() ? exact : partial;
        if (currentMatches.isEmpty()) {
            showListeningPrompt(false);
            statusText.setText("No contact found for “" + spoken + "”. Try again or search manually.");
        } else if (currentMatches.size() == 1) {
            currentIndex = 0;
            showCard(false);
        } else {
            showMatchChooser(spoken, currentMatches);
        }
    }

    private void showMatchChooser(String spoken, List<ContactItem> matches) {
        String[] names = new String[matches.size()];
        for (int i = 0; i < matches.size(); i++) names[i] = matches.get(i).name + "  •  " + matches.get(i).phone;
        new AlertDialog.Builder(this).setTitle("Which contact did you mean?")
                .setItems(names, (d, which) -> {
                    currentMatches = new ArrayList<>();
                    currentMatches.add(matches.get(which));
                    currentIndex = 0;
                    showCard(false);
                }).setNegativeButton("Search again", (d, w) -> startListening()).show();
    }

    private void showManualSearch() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setHint("Enter contact name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        new AlertDialog.Builder(this).setTitle("Search contacts").setView(input)
                .setPositiveButton("Search", (d, w) -> findContact(input.getText().toString()))
                .setNegativeButton("Cancel", null).show();
    }

    private void showCard(boolean back) {
        if (currentMatches.isEmpty()) return;
        showingBack = back;
        ContactItem item = currentMatches.get(Math.max(0, Math.min(currentIndex, currentMatches.size()-1)));
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable outline = new android.graphics.drawable.GradientDrawable();
        outline.setColor(Color.WHITE);
        outline.setCornerRadius(dp(20));
        outline.setStroke(dp(1), Color.rgb(220,220,220));
        card.setBackground(outline);

        if (!back) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackgroundColor(Color.rgb(235,235,235));
            photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(260)));
            if (item.photo != null) {
                try (java.io.InputStream stream = getContentResolver().openInputStream(item.photo)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) photo.setImageBitmap(bitmap);
                    else photo.setImageDrawable(null);
                } catch (Exception ignored) {}
            }
            card.addView(photo);

            TextView name = text(item.name, 25, true);
            name.setPadding(0, dp(12), 0, dp(8));
            card.addView(name);
            TextView phone = text("Mobile · " + (item.phone.isEmpty() ? "No number saved" : item.phone), 17, false);
            phone.setTextColor(Color.DKGRAY);
            phone.setPadding(0, 0, 0, dp(14));
            card.addView(phone);
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(230,230,230));
            card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);
            Button call = actionButton("☎\nCall");
            Button whatsapp = actionButton("◉\nWhatsApp");
            actions.addView(call, new LinearLayout.LayoutParams(0, dp(76), 1));
            actions.addView(whatsapp, new LinearLayout.LayoutParams(0, dp(76), 1));
            call.setOnClickListener(v -> callContact(item));
            whatsapp.setOnClickListener(v -> openWhatsApp(item));
            card.addView(actions);

            TextView hint = text("Tap card to view more details", 13, false);
            hint.setTextColor(Color.GRAY);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(4), 0, dp(4));
            card.addView(hint);
            card.setOnClickListener(v -> showCard(true));
        } else {
            LinearLayout heading = new LinearLayout(this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text("BACK OF CARD", 16, true);
            heading.addView(label, new LinearLayout.LayoutParams(0, dp(36), 1));
            TextView badge = text("Additional details", 12, true);
            badge.setTextColor(Color.DKGRAY);
            heading.addView(badge);
            card.addView(heading);
            TextView name = text(item.name, 22, true);
            name.setPadding(0, dp(8), 0, dp(12));
            card.addView(name);
            addDetail(card, "✉", "Email", item.email);
            addDetail(card, "▣", "Company / Designation", item.company);
            addDetail(card, "▦", "Birthday", item.birthday);
            addDetail(card, "⌖", "Address", item.address);
            addDetail(card, "＋", "Other saved contact information", "");
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(230,230,230));
            LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, dp(1));
            dpv.topMargin = dp(8);
            card.addView(divider, dpv);
            Button google = new Button(this);
            google.setText("Open in Google Contacts");
            google.setAllCaps(false);
            google.setTextSize(17);
            google.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, item.id));
                try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Contacts app unavailable.", Toast.LENGTH_SHORT).show(); }
            });
            card.addView(google, new LinearLayout.LayoutParams(-1, dp(56)));

            Button backToCard = new Button(this);
            backToCard.setText("← Back to contact card");
            backToCard.setAllCaps(false);
            backToCard.setTextSize(17);
            backToCard.setOnClickListener(v -> showCard(false));
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(56));
            backParams.topMargin = dp(8);
            card.addView(backToCard, backParams);
            card.setOnClickListener(v -> showCard(false));
        }

        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = new Button(this);
        prev.setText("‹  Previous");
        prev.setAllCaps(false);
        Button next = new Button(this);
        next.setText("Next  ›");
        next.setAllCaps(false);
        nav.addView(prev, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView count = text((currentIndex + 1) + " of " + currentMatches.size(), 14, false);
        count.setGravity(Gravity.CENTER);
        nav.addView(count, new LinearLayout.LayoutParams(dp(90), dp(56)));
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(56), 1));
        prev.setOnClickListener(v -> {
            if (currentMatches.size() > 1) currentIndex = (currentIndex - 1 + currentMatches.size()) % currentMatches.size();
            else currentIndex = Math.max(0, contacts.indexOf(item) - 1);
            if (currentMatches.size() == 1 && !contacts.isEmpty()) { currentMatches = new ArrayList<>(contacts); currentIndex = Math.max(0, contacts.indexOf(item)-1); }
            showCard(showingBack);
        });
        next.setOnClickListener(v -> {
            if (currentMatches.size() > 1) currentIndex = (currentIndex + 1) % currentMatches.size();
            else if (!contacts.isEmpty()) { currentMatches = new ArrayList<>(contacts); currentIndex = (contacts.indexOf(item) + 1) % contacts.size(); }
            showCard(showingBack);
        });
        content.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        statusText.setText("Contact found. Choose Call or WhatsApp.");
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
        TextView row = text(icon + "   " + (value.isEmpty() ? label : value), 17, false);
        row.setPadding(0, dp(8), 0, dp(8));
        card.addView(row);
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
        String[] choices = {"English", "मराठी", "हिन्दी"};
        int checked = language.equals("mr-IN") ? 1 : (language.equals("hi-IN") ? 2 : 0);
        new AlertDialog.Builder(this).setTitle("Voice search language")
                .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                    language = which == 1 ? "mr-IN" : (which == 2 ? "hi-IN" : "en-IN");
                    getPreferences(MODE_PRIVATE).edit().putString("language", language).apply();
                    dialog.dismiss();
                    showListeningPrompt(false);
                    statusText.setText("Language updated. Start a new voice search.");
                }).setNegativeButton("Cancel", null).show();
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
        super.onDestroy();
    }
}
