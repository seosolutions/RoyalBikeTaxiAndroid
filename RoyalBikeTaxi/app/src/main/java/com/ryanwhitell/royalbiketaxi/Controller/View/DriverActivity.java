package com.ryanwhitell.royalbiketaxi.Controller.View;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;

import java.util.Map;

public class DriverActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    /******* VARIABLES *******/
    private final String DEBUG_LOG = "RBT Debug Log";

    // Google Api - Location, Map
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Marker mLocationMarker;

    // Firebase database
    private DatabaseReference mDatabaseRefAvailableDrivers;
    private DatabaseReference mDatabaseRefLocationRequest;
    private String mLocationRequestKey;

    // Driver info
    private String mName;
    private boolean mAvailable;
    private Location mDriverLocation;


    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        // Initialize driver info
        mAvailable = true;

        Intent intent = getIntent();
        mName = intent.getStringExtra("name");

        mDriverLocation = null;

        // Initialize Navigation
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("You are logged in as " + mName);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(DEBUG_LOG, "fab");
                requestDriverLocations();
            }
        });

        // Initialize database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mDatabaseRefAvailableDrivers = database.getReference("Available Drivers");

        // Initialize Google Api - Location, Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mDatabaseRefLocationRequest = database.getReference("Refresh Request");
        mDatabaseRefLocationRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                driverLocationRequestReceived();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // TODO: handle
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }


    /******* DRIVER LOGIC *******/
    public void requestDriverLocations() {
        Log.d(DEBUG_LOG, "I have requested all driver locations");
        mLocationRequestKey = mDatabaseRefLocationRequest.push().getKey();
        mDatabaseRefLocationRequest.child(mLocationRequestKey).setValue("REQUEST");
        mDatabaseRefLocationRequest.child(mLocationRequestKey).removeValue();
        updateMap();
    }

    public void driverLocationRequestReceived() {
        Log.d(DEBUG_LOG, "My Location was requested - connect");
        mGoogleApiClient.connect();
        if ((mDriverLocation != null) && mAvailable) {
            mDatabaseRefAvailableDrivers.child(mName).setValue(mDriverLocation);
        } else {
            mDatabaseRefAvailableDrivers.child(mName).setValue(null);
        }
    }

    public void updateMap(){
        mDatabaseRefAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                mMap.clear();

                for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                    if (driver.getKey() != mName) {
                        Double lat = Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString());
                        Double lon = Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString());

                        mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(lat, lon))
                                .title(driver.getKey().toString())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    }
                }

                mGoogleApiClient.connect();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: handle
            }
        });
    }


    /******* GOOGLE MAP *******/
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                marker.showInfoWindow();
                CameraPosition cp = new CameraPosition(marker.getPosition(), 14.9f, 0, 17.5f);
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cp), 500, null);
                Log.d(DEBUG_LOG,"Marker Click");
                return true;
            }
        });

        LatLng savannah = new LatLng(32.072219, -81.0933537);
        CameraPosition cp = new CameraPosition(savannah, 14.9f, 0, 17.5f);
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cp));
    }


    /******* LOCATION SERVICES *******/
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest locationRequest = LocationRequest.create();
        //TODO: look into HIGH ACCURACY vs BATTER and DATA saver
        // Consider, to safe driver data and battery, letting him choose the interval
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2500);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    @Override
    public void onConnectionSuspended(int i) {
        //TODO: Handle Suspended Connection
        Log.d(DEBUG_LOG,"onConnectionSuspended Fired");
    }

    @Override
    public void onLocationChanged(Location location) {

        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        mLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title("ME")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mDriverLocation = location;

        Log.d(DEBUG_LOG, "Location Set - Disconnected");

        // Only fire once on each connect
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
        Log.d(DEBUG_LOG,"onConnectionFailed Fired");
    }

}
