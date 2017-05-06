package com.group02.guard;
/**
 * @author Gabriel Bulai
 * This class implements the google API and uses it to retreive current location of the phone
 * @version 2.0.0 GB
 */

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final int ERROR_DIALOG_REQUEST = 9001;
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 101;
    private static final int MY_PERMISSIONS_REQUEST_COARSE_LOCATION = 102;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final String BROADCAST_ACTION = "android.location.PROVIDERS_CHANGED";
    public static Double currentLatitude = 0.0;
    public static Double currentLongitude = 0.0;
    public static Handler handler;
    ClientSendThread clientSendThread;
    ClientReceiveThread clientReceiveThread;
    //String address = "192.168.1.101";
    String address = "129.16.155.11";
    int port = 8000;
    Marker phoneMarker = null;
    Marker carMarker = null;
    private GoogleApiClient mGoogleApiClient;
    private GoogleMap mMap;
    private FusedLocationProviderApi locationProvider = LocationServices.FusedLocationApi;
    private LocationRequest locationRequest;
    private boolean permissionIsGranted = false;
    /* Broadcast receiver to check status of GPS */
    private BroadcastReceiver gpsLocationReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            //If Action is Location
            if (intent.getAction().matches(BROADCAST_ACTION)) {
                LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                //Check if GPS is turned ON or OFF
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Toast.makeText(getApplicationContext(), "GPS is Enabled in your device", Toast.LENGTH_SHORT).show();
                    Log.e("About GPS", "GPS is Enabled in your device");
                } else {
                    //If GPS turned OFF show Location Dialog
                    //new Handler().postDelayed(new MyRunnable(), 10);
                    // showSettingDialog();
                    Toast.makeText(getApplicationContext(), "GPS is Disabled in your device", Toast.LENGTH_SHORT).show();
                    Log.e("About GPS", "GPS is Disabled in your device");
                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (servicesOK()) {
            setContentView(R.layout.activity_maps);
            if (android.os.Build.VERSION.SDK_INT > 9) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                StrictMode.setThreadPolicy(policy);
            }
            //initMap();
            if (initMap()) {
                initGoogleAPIClient();
            } else {
                Toast.makeText(this, "Map Not Connected!", Toast.LENGTH_SHORT).show();
            }
        } else {
            setContentView(R.layout.activity_main);
        }
    }

    private void initGoogleAPIClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        Toast.makeText(this, "Map Connected", Toast.LENGTH_SHORT).show();
        showSettingDialog();
    }

    public boolean servicesOK() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode == ConnectionResult.SUCCESS) {
            return true; //the user can make a mapping request
        } else if (apiAvailability.isUserResolvableError(resultCode)) {
            apiAvailability.getErrorDialog(this, resultCode, ERROR_DIALOG_REQUEST)
                    .show();
        } else {
            Toast.makeText(this, "Could not connect to mapping service", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    public boolean initMap() {
        if (mMap == null) {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
//                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setZoomControlsEnabled(true);
                    mMap.getUiSettings().setAllGesturesEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                    mMap.getUiSettings().setCompassEnabled(true);
                    MapStyleOptions retroMap = MapStyleOptions.loadRawResourceStyle(getApplicationContext(), R.raw.maps_retro);
                    MapStyleOptions nightMap = MapStyleOptions.loadRawResourceStyle(getApplicationContext(), R.raw.maps_night);
                    try {
                        // Customise the styling of the base map using a JSON object defined
                        // in a raw resource file.
                        mMap.setMapStyle(retroMap);

                    } catch (Resources.NotFoundException e) {
                    }
                }
            });
        }
        return true;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
    }

    private void gotoLocation(double lat, double lng, int zoom) {
        if (phoneMarker != null) {
            phoneMarker.remove();
        }
        LatLng phoneLatLng = new LatLng(lat, lng);
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(phoneLatLng, zoom);
        MarkerOptions phoneMarkerOptions = new MarkerOptions()
                .position(phoneLatLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                .title("Current Location");
        phoneMarker = mMap.addMarker(phoneMarkerOptions);
        phoneMarker.showInfoWindow();
        mMap.animateCamera(update);
        if (serverOK()) {
            LatLng carLatLng = new LatLng(Double.parseDouble(getCarCoords()[0]), Double.parseDouble(getCarCoords()[1]));
            if (carMarker != null) {
                carMarker.remove();
            }
            MarkerOptions carMarkerOptions = new MarkerOptions()
                    .position(carLatLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    .title("SmartCar");
            carMarker = mMap.addMarker(carMarkerOptions);
            carMarker.showInfoWindow();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_FINE_LOCATION);
            } else {
                //ensures we can run the app below api 23
                showSettingDialog();
//                permissionIsGranted = true;
            }
            return;
        }
        locationProvider.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }

    private void showSettingDialog() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(5 * 1000);
        locationRequest.setFastestInterval(1 * 1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // locationRequest.getSmallestDisplacement(10);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        //show dialog always when GPS is off
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        Toast.makeText(getApplicationContext(), "GPS is Enabled in your device", Toast.LENGTH_LONG).show();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MapsActivity.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case RESULT_OK:
                        Log.e("Settings", "Result OK");
                        Toast.makeText(getApplicationContext(), "GPS is Enabled in your device", Toast.LENGTH_LONG).show();
                        //startLocationUpdates();
                        break;
                    case RESULT_CANCELED:
                        Log.e("Settings", "Result Cancel");
                        Toast.makeText(getApplicationContext(), "GPS is Disabled in your device", Toast.LENGTH_LONG).show();
                        break;
                }
                break;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        currentLatitude = location.getLatitude();
        currentLongitude = location.getLongitude();
        gotoLocation(currentLatitude, currentLongitude, 15);
        if (location.hasAccuracy()) {
            clientSendThread.txMsg(currentLatitude.toString() + " " + currentLongitude.toString());
        } else {
            clientSendThread.txMsg("1");
        }
    }

    public boolean serverOK() {
        if (clientReceiveThread.otherLine != null) {
            return true;
        }
        return false;
    }

    public String[] getCarCoords() {
        String[] splitted = new String[2];
        if (serverOK()) {
            String abc = clientReceiveThread.otherLine;
            splitted = abc.split(" ");
        }
        return splitted;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String message = (String) msg.obj;
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();

            }
        };
        clientSendThread = new ClientSendThread(
                address,
                port);
        clientSendThread.start();
        clientReceiveThread = new ClientReceiveThread(
                address,
                port);
        clientReceiveThread.start();
//        Toast.makeText(getApplicationContext(), "Threads have been started", Toast.LENGTH_LONG).show();
//        if (!serverOK()) {
//            Toast.makeText(getApplicationContext(), "Connection with the server lost", Toast.LENGTH_SHORT).show();
        //}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionIsGranted) {
            if (mGoogleApiClient.isConnected()) {
                requestLocationUpdates();
            }
        }
        registerReceiver(gpsLocationReceiver, new IntentFilter(BROADCAST_ACTION));//Register broadcast receiver to check the status of GPS

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (permissionIsGranted) {
            locationProvider.removeLocationUpdates(mGoogleApiClient, this);
        }
    }
    //Run on UI
//    private Runnable sendUpdatesToUI = new Runnable() {
//        public void run() {
//            showSettingDialog();
//        }
//    };

    @Override
    protected void onStop() {
        super.onStop();
        if (permissionIsGranted) {
            mGoogleApiClient.disconnect();
            //Unregister receiver on destroy
        } else if (gpsLocationReceiver != null)
            unregisterReceiver(gpsLocationReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //permission handler according to which type of permission we want to check
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permission granted
                    permissionIsGranted = true;
                } else {
                    //permission denied
                    permissionIsGranted = false;
                    Toast.makeText(getApplicationContext(), "This app requires permission to be granted", Toast.LENGTH_SHORT).show();
                    //code to exit the map view
//                    startActivity(new Intent(MapsActivity.this, MainActivity.class));
                }
                break;
            case MY_PERMISSIONS_REQUEST_COARSE_LOCATION:
                // code for coarse location
                break;
        }
    }
}
