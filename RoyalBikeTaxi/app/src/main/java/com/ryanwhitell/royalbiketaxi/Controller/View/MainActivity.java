package com.ryanwhitell.royalbiketaxi.Controller.View;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

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
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.Controller.Model.DriverLocation;
import com.ryanwhitell.royalbiketaxi.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.os.Handler;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


    /******* VARIABLES *******/
    private final String DEBUG_LOG = "RBT Debug Log";

    // Alerts
    private AlertDialog.Builder mConfirmDispatchAlert;
    private AlertDialog.Builder mOutOfBoundsAlert;
    private ProgressBar mActivityWheel;

    // Firebase
    private DatabaseReference mFirebaseUserDispatchRequest;
    private DatabaseReference mFirebaseAvailableDrivers;
    private DatabaseReference mFirebaseLocationRequest;
    private String mDispatchRequestKey;

    // Flow Control
    private boolean mBoundsDisplayed;
    private enum State {
        IDLE, REQUESTING, SEARCHING, CONNECTED, WAITING
    }
    private State mDispatchState;
    private int mDriverClickCounter;

    // Google Api - Location, Map
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Polygon mBounds;
    private Marker mLocationMarker;
    private Marker mDriverLocationMarker;
    private Location mLastKnownLocation;
    public ArrayList<DriverLocation> mDriverLocations;

    // Navigation
    private Button mCancelDispatch;
    private FloatingActionButton mFab;

    // Runnable and Handler
    private int mNumberOfDrivers;
    private int mIndex;
    private Handler mHandler;


    /******* ACTIVITY LIFECYCLE *******/
    //TODO: Keep from sleeping, changing state in any way
    //TODO: REMOVE EVENT LISTENERS ON ACTIVITY CHANGE removeEventListener()
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /******* Initialize Alerts *******/
        mConfirmDispatchAlert = new AlertDialog.Builder(this)
                .setTitle("Confirm Dispatch to My Location")
                .setMessage(
                        "By clicking CONFIRM you agree " +
                                "to accepting a ride from our nearest " +
                                "driver. A bike will be dispatched to your " +
                                "location, please wait in a convenient pickup " +
                                "location and do not navigate away " +
                                "from the current screen. Thank you!")
                .setPositiveButton("CONFIRM", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmDispatch(which);
                    }
                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmDispatch(which);
                    }
                });

        mOutOfBoundsAlert = new AlertDialog.Builder(this)
                .setTitle("Out of Bounds Alert")
                .setMessage(
                        "You are currently trying to request " +
                                "a dispatch outside of our " +
                                "operating boundaries. To view boundaries, click " +
                                "TOGGLE BOUNDS")
                .setPositiveButton("TOGGLE BOUND", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleBoundaries(true);
                    }
                })
                .setNegativeButton("BACK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleBoundaries(false);
                    }
                });


        /******* Initialize Firebase *******/
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = database.getReference("Available Drivers");

        mFirebaseLocationRequest = database.getReference("Location Request");

        mFirebaseUserDispatchRequest = database.getReference("Dispatch Request");
        mFirebaseUserDispatchRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                driverHandshake(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: Handle
            }
        });


        /******* Initialize Flow Control *******/
        mBoundsDisplayed = false;
        mDriverClickCounter = 1;
        mDispatchState = State.IDLE;


        /******* Initialize Google Api - Location, Map *******/
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mDriverLocations = new ArrayList<>();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();


        /******* Initialize Navigation *******/
        mCancelDispatch = (Button) findViewById(R.id.cancel__dispatch_button);
        assert mCancelDispatch != null;
        mCancelDispatch.setVisibility(View.GONE);

        mActivityWheel = (ProgressBar) findViewById(R.id.progress_bar);
        assert mActivityWheel != null;
        mActivityWheel.setVisibility(View.GONE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        assert mFab != null;
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickFab(view);
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        assert navigationView != null;
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        destroyDispatchRequest();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
        destroyDispatchRequest();
    }


    /******* NAVIGATION *******/
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_toggle_bounds) {
            toggleBoundaries(true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.nav_book) {
            Log.d(DEBUG_LOG, "nav_book");
        } else if (id == R.id.nav_info) {
            Log.d(DEBUG_LOG, "nav_info");
        } else if (id == R.id.nav_help) {
            Log.d(DEBUG_LOG, "nav_help");
        } else if (id == R.id.nav_call) {
            Log.d(DEBUG_LOG, "nav_call");
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void toggleBoundaries(Boolean toggle) {
        if (toggle) {
            if (!mBoundsDisplayed) {
                mBounds = mMap.addPolygon(new PolygonOptions()
                        .add(new LatLng(32.082932, -81.096341),
                                new LatLng(32.079433, -81.083713),
                                new LatLng(32.062920, -81.089982),
                                new LatLng(32.066280, -81.102650))
                        .strokeColor(Color.BLUE));
                mBoundsDisplayed = true;
            } else {
                mBounds.remove();
                mBoundsDisplayed = false;
            }
        }
    }



    /******* USER DISPATCH LOGIC *******/
    // Request a dispatch
    public void onClickFab(View view){
        if (withinBounds()) {
            mConfirmDispatchAlert.create();
            mConfirmDispatchAlert.show();
        } else {
            mOutOfBoundsAlert.create();
            mOutOfBoundsAlert.show();
        }
    }

    public void confirmDispatch(int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {

            // 1. Hide fab and show dispatch request state views, prevent device sleep
            Log.d(DEBUG_LOG, "1. Hide fab and show dispatch request state views, prevent device sleep");
            mCancelDispatch.setVisibility(View.VISIBLE);
            mActivityWheel.setVisibility(View.VISIBLE);
            mFab.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Toast.makeText(this, "Requesting a driver...", Toast.LENGTH_LONG).show();

            // 2. Refresh all driver locations
            Log.d(DEBUG_LOG, "2. Refresh all driver locations");
            String key = mFirebaseLocationRequest.push().getKey();
            mFirebaseLocationRequest.child(key).setValue("REQUEST");
            mFirebaseLocationRequest.child(key).removeValue();

            // 3. Update Driver Locations
            Log.d(DEBUG_LOG, "3. Update Driver Locations");
            updateDriverLocations();

            // 4. Change dispatch state to "currently waiting for driver update"
            Log.d(DEBUG_LOG, "4. Change dispatch state to \"currently waiting for driver update\"");
            mDispatchState = State.WAITING;
        }
    }

    public void updateDriverLocations(){

        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                mDriverLocations.clear();

                // 5. Update driver locations
                Log.d(DEBUG_LOG, "5. Update driver locations");
                for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {
                    Double lat = Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString());
                    Double lon = Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString());

                    DriverLocation location = new DriverLocation(new LatLng(lat, lon), driver.getKey());
                    location.setDistance(new LatLng(mLocationMarker.getPosition().latitude, mLocationMarker.getPosition().longitude));

                    mDriverLocations.add(location);
                }


                // 6. Push dispatch request onto the database
                Log.d(DEBUG_LOG, "6. Push dispatch request onto the database");
                mDispatchRequestKey = mFirebaseUserDispatchRequest.push().getKey();
                mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("longitude").setValue(mLastKnownLocation.getLongitude());
                mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("latitude").setValue(mLastKnownLocation.getLatitude());

                // 7. Set state to "Requesting a dispatch"
                Log.d(DEBUG_LOG, "7. Set state to \"Requesting a dispatch\"");
                mDispatchState = State.REQUESTING;

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: handle
            }
        });
    }

    public void searchForDriver() {

        if (mDispatchState == State.SEARCHING) {

            mNumberOfDrivers = mDriverLocations.size();
            mIndex = 0;
            mHandler = new Handler();

            Runnable waitForResponse = new Runnable() {
                @Override
                public void run() {
                    if (mIndex < mNumberOfDrivers-1) {
                        // 12 B. Check to see if the driver has responded, else try the next on the list
                        Log.d(DEBUG_LOG, "12 B. Check to see if the driver has responded, else try the next on the list");

                        if (mDispatchState == State.CONNECTED) {
                            // 13. Connected to driver, quit runnable, track driver
                            Log.d(DEBUG_LOG, "13. Connected to driver, quit runnable");
                            trackDriver();
                        } else {
                            // 12 C1. Driver has not responded, request next closest driver
                            Log.d(DEBUG_LOG, "12 C1. Driver has not responded, request next closest driver");
                            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
                            mIndex++;
                            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").setValue(mDispatchRequestKey);
                            mHandler.postDelayed(this, 10000);
                        }

                    } else {
                        // 12 C2. No drivers are currently available
                        Log.d(DEBUG_LOG, "12 C2. No drivers are currently available");
                        mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
                    }
                }
            };

            // 11. Request nearest driver and provide 10 seconds for response
            if (mDriverLocations.get(mIndex) != null) {
                Log.d(DEBUG_LOG, "11. Request nearest driver and provide 10 seconds for response");
                mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").setValue(mDispatchRequestKey);
                mHandler.postDelayed(waitForResponse, 10000);
            } else {
                // TODO: Handle errors
            }

        }
    }

    public void driverHandshake(DataSnapshot dataSnapshot){
        if (mDispatchState == State.REQUESTING) {
            if (dataSnapshot.getValue() != null) {
                Map<String, Object> dispatchRequests = (Map<String, Object>) dataSnapshot.getValue();
                for (Map.Entry<String, Object> request : dispatchRequests.entrySet()) {
                    if (request.getKey().equals(mDispatchRequestKey)) {
                        // 8. Sort available drivers by closest
                        Log.d(DEBUG_LOG, "8. Sort available drivers by closest");
                        Collections.sort(mDriverLocations);

                        // 9. Change dispatch state to "Searching and contacting nearest driver"
                        Log.d(DEBUG_LOG, "9. Change dispatch state to \"Searching and contacting nearest driver\"");
                        mDispatchState = State.SEARCHING;

                        // 10. Find and contact nearest driver
                        Log.d(DEBUG_LOG, "10. Find and contact nearest driver");
                        searchForDriver();
                    }
                }
            }
        } else if (mDispatchState == State.SEARCHING) {
            if (dataSnapshot.getValue() != null) {
                Map<String, Object> dispatchRequests = (Map<String, Object>) dataSnapshot.getValue();
                Map<String, Object> dispatchRequest = (Map<String, Object>) dispatchRequests.get(mDispatchRequestKey);
                for (Map.Entry<String, Object> request : dispatchRequest.entrySet()) {
                    if (request.getKey().equals("Connected")) {
                        // 12 A. Driver has responded, send connect request, update state to "connected"
                        Log.d(DEBUG_LOG, "12 A. Driver has responded, send connect request, update state to \"connected\"");
                        String driverName = request.getValue().toString();
                        mFirebaseAvailableDrivers.child(driverName).child("Dispatch Request").setValue("Connected");
                        mDispatchState = State.CONNECTED;
                    }
                }
            }
        }
    }

    // Connected to dispatch
    public void trackDriver() {
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> driverInfo = (Map<String, Object>) dataSnapshot.getValue();

                if (mDriverLocationMarker != null) {
                    mDriverLocationMarker.remove();
                }

                // 14 A. Tracking driver...
                Log.d(DEBUG_LOG, "14 A. Tracking driver...");

                mDriverLocationMarker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng((Double) driverInfo.get("latitude"), (Double) driverInfo.get("longitude")))
                        .title((String) driverInfo.get("name"))
                        .snippet((String) driverInfo.get("number"))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    // Cancel a dispatch
    public void cancelDispatch(View view) {
        // 1. Change dispatch state to "not requesting a dispatch"
        mDispatchState = State.IDLE;

        // 2. Show fab and hide dispatch request state views
        view.setVisibility(View.GONE);
        mActivityWheel.setVisibility(View.GONE);
        mFab.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Dispatch Cancelled!", Toast.LENGTH_LONG).show();

        // 3. Delete dispatch request in the database
        destroyDispatchRequest();
    }

    public void destroyDispatchRequest() {
        if (mDispatchRequestKey != null) {
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).removeValue();
            mDispatchRequestKey = null;
        }
    }

    // Boundaries logic
    public boolean withinBounds() {

        // Savannah Bike Taxi operating boundaries
        LatLng A = new LatLng(32.082932, -81.096341);
        LatLng B = new LatLng(32.079433, -81.083713);
        LatLng C = new LatLng(32.062920, -81.089982);
        LatLng D = new LatLng(32.066280, -81.102650);

        LatLng P = mLocationMarker.getPosition();

        Double AREA_BOUND = Math.sqrt((Math.pow(A.latitude - B.latitude, 2) + Math.pow(A.longitude - B.longitude, 2))) *
                Math.sqrt((Math.pow(A.latitude - D.latitude, 2) + Math.pow(A.longitude - D.longitude, 2)));

        Double AREA_TOTAL = calculateArea(A, B, P) +
                calculateArea(A, D, P) +
                calculateArea(D, C, P) +
                calculateArea(C, B, P);

        return (AREA_BOUND >= AREA_TOTAL);
    }

    public double calculateArea(LatLng A, LatLng B, LatLng C) {
        Double a = Math.sqrt((Math.pow(A.latitude - B.latitude, 2) + Math.pow(A.longitude - B.longitude, 2)));
        Double b = Math.sqrt((Math.pow(A.latitude - C.latitude, 2) + Math.pow(A.longitude - C.longitude, 2)));
        Double c = Math.sqrt((Math.pow(B.latitude - C.latitude, 2) + Math.pow(B.longitude - C.longitude, 2)));
        Double s = (a+b+c) / 2;
        return Math.sqrt(s*(s-a)*(s-b)*(s-c));
    }


    /******* NAVIGATE TO LOGIN VIEW *******/
    public void signInAsDriver(View view) {
        if (mDriverClickCounter < 7) {
            mDriverClickCounter++;
        } else {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
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

        mLastKnownLocation = location;

        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        mLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        
        if (mDispatchState == State.CONNECTED) {
            trackUserLocation(location);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
        Log.d(DEBUG_LOG,"onConnectionFailed Fired");
    }

    public void trackUserLocation(Location location) {
        // 14 B. Tracking user...
        Log.d(DEBUG_LOG, "14 B. Tracking user...");

        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("longitude").setValue(location.getLongitude());
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("latitude").setValue(location.getLatitude());
    }
}
