package su.geocaching.android.ui.searchgeocache;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.google.android.maps.*;
import su.geocaching.android.controller.*;
import su.geocaching.android.controller.compass.CompassPreferenceManager;
import su.geocaching.android.controller.compass.CompassSpeed;
import su.geocaching.android.controller.compass.SmoothCompassThread;
import su.geocaching.android.model.datatype.GeoCache;
import su.geocaching.android.model.datatype.GeoCacheStatus;
import su.geocaching.android.model.datatype.GeoCacheType;
import su.geocaching.android.ui.R;
import su.geocaching.android.ui.compass.SearchGeoCacheCompass;
import su.geocaching.android.ui.geocachemap.ConnectionManager;
import su.geocaching.android.ui.geocachemap.GeoCacheOverlayItem;
import su.geocaching.android.ui.geocachemap.IInternetAware;
import su.geocaching.android.ui.geocachemap.SearchCacheOverlay;
import su.geocaching.android.ui.searchgeocache.drivingDirections.DrivingDirections;
import su.geocaching.android.ui.searchgeocache.stepbystep.CheckpointDialog;
import su.geocaching.android.ui.searchgeocache.stepbystep.StepByStepTabActivity;
import su.geocaching.android.utils.GpsHelper;
import su.geocaching.android.utils.UiHelper;

import java.util.List;

/**
 * Search GeoCache with the map
 *
 * @author Android-Geocaching.su student project team
 * @since October 2010
 */
public class SearchGeoCacheMap extends MapActivity implements IInternetAware, ILocationAware, ICompassAware, IGpsStatusAware {
    private final static String TAG = SearchGeoCacheMap.class.getCanonicalName();

    private GeoCacheOverlayItem cacheOverlayItem;
    private SearchCacheOverlay searchCacheOverlay;
    private Drawable cacheMarker;
    private DistanceToGeoCacheOverlay distanceOverlay;
    private UserLocationOverlay userOverlay;
    private MapView map;
    private MapController mapController;
    private List<Overlay> mapOverlays;

    private TextView waitingLocationFixText;
    private ImageView progressBarView;
    private AnimationDrawable progressBarAnim;

    private ConnectionManager internetManager;
    private CompassManager mCompassManager;
    private GeoCacheLocationManager mLocationManager;
    private GpsStatusManager mGpsStatusManager;
    private Controller mController;
    private GoogleAnalyticsTracker tracker;
    private DrivingDirections drivingDirection;
    private SmoothCompassThread animationThread;

    /*
      * (non-Javadoc)
      *
      * @see com.google.android.maps.MapActivity#onCreate(android.os.Bundle)
      */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_geocache_map);

        tracker = GoogleAnalyticsTracker.getInstance();
        tracker.start(getString(R.string.id_Google_Analytics), this);
        tracker.trackPageView(getString(R.string.search_activity_folder));
        tracker.dispatch();

        waitingLocationFixText = (TextView) findViewById(R.id.waitingLocationFixText);
        progressBarView = (ImageView) findViewById(R.id.progressCircle);
        progressBarView.setBackgroundResource(R.anim.earth_anim);
        progressBarAnim = (AnimationDrawable) progressBarView.getBackground();

        map = (MapView) findViewById(R.id.searchGeocacheMap);
        mapOverlays = map.getOverlays();
        mapController = map.getController();
        userOverlay = new UserLocationOverlay(this);
        map.setBuiltInZoomControls(true);

        GeoCache geoCache = (GeoCache) getIntent().getParcelableExtra(GeoCache.class.getCanonicalName());

        mController = Controller.getInstance();
        mController.setSearchingGeoCache(geoCache);

        internetManager = mController.getConnectionManager();
        internetManager.addSubscriber(this);
        mLocationManager = mController.getLocationManager();
        mCompassManager = mController.getCompassManager();
        mGpsStatusManager = mController.getGpsStatusManager();

        if (geoCache != null) {
            cacheMarker = mController.getResourceManager().getMarker(mController.getSearchingGeoCache());
            searchCacheOverlay = new SearchCacheOverlay(cacheMarker, this, map);
            cacheOverlayItem = new GeoCacheOverlayItem(mController.getSearchingGeoCache(), "", "");
            searchCacheOverlay.addOverlayItem(cacheOverlayItem);
            mapOverlays.add(searchCacheOverlay);
        }
    }

    /*
      * (non-Javadoc)
      *
      * @see com.google.android.maps.MapActivity#onPause()
      */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "on pause");

        if (mLocationManager.hasLocation()) {
            stopAnimation();
        }

        internetManager.removeSubscriber(this);
        mLocationManager.removeSubsriber(this);
        mCompassManager.removeSubscriber(this);
        mGpsStatusManager.removeSubsriber(this);

        tracker.stop();
    }

    /*
      * (non-Javadoc)
      *
      * @see com.google.android.maps.MapActivity#onResume()
      */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "on pause");
        map.setKeepScreenOn(Controller.getInstance().getKeepScreenOnPreference(map.getContext()));

        if (!mLocationManager.isBestProviderEnabled()) {
            if (!mLocationManager.isBestProviderGps()) {
                Log.w(TAG, "resume: device without gps");
            }
            UiHelper.askTurnOnGps(this);
            Log.d(TAG, "resume: best provider (" + mLocationManager.getBestProvider() + ") disabled. Current provider is " + mLocationManager.getCurrentProvider());
        } else {
            Log.d(TAG, "resume: best provider (" + mLocationManager.getBestProvider() + ") enabled. Run logic");
            if (mController.getSearchingGeoCache() == null) {
                Log.e(TAG, "null geocache. Finishing.");
                Toast.makeText(this, getString(R.string.search_geocache_error_no_geocache), Toast.LENGTH_LONG).show();
                this.finish();
                return;
            }

            // Save last searched geocache
            mController.setLastSearchedGeoCache(mController.getSearchingGeoCache(), this);

            if (!mLocationManager.hasLocation()) {
                onBestProviderUnavailable();
                mapController.animateTo(mController.getSearchingGeoCache().getLocationGeoPoint());
                progressBarView.setVisibility(View.VISIBLE);
                Log.d(TAG, "runLogic: location not fixed. Send msg.");
            } else {
                updateLocation(mLocationManager.getLastKnownLocation());
                progressBarView.setVisibility(View.GONE);
                startAnimation();
                Log.d(TAG, "runLogic: location fixed. Update location with last known location");
            }
            mLocationManager.addSubscriber(this);
            mLocationManager.enableBestProviderUpdates();
            mCompassManager.addSubscriber(this);
            mGpsStatusManager.addSubscriber(this);

            map.invalidate();
        }

        if (!internetManager.isInternetConnected()) {
            onInternetLost();
            Log.w(TAG, "internet not connected");
        }
    }

    /**
     * Start SearchGeoCacheCompass activity
     */
    public void startCompassView() {
        Log.d(TAG, "start compass activity");

        Intent intent = new Intent(this, SearchGeoCacheCompass.class);
        if (mController.getSearchingGeoCache() != null) {
            intent.putExtra(GeoCache.class.getCanonicalName(), mController.getSearchingGeoCache());
        }
        startActivity(intent);
    }

    /*
      * (non-Javadoc)
      *
      * @see su.geocaching.android.controller.ILocationAware#updateLocation(android.location.Location)
      */
    @Override
    public void updateLocation(Location location) {
        userOverlay.setPoint(GpsHelper.locationToGeoPoint(location));
        userOverlay.setAccuracy(location.getAccuracy());
        Log.d(TAG, "update location");
        if (progressBarView.getVisibility() == View.VISIBLE) {
            progressBarView.setVisibility(View.GONE);
        }
        if (distanceOverlay == null) {
            // It's really first run of update location
            Log.d(TAG, "update location: first run of this activity");
            distanceOverlay = new DistanceToGeoCacheOverlay(GpsHelper.locationToGeoPoint(location), mController.getSearchingGeoCache().getLocationGeoPoint());
            distanceOverlay.setCachePoint(mController.getSearchingGeoCache().getLocationGeoPoint());
            mapOverlays.add(distanceOverlay);
            mapOverlays.add(userOverlay);
            resetZoom();
            drivingDirection = new DrivingDirections(GpsHelper.locationToGeoPoint(location), mController.getSearchingGeoCache().getLocationGeoPoint());

            startAnimation();

            return;
        }
        distanceOverlay.setUserPoint(GpsHelper.locationToGeoPoint(location));

        map.invalidate();
    }

    @Override
    // id misused
    protected Dialog onCreateDialog(int index) {
        return new CheckpointDialog(this, index, checkpointCacheOverlay, map);
    }


    /* (non-Javadoc)
      * @see su.geocaching.android.controller.ICompassAware#updateBearing(float)
      */
    @Override
    public void updateBearing(float bearing) {
        float[] values = new float[1];
        values[0] = bearing;
        // Log.d(TAG, "update bearing. New bearing=" +
        // Integer.toString(bearing));
        userOverlay.setDirection(bearing);
    }

    /**
     * Set map zoom which can show userPoint and GeoCachePoint
     */
    private void resetZoom() {
        GeoPoint currentGeoPoint = GpsHelper.locationToGeoPoint(mLocationManager.getLastKnownLocation());
        // Calculating max and min lat and lon
        int minLat = Math.min(mController.getSearchingGeoCache().getLocationGeoPoint().getLatitudeE6(), currentGeoPoint.getLatitudeE6());
        int maxLat = Math.max(mController.getSearchingGeoCache().getLocationGeoPoint().getLatitudeE6(), currentGeoPoint.getLatitudeE6());
        int minLon = Math.min(mController.getSearchingGeoCache().getLocationGeoPoint().getLongitudeE6(), currentGeoPoint.getLongitudeE6());
        int maxLon = Math.max(mController.getSearchingGeoCache().getLocationGeoPoint().getLongitudeE6(), currentGeoPoint.getLongitudeE6());

        // Calculate span
        int latSpan = maxLat - minLat;
        int lonSpan = maxLon - minLon;

        // Set zoom
        mapController.zoomToSpan(latSpan, lonSpan);

        // Calculate new center of map
        GeoPoint center = new GeoPoint((mController.getSearchingGeoCache().getLocationGeoPoint().getLatitudeE6() + currentGeoPoint.getLatitudeE6()) / 2, (mController.getSearchingGeoCache()
                .getLocationGeoPoint().getLongitudeE6() + currentGeoPoint.getLongitudeE6()) / 2);

        // Set new center of map
        mapController.setCenter(center);
        map.invalidate();
        Projection proj = map.getProjection();
        // calculate padding
        int userPadding = (int) proj.metersToEquatorPixels(mLocationManager.getLastKnownLocation().getAccuracy());
        Rect cacheBounds = cacheMarker.getBounds();
        // Get points of user and cache on screen
        Point userPoint = new Point();
        Point cachePoint = new Point();
        proj.toPixels(currentGeoPoint, userPoint);
        proj.toPixels(mController.getSearchingGeoCache().getLocationGeoPoint(), cachePoint);
        // Get map boundaries
        int mapRight = map.getRight();
        int mapBottom = map.getBottom();
        int mapLeft = map.getLeft();
        int mapTop = map.getTop();
        // Check contains markers in visible part of map
        boolean isCacheMarkerNotInMapX = (cachePoint.x + cacheBounds.left < mapLeft) || (cachePoint.x + cacheBounds.right > mapRight);
        boolean isCacheMarkerNotInMapY = (cachePoint.y + cacheBounds.top < mapTop) || (cachePoint.y + cacheBounds.bottom > mapBottom);
        boolean isUserMarkerNotInMapX = (userPoint.x - userPadding < mapLeft) || (userPoint.x + userPadding > mapRight);
        boolean isUserMarkerNotInMapY = (userPoint.y - userPadding < mapTop) || (userPoint.y + userPadding > mapBottom);
        boolean isMapDimensionsZeroes = mapRight == 0 && mapLeft == 0 && mapTop == 0 && mapBottom == 0;
        // if markers are not visible then zoomOut
        if ((isCacheMarkerNotInMapX || isCacheMarkerNotInMapY || isUserMarkerNotInMapX || isUserMarkerNotInMapY) && (!isMapDimensionsZeroes)) {
            Log.d(TAG, "markers not in the visible part of map. Zoom out.");
            mapController.zoomOut();
        }
    }

    /**
     * Creating menu object
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search_geocache_map, menu);
        return true;
    }

    /**
     * Called when menu element selected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuDefaultZoom:
                if (mLocationManager.hasLocation()) {
                    resetZoom();
                } else {
                    mapController.animateTo(mController.getSearchingGeoCache().getLocationGeoPoint());
                    Toast.makeText(getBaseContext(), R.string.status_null_last_location, Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.menuStartCompass:
                this.startCompassView();
                return true;
            case R.id.menuGeoCacheInfo:
                UiHelper.showGeoCacheInfo(this, mController.getSearchingGeoCache());
                return true;
            case R.id.addDrawDirectionPath:
                if (!drivingDirection.drawWay(map)) {
                    Toast.makeText(getBaseContext(), "���������� ��������� ����.", Toast.LENGTH_SHORT).show();
                }
            case R.id.DrawDirectionPathApp:
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse("http://maps.google.com/maps?saddr=" + GpsHelper.locationToGeoPoint(mLocationManager.getLastKnownLocation()).getLatitudeE6() / 1.0E6 + "," + GpsHelper.locationToGeoPoint(mLocationManager.getLastKnownLocation()).getLongitudeE6() / 1.0E6 + "&daddr=" + mController.getSearchingGeoCache().getLocationGeoPoint().getLatitudeE6() / 1.0E6 + "," + mController.getSearchingGeoCache().getLocationGeoPoint().getLongitudeE6() / 1.0E6 + "&ie=UTF8&om=0&output=kml"));
                startActivity(intent);
                return true;
            case R.id.stepByStep:
                UiHelper.startStepByStepForResult(this, mController.getSearchingGeoCache());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private SearchCacheOverlay checkpointCacheOverlay;

    // TODO
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case UiHelper.STEP_BY_STEP_REQUEST:
                if (resultCode == RESULT_OK && data != null) {
                    int latitude = data.getIntExtra(StepByStepTabActivity.LATITUDE, 0);
                    int longitude = data.getIntExtra(StepByStepTabActivity.LONGITUDE, 0);
                    GeoCache gc = new GeoCache();
                    GeoCache currentGCache = mController.getSearchingGeoCache();
                    gc.setLocationGeoPoint(new GeoPoint(latitude, longitude));
                    gc.setType(GeoCacheType.CHECKPOINT);

                    if (checkpointCacheOverlay == null) {
                        cacheMarker = Controller.getInstance().getResourceManager().getMarker(gc);
                        checkpointCacheOverlay = new SearchCacheOverlay(cacheMarker, this, map);
                        mapOverlays.add(checkpointCacheOverlay);
                    }

                    GeoCacheOverlayItem checkpoint = new GeoCacheOverlayItem(gc, "", "");
                    checkpointCacheOverlay.addOverlayItem(checkpoint);

                    gc.setId(currentGCache.getId());
                    gc.setStatus(GeoCacheStatus.ACTIVE_CHECKPOINT);
                    gc.setName("Checkpoint " + searchCacheOverlay.size());
                    mController.setSearchingGeoCache(gc);

                    map.invalidate();
                }
                break;
        }
    }

    /**
     * Show message string to user
     *
     * @param status string with information about device status
     * @param type   type of message(GPS, Internet, etc)
     */
    public void updateStatus(String status, StatusType type) {
        if (type == StatusType.GPS) {
            waitingLocationFixText.setText(status);
        }
    }

    /*
      * (non-Javadoc)
      *
      * @see com.google.android.maps.MapActivity#isRouteDisplayed()
      */
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    /*
      * (non-Javadoc)
      *
      * @see su.geocaching.android.ui.geocachemap.IInternetAware#onInternetLost()
      */
    @Override
    public void onInternetLost() {
        Toast.makeText(this, getString(R.string.search_geocache_internet_lost), Toast.LENGTH_LONG).show();
    }

    /*
      * (non-Javadoc)
      *
      * @see su.geocaching.android.ui.geocachemap.IInternetAware#onInternetFound()
      */
    @Override
    public void onInternetFound() {
        // TODO: do smthng?
    }

    public void onBestProviderUnavailable() {
        if (progressBarView.getVisibility() == View.GONE) {
            progressBarView.setVisibility(View.VISIBLE);
        }
        updateStatus(getString(R.string.waiting_location_fix_message), StatusType.GPS);
        Toast.makeText(this, getString(R.string.search_geocache_best_provider_lost), Toast.LENGTH_LONG).show();
    }

    /*
      * (non-Javadoc)
      *
      * @see android.app.Activity#onWindowFocusChanged(boolean)
      */
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        progressBarAnim.start();
    }

    public void onHomeClick(View v) {
        UiHelper.goHome(this);
    }

    /*
      * (non-Javadoc)
      *
      * @see su.geocaching.android.controller.IGpsStatusAware#updateStatus(java.lang.String)
      */
    @Override
    public void updateStatus(String status) {
        updateStatus(status, StatusType.GPS);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO: send code of event to activity
        Log.d(TAG, "onStatusChanged:");
        String statusString = "Location fixed: " + Boolean.toString(mLocationManager.hasLocation()) + ". Provider: " + provider + ". ";
        Log.d(TAG, "     " + statusString);
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                statusString += "Status: out of service. ";
                onBestProviderUnavailable();
                Log.d(TAG, "     Status: out of service.");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                statusString += "Status: temporarily unavailable. ";
                onBestProviderUnavailable();
                Log.d(TAG, "     Status: temporarily unavailable.");
                break;
            case LocationProvider.AVAILABLE:
                statusString += "Status: available. ";
                Log.d(TAG, "     Status: available.");
                break;
        }
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            statusString += "Satellites: " + Integer.toString(extras.getInt("satellites"));
            Log.d(TAG, "     Satellites: " + Integer.toString(extras.getInt("satellites")));
        }
    }

    /*
      * (non-Javadoc)
      *
      * @see su.geocaching.android.controller.ILocationAware#onProviderEnabled(java.lang.String)
      */
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "onProviderEnabled: do nothing");
    }

    /*
      * (non-Javadoc)
      *
      * @see su.geocaching.android.controller.ILocationAware#onProviderDisabled(java.lang.String)
      */
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "onProviderDisabled");
        if (!mLocationManager.isBestProviderEnabled()) {
            Log.d(TAG, "onStatusChanged: best provider (" + mLocationManager.getBestProvider() + ") disabled. Ask turn on.");
            onBestProviderUnavailable();
            UiHelper.askTurnOnGps(this);
        }
    }

    /**
     * run animation for user location overlay
     */
    private void startAnimation() {
        if (animationThread == null) {
            animationThread = new SmoothCompassThread(userOverlay, this);
            animationThread.setRunning(true);

            CompassPreferenceManager preferManager = CompassPreferenceManager.getPreference(this);
            String speed = preferManager.getString(CompassPreferenceManager.PREFS_COMPASS_SPEED_KEY, CompassSpeed.NORMAL.name());
            animationThread.setSpeed(CompassSpeed.valueOf(speed));

            animationThread.start();
        }
    }

    /**
     * Stop animation for user location overlay
     */
    private void stopAnimation() {
        if (animationThread != null) {
            animationThread.setRunning(false);
            try {
                animationThread.join(150); // TODO Is it need?
            } catch (InterruptedException e) {
            }
            animationThread = null;
        }
    }
}