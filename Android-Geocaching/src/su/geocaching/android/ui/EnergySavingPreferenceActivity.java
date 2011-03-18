package su.geocaching.android.ui;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import su.geocaching.android.controller.Controller;
import su.geocaching.android.controller.GpsUpdateFrequency;
import su.geocaching.android.controller.LogManager;

public class EnergySavingPreferenceActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    private static final String TAG = EnergySavingPreferenceActivity.class.getCanonicalName();

    /*
     * (non-Javadoc)
     * 
     * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogManager.d(TAG, "onCreate");
        addPreferencesFromResource(R.xml.energy_saving_preference);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Preference preference = (Preference) findPreference(getString(R.string.gps_update_frequency_key));
        preference.setOnPreferenceChangeListener(this);
    }

    /* (non-Javadoc)
     * @see android.preference.Preference.OnPreferenceChangeListener#onPreferenceChange(android.preference.Preference, java.lang.Object)
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        GpsUpdateFrequency frequency = GpsUpdateFrequency.valueOf((String) newValue);
        Controller.getInstance().getLocationManager(getApplicationContext()).updateFrequency(frequency);
        return true;
    }
}