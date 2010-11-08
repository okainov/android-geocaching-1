package su.geocaching.android.view.geocachemap;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * @author Grigory Kalabin. grigory.kalabin@gmail.com
 * @since fall, 2010
 * @description Location manager which get updates of location by GPS or GSM/Wi-Fi
 */
public class SearchGeoCacheLocationManager implements LocationListener {

    private IActivityWithLocation context;
    private LocationManager locationManager;
    private Location lastLocation;
    private String provider;
    private boolean locationFixed;

    /**
     * @param context - Activity which use this sensor
     * @param locationManager - location manager of context
     */
    public SearchGeoCacheLocationManager(IActivityWithLocation context,
                                         LocationManager locationManager) {
        this.context = context;
        this.locationManager = locationManager;
        locationFixed = false;
        
    }

    /**
     * Update location obtained from LocationManager
     */
    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        locationFixed = true;
        updateLocation();
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO: implement onProviderDisabled
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO: implement onProviderEnabled
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO: implement onStatusChanged
    }

    /**
     * Remove updates, when need to pause work
     */
    public void pause() {
        locationManager.removeUpdates(this);
    }

    /**
     * Add updates after pause.
     */
    public void resume() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        provider = locationManager.getBestProvider(criteria, true);
        locationManager.requestLocationUpdates(provider, 1000, 1, this);
        lastLocation = this.locationManager.getLastKnownLocation(provider);
        if (lastLocation != null) {
            updateLocation();
        }
    }

    /**
     * @return last known location
     */
    public Location getCurrentLocation() {
        return lastLocation;
    }

    /**
     * Calling when we get new location and want tell it to context
     */
    private void updateLocation() {
        context.updateLocation(lastLocation);
    }
    
    public boolean isLocationFixed() {
    	return locationFixed;
    }
}
