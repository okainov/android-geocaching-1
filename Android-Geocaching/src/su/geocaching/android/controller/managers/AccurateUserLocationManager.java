package su.geocaching.android.controller.managers;

import android.location.*;
import android.os.Bundle;
import su.geocaching.android.controller.Controller;
import su.geocaching.android.controller.GpsUpdateFrequency;
import su.geocaching.android.controller.utils.CoordinateHelper;
import su.geocaching.android.ui.R;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Location manager which get updates of location by GPS or GSM/Wi-Fi
 *
 * @author Grigory Kalabin. grigory.kalabin@gmail.com
 * @since fall, 2010
 */
public class AccurateUserLocationManager extends AbstractUserLocationManager implements GpsStatus.Listener {
    /**
     * @see <a href="http://developer.android.com/reference/android/location/LocationProvider.html#OUT_OF_SERVICE">Android reference</a>
     */
    public static final int OUT_OF_SERVICE = 0;

    /**
     * @see <a href="http://developer.android.com/reference/android/location/LocationProvider.html#TEMPORARILY_UNAVAILABLE">Android reference</a>
     */
    public static final int TEMPORARILY_UNAVAILABLE = 1;

    /**
     * Event sent when the GPS system has started.
     *
     * @see <a href="http://developer.android.com/reference/android/location/GpsStatus.html#GPS_EVENT_STARTED">Android reference</a>
     */
    public static final int GPS_EVENT_STARTED = 3;

    /**
     * Event sent when the GPS system has stopped.
     *
     * @see <a href="http://developer.android.com/reference/android/location/GpsStatus.html#GPS_EVENT_STOPPED">Android reference</a>
     */
    public static final int GPS_EVENT_STOPPED = 4;

    /**
     * Event sent when the GPS system has received its first fix since starting.
     *
     * @see <a href="http://developer.android.com/reference/android/location/GpsStatus.html#GPS_EVENT_FIRST_FIX">Android reference</a>
     */
    public static final int GPS_EVENT_FIRST_FIX = 5;

    /**
     * Event sent periodically to report GPS satellite status.
     *
     * @see <a href="http://developer.android.com/reference/android/location/GpsStatus.html#GPS_EVENT_SATELLITE_STATUS">Android reference</a>
     */
    public static final int GPS_EVENT_SATELLITE_STATUS = 6;

    /**
     * Event sent when the provider has enabled.
     */
    public static final int EVENT_PROVIDER_ENABLED = 7;

    /**
     * Event sent when the provider has disabled.
     */
    public static final int EVENT_PROVIDER_DISABLED = 8;

    private static final String TAG = AccurateUserLocationManager.class.getCanonicalName();
    private static final String REMOVE_UPDATES_TIMER_NAME = "remove location updates timer";
    private static final String DEPRECATE_LOCATION_TIMER_NAME = "waiting for location deprecation";
    private static final long REMOVE_UPDATES_DELAY = 5000; // in milliseconds
    private static final int PRECISE_LOCATION_MAX_TIME = 30 * 1000; // in milliseconds
    private static final float PRECISE_LOCATION_MAX_ACCURACY = 40f; // in meters
    private static final float MAX_SPEED_OF_HARDWARE_COMPASS = 20 * 1000 / 3600; // (in m/s) if user speed lower than this - use hardware compass otherwise use GPS compass

    private long lastLocationTime = -1;
    private final Timer removeUpdatesTimer;
    private DeprecateLocationNotifier deprecateLocationNotifier;
    private final Timer deprecateLocationTimer;
    private RemoveUpdatesTask removeUpdatesTask;
    private boolean isUpdating;

    private GpsUpdateFrequency updateFrequency;

    /**
     * @param locationManager
     *         manager which can add or remove updates of location services
     */
    public AccurateUserLocationManager(LocationManager locationManager) {
        super(locationManager);
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        updateFrequency = Controller.getInstance().getPreferencesManager().getGpsUpdateFrequency();

        isUpdating = false;
        removeUpdatesTimer = new Timer(REMOVE_UPDATES_TIMER_NAME);
        removeUpdatesTask = new RemoveUpdatesTask(this);
        deprecateLocationTimer = new Timer(DEPRECATE_LOCATION_TIMER_NAME);
        deprecateLocationNotifier = new DeprecateLocationNotifier();
    }

    /**
     * @param subscriber
     *         activity which will be listen location updates
     */
    public void addSubscriber(ILocationAware subscriber) {
        removeUpdatesTask.cancel();

        LogManager.d(TAG, "addSubscriber: remove task cancelled;\n	isUpdating=" + Boolean.toString(isUpdating) + ";\n	subscribers=" + Integer.toString(subscribers.size()));
        synchronized (subscribers) {
            if (((subscribers.size() == 0) && !isUpdating)) {
                addUpdates();
            }
            if (!subscribers.contains(subscriber)) {
                subscribers.add(subscriber);
            }
        }
        LogManager.d(TAG, "	Count of subscribers became " + Integer.toString(subscribers.size()));
    }

    /**
     * @param subscriber
     *         activity which no need to listen location updates
     * @return true if activity was subscribed on location updates
     */
    public boolean removeSubscriber(ILocationAware subscriber) {
        boolean res;
        synchronized (subscribers) {
            res = subscribers.remove(subscriber);
            if (subscribers.size() == 0 && res) {
                removeUpdatesTask.cancel();
                removeUpdatesTask = new RemoveUpdatesTask(this);
                removeUpdatesTimer.schedule(removeUpdatesTask, REMOVE_UPDATES_DELAY);
                LogManager.d(TAG, "none subscribers. wait " + Long.toString(REMOVE_UPDATES_DELAY / 1000) + " s from " + Long.toString(System.currentTimeMillis()));
            }
        }
        LogManager.d(TAG, "remove subscriber. Count of subscribers became " + Integer.toString(subscribers.size()));
        return res;
    }

    public void checkSubscribers() {
        synchronized (subscribers) {
            if (subscribers.size() == 0) {
                removeUpdatesTask.cancel();
                removeUpdates();
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.location.LocationListener#onLocationChanged(android.location. Location)
     */
    @Override
    public void onLocationChanged(Location location) {
        super.onLocationChanged(location);
        Odometer.onLocationChanged(location);
        lastLocationTime = System.currentTimeMillis();
        // start timer which notify about deprecation
        deprecateLocationNotifier.cancel();
        deprecateLocationNotifier = new DeprecateLocationNotifier();
        deprecateLocationTimer.schedule(deprecateLocationNotifier, PRECISE_LOCATION_MAX_TIME);

        Controller.getInstance().getCompassManager().resetUpdates(location.getSpeed() > MAX_SPEED_OF_HARDWARE_COMPASS && location.hasBearing());
    }

    /**
     * Tell to subscribers about event using statuses
     *
     * @param provider
     *         which has been disabled
     */
    @Override
    public void onProviderDisabled(String provider) {
        onAggregatedStatusChanged(provider, EVENT_PROVIDER_DISABLED, null);
    }

    /**
     * Tell to subscribers about event using statuses
     *
     * @param provider
     *         which has been enabled
     */
    @Override
    public void onProviderEnabled(String provider) {
        onAggregatedStatusChanged(provider, EVENT_PROVIDER_ENABLED, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                onAggregatedStatusChanged(provider, OUT_OF_SERVICE, extras);
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                onAggregatedStatusChanged(provider, TEMPORARILY_UNAVAILABLE, extras);
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                onAggregatedStatusChanged(provider, GPS_EVENT_FIRST_FIX, null);
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                onAggregatedStatusChanged(provider, GPS_EVENT_SATELLITE_STATUS, null);
                break;
            case GpsStatus.GPS_EVENT_STARTED:
                onAggregatedStatusChanged(provider, GPS_EVENT_STARTED, null);
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                onAggregatedStatusChanged(provider, GPS_EVENT_STOPPED, null);
                break;
        }
    }

    /**
     * Aggregate status changing from {@link #onStatusChanged(String, int, android.os.Bundle)} and {@link #onGpsStatusChanged(int)}
     *
     * @param provider
     *         the name of the location provider associated with this update
     * @param status
     *         one of
     *         <ul><li>{@link #GPS_EVENT_FIRST_FIX}
     *         <li>{@link #GPS_EVENT_SATELLITE_STATUS}
     *         <li>{@link #GPS_EVENT_STARTED}
     *         <li>{@link #GPS_EVENT_STOPPED}
     *         <li>{@link #OUT_OF_SERVICE}
     *         <li>{@link #TEMPORARILY_UNAVAILABLE}</ul>
     *         <li>{@link #EVENT_PROVIDER_DISABLED}</ul>
     *         <li>{@link #EVENT_PROVIDER_ENABLED}</ul>
     * @param extras
     *         an optional Bundle which will contain provider specific status variables (from {@link android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)})
     */
    private void onAggregatedStatusChanged(String provider, int status, Bundle extras) {
        synchronized (subscribers) {
            for (ILocationAware subscriber : subscribers) {
                subscriber.onStatusChanged(provider, status, extras);
            }
        }
    }

    /**
     * Remove updates of location
     */
    protected synchronized void removeUpdates() {
        if (!isUpdating) {
            LogManager.w(TAG, "updates already removed");
        }
        LogManager.d(TAG, "remove location updates at " + Long.toString(System.currentTimeMillis()));
        locationManager.removeUpdates(this);
        locationManager.removeGpsStatusListener(this);
        provider = null;
        isUpdating = false;
    }

    /**
     * Add updates of location
     */
    protected synchronized void addUpdates() {
        provider = locationManager.getBestProvider(criteria, true);
        requestLocationUpdates();
        LogManager.d(TAG, "add updates. Provider is " + provider);
    }

    /**
     * Check is last known location actual or not
     *
     * @return true, if last known location actual
     */
    public boolean hasPreciseLocation() {
        return hasLocation() && lastLocationTime + PRECISE_LOCATION_MAX_TIME > System.currentTimeMillis()
                && lastLocation.hasAccuracy() && lastLocation.getAccuracy() < PRECISE_LOCATION_MAX_ACCURACY;
    }

    /**
     * @return true if best provider by accuracy locationAvailable
     */
    public boolean isBestProviderEnabled() {
        String bestProv = locationManager.getBestProvider(criteria, false);
        return locationManager.isProviderEnabled(bestProv);
    }

    /**
     * call request location updates on location manager with right min time and min distance
     */
    private void requestLocationUpdates() {
        long minTime;
        float minDistance;
        switch (updateFrequency) {
            case MINIMAL:
                minTime = 16000;
                minDistance = 16;
                break;
            case RARELY:
                minTime = 8000;
                minDistance = 8;
                break;
            case NORMAL:
                minTime = 4000;
                minDistance = 4;
                break;
            case OFTEN:
                minTime = 2000;
                minDistance = 2;
                break;
            case MAXIMAL:
                minTime = 1000;
                minDistance = 1;
                break;
            default:
                minTime = 4000;
                minDistance = 4;
                break;
        }
        LogManager.d(TAG, "update frequency: " + updateFrequency.toString());
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, minTime, minDistance, this);
            locationManager.addGpsStatusListener(this);
            isUpdating = true;
        } else {
            LogManager.w(TAG, "provider == null");
        }
    }

    /**
     * Refresh frequency of location updates and re-request location updates from current provider
     *
     * @param value
     *         frequency which need
     */
    public synchronized void updateFrequency(GpsUpdateFrequency value) {
        if (updateFrequency.equals(value)) {
            LogManager.d(TAG, "refresh frequency: already done");
            return;
        }
        updateFrequency = value;
        LogManager.d(TAG, "refresh frequency. new value is " + updateFrequency.toString());
        if (isUpdating) {
            removeUpdates();
            addUpdates();
            LogManager.d(TAG, "refresh frequency: re-request location updates from provider");
        }
    }

    /**
     * Set frequency from preferences of location updates and re-request location updates from current provider
     */
    public synchronized void updateFrequencyFromPreferences() {
        updateFrequency(Controller.getInstance().getPreferencesManager().getGpsUpdateFrequency());
    }

    /**
     * Return string like "satellites: 2/5" with info about satellites
     *
     * @return string with localized info about satellites
     */
    public String getSatellitesStatusString() {
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        int usedInFix = 0;
        int count = 0;
        if (gpsStatus.getSatellites() == null) {
            return "";
        }
        for (GpsSatellite satellite : gpsStatus.getSatellites()) {
            count++;
            if (satellite.usedInFix()) {
                usedInFix++;
            }
        }
        return String.format(Controller.getInstance().getResourceManager().getString(R.string.gps_status_satellite_status), usedInFix, count);
    }

    /**
     * task which remove updates from LocationManager
     *
     * @author Grigory Kalabin. grigory.kalabin@gmail.com
     */
    private class RemoveUpdatesTask extends TimerTask {
        private AccurateUserLocationManager parent;

        /**
         * @param parent
         *         listener which want remove updates
         */
        public RemoveUpdatesTask(AccurateUserLocationManager parent) {
            this.parent = parent;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.TimerTask#run()
         */
        public void run() {
            if (parent.isUpdating) {
                parent.removeUpdates();
            }
        }
    }

    /**
     * Task which notify about location deprecation
     *
     * @author Grigory Kalabin. grigory.kalabin@gmail.com
     */
    private class DeprecateLocationNotifier extends TimerTask {
        /*
         * (non-Javadoc)
         *
         * @see java.util.TimerTask#run()
         */
        public void run() {
            Controller.getInstance().getCallbackManager().postEmptyMessage(CallbackManager.WHAT_LOCATION_DEPRECATED);
        }
    }

    /**
     * Class calculate distance of user path
     *
     * @author Grigory Kalabin. grigory.kalabin@gmail.com
     */
    public static class Odometer {
        private static final String TAG = Odometer.class.getCanonicalName();

        private static boolean isEnabled = false;
        private static float distance = 0f;
        private static Location lastLocation = Controller.getInstance().getLocationManager().getLastKnownLocation();

        /**
         * @return true, if odometer enabled
         */
        public static boolean isEnabled() {
            return isEnabled;
        }

        /**
         * This method <b>not</b> reset accumulated distance value
         */
        public static void setEnabled(boolean enabled) {
            isEnabled = enabled;
        }

        /**
         * Reset accumulated distance of odometer
         */
        public static void refresh() {
            if (!isEnabled) {
                LogManager.w(TAG, "odometer is disabled");
            }
            distance = 0;
        }

        /**
         * @return accumulated distance of user path
         */
        public static float getDistance() {
            return distance;
        }

        /**
         * Increase distance of user path
         *
         * @param location
         *         new location of user
         */
        private static void onLocationChanged(Location location) {
            if (isEnabled && lastLocation != null) {
                float delta = CoordinateHelper.getDistanceBetween(location, lastLocation);
                if (delta > location.getAccuracy()) {
                    distance += delta;
                    lastLocation = location;
                }
            } else {
                lastLocation = location;
            }
        }
    }
}
