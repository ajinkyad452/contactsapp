package com.ajinkyadube.voicedeck;

import android.content.Context;
import android.content.res.Configuration;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Requires Android: verifies resource selection and Android's formatted string handling. */
public class LanguageResourcesTest {
    @Test public void selectedLanguageOverridesTheDeviceLanguageAndCanSwitchBack() {
        assertEquals("Settings", localized("en-IN").getString(R.string.settings));
        assertEquals("सेटिंग्स", localized("hi-IN").getString(R.string.settings));
        assertEquals("सेटिंग्ज", localized("mr-IN").getString(R.string.settings));
        assertEquals("Settings", localized("en-IN").getString(R.string.settings));
    }

    @Test public void dynamicMessagesKeepNamesAndNumbersInEveryLanguage() {
        for (String tag : new String[]{"en-IN", "hi-IN", "mr-IN"}) {
            Context context = localized(tag);
            assertTrue(context.getString(R.string.confirm_remember_alias, "SAMPLE ALIAS", "SAMPLE CONTACT")
                    .contains("SAMPLE ALIAS"));
            assertTrue(context.getString(R.string.confirm_remember_alias, "SAMPLE ALIAS", "SAMPLE CONTACT")
                    .contains("SAMPLE CONTACT"));
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            assertTrue(context.getString(R.string.voice_error_unknown, 7)
                    .contains(String.format(locale, "%d", 7)));
            assertTrue(context.getString(R.string.contact_position, 1, 2)
                    .contains(String.format(locale, "%d", 2)));
        }
    }

    private Context localized(String tag) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(tag));
        return base.createConfigurationContext(configuration);
    }
}
