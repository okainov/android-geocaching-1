package su.geocaching.android.ui.preferences;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import su.geocaching.android.controller.Controller;
import su.geocaching.android.controller.GpsUpdateFrequency;
import su.geocaching.android.controller.managers.NavigationManager;
import su.geocaching.android.ui.R;

public class EnergySavingPreferenceActivity extends SherlockPreferenceActivity implements Preference.OnPreferenceChangeListener {
    private static final String ENERGY_SAVING_ACTIVITY_NAME = "/preferences/EnergySaving";

    /*
    * (non-Javadoc)
    *
    * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
    */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Controller.getInstance().getGoogleAnalyticsManager().trackActivityLaunch(ENERGY_SAVING_ACTIVITY_NAME);
        getSupportActionBar().setHomeButtonEnabled(true);
        addPreferencesFromResource(R.xml.energy_saving_preference);

        ListPreference preference = (ListPreference) findPreference(getString(R.string.gps_update_frequency_key));
        preference.setOnPreferenceChangeListener(this);
        preference.setSummary(preference.getEntry());
    }

    /* (non-Javadoc)
     * @see android.preference.Preference.OnPreferenceChangeListener#onPreferenceChange(android.preference.Preference, java.lang.Object)
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ListPreference listPreference = (ListPreference) preference;
        GpsUpdateFrequency frequency = GpsUpdateFrequency.valueOf((String) newValue);
        Controller.getInstance().getLocationManager().updateFrequency(frequency);
        // update summary
        preference.setSummary(listPreference.getEntries()[listPreference.findIndexOfValue((String) newValue)]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavigationManager.startDashboardActivity(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}