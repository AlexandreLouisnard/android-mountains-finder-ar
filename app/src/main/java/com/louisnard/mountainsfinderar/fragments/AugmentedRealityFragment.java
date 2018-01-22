package com.louisnard.mountainsfinderar.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.louisnard.mountainsfinderar.BuildConfig;
import com.louisnard.mountainsfinderar.R;
import com.louisnard.mountainsfinderar.debug.DevUtils;
import com.louisnard.mountainsfinderar.model.Utils;
import com.louisnard.mountainsfinderar.model.database.ARDbHelper;
import com.louisnard.mountainsfinderar.model.objects.Point;
import com.louisnard.mountainsfinderar.model.services.Compass;
import com.louisnard.mountainsfinderar.model.services.PointService;
import com.louisnard.mountainsfinderar.views.CompassView;
import com.louisnard.mountainsfinderar.views.PointsView;

import java.util.List;

/**
 * Fragment showing the points around the user location using augmented reality over a camera preview.<br>
 *
 * Requires Manifest.permission.CAMERA and Manifest.permission.ACCESS_FINE_LOCATION permissions.
 *
 * @author Alexandre Louisnard
 */
public class AugmentedRealityFragment extends CameraPreviewFragment implements LocationListener, Compass.CompassListener {

    // TODO: use this library to build "Mountain Peaks - France"
    // TODO: solve crash bug with camera lock opening
    // Tag
    private static final String TAG = AugmentedRealityFragment.class.getSimpleName();
    private static final String TAG_ALERT_DIALOG_ENABLE_GPS = AlertDialogFragment.TAG + "_ENABLE_GPS";

    // Permissions
    private boolean mHasPermissions;
    private String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION};

    // Request codes
    private static final int REQUEST_PERMISSIONS = (TAG.hashCode() & 0x0000ffff) - 1;
    private static final int REQUEST_ENABLE_GPS = (TAG.hashCode() & 0x0000ffff) - 2;

    // Constants
    // The minimum distance the user must have moved from its previous location to recalculate azimuths and distances, in meters
    private static final int MIN_DISTANCE_DIFFERENCE_BETWEEN_RECALCULATIONS = 10;
    // The minimum distance the user must have moved from its previous location to reload the points from the database, in meters
    private static final int MIN_DISTANCE_DIFFERENCE_BETWEEN_DATABASE_RELOADS = 500;
    // The maximum distance to search and display points around the user's location, in meters
    private static final int MAX_RADIUS_DISTANCE_TO_SEARCH_POINTS_AROUND = 10000;
    // The minimum time interval between GPS location updates, in milliseconds
    private static final long MIN_TIME_INTERVAL_BETWEEN_LOCATION_UPDATES = 5000;
    // The maximum age of a location update from the system to be considered as still valid (in order to avoid working with old positions), in milliseconds
    private static final long MAX_AGE_FOR_A_LOCATION = 3 * 60000;
    // The minimum difference with the last orientation values from Compass for the CompassListener to be notified, in degrees
    private static final float MIN_AZIMUTH_DIFFERENCE_BETWEEN_COMPASS_UPDATES = 1;
    private static final float MIN_VERTICAL_INCLINATION_DIFFERENCE_BETWEEN_COMPASS_UPDATES = 1;
    private static final float MIN_HORIZONTAL_INCLINATION_DIFFERENCE_BETWEEN_COMPASS_UPDATES = 1;

    // Location
    private LocationManager mLocationManager;
    private Location mLastGpsLocation;

    // Compass
    private Compass mCompass;

    // Points
    private Point mUserLocationPoint;
    private Location mUserLocationAtLastDbReading;
    private List<Point> mPoints;

    // Views
    private PointsView mPointsView;
    private CompassView mCompassView;
    private TextView mGpsStatusTextView;
    private TextView mVerticalInclinationTextView;
    private TextView mHorizontalInclinationTextView;

    // Check for regular GPS updates
    // Init
    private final Handler mCheckGpsHandler = new Handler();
    private final Runnable mCheckGpsRunnable = new Runnable() {
        @Override
        public void run() {
            updateGpsStatus();
            mCheckGpsHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHasPermissions = Utils.hasPermissions(getContext(), REQUIRED_PERMISSIONS);

        // Check permissions
        if (!mHasPermissions) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
        } else {
            // Compass
            mCompass = Compass.newInstance(getContext(), this);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_augmented_reality, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Views
        mPointsView = view.findViewById(R.id.points_view);
        mCompassView = view.findViewById(R.id.compass_view);
        mGpsStatusTextView = view.findViewById(R.id.gps_status_text_view);
        mVerticalInclinationTextView = view.findViewById(R.id.pitch_text_view);
        mHorizontalInclinationTextView = view.findViewById(R.id.roll_text_view);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mHasPermissions) {
            // GPS location listener
            mLocationManager = (LocationManager) getActivity().getSystemService(Activity.LOCATION_SERVICE);
            try {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_INTERVAL_BETWEEN_LOCATION_UPDATES, 5, this);
            } catch (SecurityException e) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Missing location permission");
                e.printStackTrace();
            }

            // Check GPS status
            updateGpsStatus();

            // Dump database for debug use only
            if (BuildConfig.DEBUG) {
                DevUtils.exportDatabaseToExternalStorage(getActivity(), ARDbHelper.getDbName());
            }

            // Start compass
            if (mCompass != null)
                mCompass.start(MIN_AZIMUTH_DIFFERENCE_BETWEEN_COMPASS_UPDATES, MIN_VERTICAL_INCLINATION_DIFFERENCE_BETWEEN_COMPASS_UPDATES, MIN_HORIZONTAL_INCLINATION_DIFFERENCE_BETWEEN_COMPASS_UPDATES);

            // Start GPS updated checks
            mCheckGpsHandler.postDelayed(mCheckGpsRunnable, 1000);
        }
    }


    @Override
    public void onPause() {
        if (mHasPermissions) {
            // Stop GPS updated checks and listener
            mCheckGpsHandler.removeCallbacks(mCheckGpsRunnable);
            mLocationManager.removeUpdates(this);
        }

        super.onPause();

        if (mHasPermissions) {
            // Stop compass
            if (mCompass != null) mCompass.stop();
        }
    }

    // CameraPreviewFragment implementation
    @Override
    protected int getTextureViewResIdForCameraPreview() {
        return R.id.texture_view;
    }

    @Override
    protected void onCameraPreviewReady(float[] cameraPreviewAnglesOfView) {
        // Set camera angles
        if (cameraPreviewAnglesOfView != null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Configuring PointsView with camera angles (horizontal x vertical): " + cameraPreviewAnglesOfView[0] + "° x " + cameraPreviewAnglesOfView[1] + "°");
            mPointsView.setCameraAngles(cameraPreviewAnglesOfView[0], cameraPreviewAnglesOfView[1]);
        }
    }

    // CompassListener interface
    @Override
    public void onOrientationChanged(float azimuth, float verticalInclination, float horizontalInclination) {
        mCompassView.updateAzimuth(azimuth);
        mVerticalInclinationTextView.setText(String.format(getString(R.string.orientation_pitch_degrees), verticalInclination));
        mHorizontalInclinationTextView.setText(String.format(getString(R.string.orientation_roll_degrees), horizontalInclination));
        mPointsView.updateOrientation(azimuth, verticalInclination, horizontalInclination);
    }

    // LocationListener interface
    @Override
    public void onLocationChanged(Location location) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "LocationListener.onLocationChanged(): " + location.toString());
            if (location.isFromMockProvider()) {
                Log.d(TAG, "Location received is from mock provider");
            }
        }

        // Check the location validity
        if (location.getTime() >= System.currentTimeMillis() - MAX_AGE_FOR_A_LOCATION) {
            mLastGpsLocation = location;

            // Load points around the user from the database
            if (mPoints == null || mUserLocationAtLastDbReading == null || mUserLocationAtLastDbReading.distanceTo(location) > MIN_DISTANCE_DIFFERENCE_BETWEEN_DATABASE_RELOADS) {
                mUserLocationAtLastDbReading = location;
                final ARDbHelper dbHelper = ARDbHelper.getInstance(getActivity().getApplicationContext());
                mPoints = dbHelper.getPointsAround(location, MAX_RADIUS_DISTANCE_TO_SEARCH_POINTS_AROUND);
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Found " + mPoints.size() + " points in the database around the new user location");
            }

            // Update user location and recalculate relative azimuths of points from the new user location
            if (mUserLocationPoint == null || mUserLocationPoint.distanceTo(location) > MIN_DISTANCE_DIFFERENCE_BETWEEN_RECALCULATIONS) {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Recalculating points azimuth from the new user location");
                mUserLocationPoint = new Point(getString(R.string.gps_your_location), location);
                // Update points view
                mPointsView.setPoints(mUserLocationPoint, PointService.sortPointsByRelativeAzimuth(mUserLocationPoint, mPoints));
            }
        }
        updateGpsStatus();
    }

    // LocationListener interface
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (BuildConfig.DEBUG) Log.d(TAG, "LocationListener.onStatusChanged()");
        updateGpsStatus();
    }

    // LocationListener interface
    @Override
    public void onProviderEnabled(String provider) {
        if (BuildConfig.DEBUG) Log.d(TAG, "LocationListener.onProviderEnabled()");
        updateGpsStatus();
    }

    // LocationListener interface
    @Override
    public void onProviderDisabled(String provider) {
        if (BuildConfig.DEBUG) Log.d(TAG, "LocationListener.onProviderDisabled()");
        updateGpsStatus();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_GPS && resultCode == Activity.RESULT_OK) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (Utils.hasPermissions(getContext(), REQUIRED_PERMISSIONS)) {
                getActivity().recreate();
            } else {
                getActivity().finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // Check GPS status and update UI accordingly
    // Should be called whenever GPS status has changed
    private void updateGpsStatus() {
        if (isAdded()) {
            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "GPS is disabled");
                mLastGpsLocation = null;
                mGpsStatusTextView.setText(getString(R.string.gps_disabled));
                mPointsView.setPoints(null, null);
                showEnableGpsAlertDialog();
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "GPS is enabled");
                dismissEnableGpsAlertDialog();
                if (mLastGpsLocation != null && mLastGpsLocation.getTime() >= System.currentTimeMillis() - MAX_AGE_FOR_A_LOCATION) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "GPS located");
                    mGpsStatusTextView.setText(String.format(getString(R.string.gps_updated_seconds_ago), (System.currentTimeMillis() - mLastGpsLocation.getTime()) / 1000));
                } else {
                    if (BuildConfig.DEBUG) Log.d(TAG, "GPS waiting for location");
                    mGpsStatusTextView.setText(getString(R.string.gps_waiting_for_location));
                    mPointsView.setPoints(null, null);
                }
            }
        }
    }

    // Display an alert dialog asking the user to enable the GPS
    private void showEnableGpsAlertDialog() {
        if (isAdded() && getFragmentManager().findFragmentByTag(TAG_ALERT_DIALOG_ENABLE_GPS) == null) {
            final AlertDialogFragment alertDialogFragment = AlertDialogFragment.newInstance(R.string.gps, R.string.gps_disabled_alert_message, android.R.string.ok, android.R.string.cancel);
            alertDialogFragment.setTargetFragment(this, REQUEST_ENABLE_GPS);
            final FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.add(alertDialogFragment, TAG_ALERT_DIALOG_ENABLE_GPS);
            fragmentTransaction.commitAllowingStateLoss();
        }
    }

    // Dismiss the alert dialog asking the user to enable the GPS
    private void dismissEnableGpsAlertDialog() {
        if (isAdded() && getFragmentManager().findFragmentByTag(TAG_ALERT_DIALOG_ENABLE_GPS) != null) {
            ((AlertDialogFragment) getFragmentManager().findFragmentByTag(TAG_ALERT_DIALOG_ENABLE_GPS)).dismissAllowingStateLoss();
        }
    }
}
