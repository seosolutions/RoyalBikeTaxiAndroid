package com.ryanwhitell.royalbiketaxi.Controller.View;

import android.Manifest;
import android.content.Context;
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

    //TODO: suppress all unchecked casts @SuppressWarnings("unchecked")
    //TODO: remove all event listeners
    // TODO: Quell AlL warnings, cancel dispatch thing still not working
    // TODO: LIFE CYCLE!!

    /******* VARIABLES *******/
    // Debugging
    private final String DEBUG_REQUEST_DISPATCH = "RequestDispatch";
    private final String DEBUG_DRIVER_LOCATIONS = "RequestLocations";
    private final String DEBUG_ON_CONNECTED = "Connected";
    private final String DEBUG_ON_CANCEL = "Cancelled";
    private final String DEBUG_ACTIVITY_LC = "Lifecycle";

    // Alerts
    private AlertDialog.Builder mConfirmDispatchAlert;
    private AlertDialog.Builder mOutOfBoundsAlert;
    private AlertDialog.Builder mNoAvailableDriversAlert;
    private ProgressBar mActivityWheel;

    // Firebase
    private DatabaseReference mFirebaseUserDispatchRequest;
    private DatabaseReference mFirebaseAvailableDrivers;
    private DatabaseReference mFirebaseLocationRequest;
    private String mDispatchRequestKey;
    private ValueEventListener mUserDispatchRequestListener;
    private ValueEventListener mUserDispatchRequestDriverListener;

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
    private Boolean mActionBarButtonState;

    // Runnable, Handler, Context
    private int mNumberOfDrivers;
    private int mIndex;
    private Handler mHandler;
    private Context mContext;


    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // onCreate()
        Log.d(DEBUG_ACTIVITY_LC, "onCreate()");


        // DEBUGGING keep screen alive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


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
                        confirmDispatch();
                    }
                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        mOutOfBoundsAlert = new AlertDialog.Builder(this)
                .setTitle("Out of Bounds Alert")
                .setMessage(
                        "You are currently trying to request " +
                                "a dispatch outside of our " +
                                "operating boundaries. To view boundaries, click " +
                                "TOGGLE BOUNDS")
                .setPositiveButton("TOGGLE BOUNDS", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleBoundaries();
                    }
                })
                .setNegativeButton("BACK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        mNoAvailableDriversAlert = new AlertDialog.Builder(this)
                .setTitle("No Drivers Available")
                .setMessage(
                        "No drivers are currently available, " +
                                "please call dispatch to set up a " +
                                "ride or try again in a few minuets."
                ).setPositiveButton("CALL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //TODO: call dispatch
                    }
                })
                .setNegativeButton("BACK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });


        /******* Initialize Firebase *******/
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = database.getReference("Available Drivers");
        mFirebaseLocationRequest = database.getReference("Location Request");
        mFirebaseUserDispatchRequest = database.getReference("Dispatch Request");


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

        mActionBarButtonState = true;


        /******* Context *******/
        mContext = this;
    }

    @Override
    protected void onStart() {
        super.onStart();

        // onStart()
        Log.d(DEBUG_ACTIVITY_LC, "onStart()");

        // Initialize user dispatch request listener
        mUserDispatchRequestListener = mFirebaseUserDispatchRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                driverHandshake(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(mContext, "Dispatch Cancelled. There was a database error!", Toast.LENGTH_LONG).show();
                // 0 B. Dispatch cancelled from database error 1
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 1");
                destroyDispatchRequest();
            }
        });

        // Connect to the api client
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // onStop()
        Log.d(DEBUG_ACTIVITY_LC, "onStop()");

        // Disconnect the api client
        mGoogleApiClient.disconnect();

        // Destroy any dispatch requests
        // 0 C. Dispatch cancelled from onStop()
        Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from onStop()");
        destroyDispatchRequest();

        // Remove event listeners
        mFirebaseUserDispatchRequest.removeEventListener(mUserDispatchRequestListener);

        if (mUserDispatchRequestDriverListener != null) {
            mFirebaseUserDispatchRequest.removeEventListener(mUserDispatchRequestDriverListener);
        }
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
            toggleBoundaries();
            return true;
        } else if ((id == R.id.action_refresh_drivers) && (mActionBarButtonState)) {
            // 1. Refresh driver locations button press
            Log.d(DEBUG_DRIVER_LOCATIONS, "1. Refresh driver locations button press");
            updateMap();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (mActionBarButtonState) {
            if (id == R.id.nav_book) {
            } else if (id == R.id.nav_info) {
            } else if (id == R.id.nav_help) {
            } else if (id == R.id.nav_call) {
            }
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void toggleBoundaries() {
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


    /******* USER DISPATCH LOGIC *******/
    // View available drivers
    public void updateMap(){
        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    // 2. Get driver locations
                    Log.d(DEBUG_DRIVER_LOCATIONS, "2. Get driver locations");
                    Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                    // 3. Clear current map
                    Log.d(DEBUG_DRIVER_LOCATIONS, "3. Clear current map");
                    mMap.clear();

                    // 4. Update my location marker
                    Log.d(DEBUG_DRIVER_LOCATIONS, "4. Update my location marker");
                    updateMyMarker(mLastKnownLocation);

                    // 5. Update drivers locations on map
                    Log.d(DEBUG_DRIVER_LOCATIONS, "5. Update drivers locations on map");
                    for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                        mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString()),
                                        Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString())))
                                .title(driver.getKey())
                                .snippet(((Map<String, Object>) driver.getValue()).get("phone number").toString())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(mContext, "Dispatch Cancelled. There was a database error!", Toast.LENGTH_LONG).show();
                // 0 B. Dispatch cancelled from database error 76
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 7");
                destroyDispatchRequest();
            }
        });
    }

    // Request a dispatch
    public void onClickFab(View view){
        //TODO: Check that is connected to internet and what not
        if (withinBounds()) {
            mConfirmDispatchAlert.create();
            mConfirmDispatchAlert.show();
        } else {
            mOutOfBoundsAlert.create();
            mOutOfBoundsAlert.show();
        }
    }

    public void confirmDispatch() {
        // 1. Hide fab and show dispatch request state views, prevent device sleep, disable action bar buttons
        Log.d(DEBUG_REQUEST_DISPATCH, "1. Hide fab and show dispatch request state views, prevent device sleep");
        mCancelDispatch.setVisibility(View.VISIBLE);
        mActivityWheel.setVisibility(View.VISIBLE);
        mFab.setVisibility(View.GONE);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mActionBarButtonState = false;
        Toast.makeText(mContext, "Locating drivers...", Toast.LENGTH_LONG).show();

        // 2. Refresh all driver locations
        Log.d(DEBUG_REQUEST_DISPATCH, "2. Refresh all driver locations");
        String key = mFirebaseLocationRequest.push().getKey();
        mFirebaseLocationRequest.child(key).setValue("REQUEST");
        mFirebaseLocationRequest.child(key).removeValue();

        // 3 A. Update Driver Locations
        Log.d(DEBUG_REQUEST_DISPATCH, "3 A. Update Driver Locations");
        updateDriverLocations();

        // 4. Change dispatch state to "currently waiting for driver update"
        Log.d(DEBUG_REQUEST_DISPATCH, "4. Change dispatch state to \"currently waiting for driver update\"");
        mDispatchState = State.WAITING;
    }

    public void updateDriverLocations(){

        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.getValue() != null) {

                    Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                    mDriverLocations.clear();

                    // 5. Update driver locations
                    Log.d(DEBUG_REQUEST_DISPATCH, "5. Update driver locations");
                    for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                        DriverLocation location = new DriverLocation(new LatLng(Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString()),
                                Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString())),
                                driver.getKey());
                        location.setDistance(new LatLng(mLocationMarker.getPosition().latitude, mLocationMarker.getPosition().longitude));

                        mDriverLocations.add(location);
                    }

                    // 6. Push dispatch request onto the database
                    Log.d(DEBUG_REQUEST_DISPATCH, "6. Push dispatch request onto the database");
                    mDispatchRequestKey = mFirebaseUserDispatchRequest.push().getKey();
                    mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("longitude").setValue(mLastKnownLocation.getLongitude());
                    mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("latitude").setValue(mLastKnownLocation.getLatitude());

                    // 7. Set state to "Requesting a dispatch"
                    Log.d(DEBUG_REQUEST_DISPATCH, "7. Set state to \"Requesting a dispatch\"");
                    mDispatchState = State.REQUESTING;

                } else {
                    // 3 B. No drivers are currently available
                    Log.d(DEBUG_REQUEST_DISPATCH, "3 B. No drivers are currently available");
                    mDispatchState = State.IDLE;
                    mNoAvailableDriversAlert.create().show();
                    destroyDispatchRequest();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(mContext, "Dispatch Cancelled. There was a database error!", Toast.LENGTH_LONG).show();
                // 0 B. Dispatch cancelled from database error 2
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 2");
                destroyDispatchRequest();
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
                    if ((mIndex < mNumberOfDrivers-1)) {
                        // 12 B. Check to see if the driver has responded, else try the next on the list
                        Log.d(DEBUG_REQUEST_DISPATCH, "12 B. Check to see if the driver has responded, else try the next on the list");

                        if (mDispatchState == State.CONNECTED) {
                            // 13. Connected to driver, quit runnable, track driver
                            Log.d(DEBUG_REQUEST_DISPATCH, "13. Connected to driver, quit runnable");
                            Toast.makeText(mContext, "Connected!", Toast.LENGTH_LONG).show();
                            onConnectedToDriver();
                        } else if (mDispatchState == State.SEARCHING) {
                            // 12 C1. Driver has not responded, request next closest driver
                            Log.d(DEBUG_REQUEST_DISPATCH, "12 C1. Driver has not responded, request next closest driver");
                            Toast.makeText(mContext, "Driver declined - trying next closest driver...", Toast.LENGTH_LONG).show();
                            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
                            mIndex++;
                            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).getName()).child("Dispatch Request").setValue(mDispatchRequestKey);
                            mHandler.postDelayed(this, 10000);
                        }
                    } else {
                        if (mDispatchState == State.SEARCHING){
                            // 12 C2. No drivers are currently available
                            Log.d(DEBUG_REQUEST_DISPATCH, "12 C2. No drivers are currently available");
                            mDispatchState = State.IDLE;
                            mNoAvailableDriversAlert.create().show();
                            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
                            destroyDispatchRequest();
                        }
                    }
                }
            };

            if (mDriverLocations.get(mIndex) != null) {
                // 11. Request nearest available driver and provide 10 seconds for response
                Log.d(DEBUG_REQUEST_DISPATCH, "11. Request nearest available driver and provide 10 seconds for response");
                Toast.makeText(mContext, "Contacting nearest driver...", Toast.LENGTH_LONG).show();
                mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).getName()).child("Dispatch Request").setValue(mDispatchRequestKey);
                mHandler.postDelayed(waitForResponse, 10000);
            } else {
                mNoAvailableDriversAlert.create().show();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void driverHandshake(DataSnapshot dataSnapshot){
        if (mDispatchState == State.REQUESTING) {
            if (dataSnapshot.getValue() != null) {
                Map<String, Object> dispatchRequests = (Map<String, Object>) dataSnapshot.getValue();
                for (Map.Entry<String, Object> request : dispatchRequests.entrySet()) {
                    if (request.getKey().equals(mDispatchRequestKey)) {
                        // 8. Sort available drivers by closest
                        Log.d(DEBUG_REQUEST_DISPATCH, "8. Sort available drivers by closest");
                        if (mDriverLocations != null) {
                            Collections.sort(mDriverLocations);
                        }

                        // 9. Change dispatch state to "Searching and contacting nearest driver"
                        Log.d(DEBUG_REQUEST_DISPATCH, "9. Change dispatch state to \"Searching and contacting nearest driver\"");
                        mDispatchState = State.SEARCHING;

                        // 10. Find and contact nearest driver
                        Log.d(DEBUG_REQUEST_DISPATCH, "10. Find and contact nearest driver");
                        searchForDriver();
                    }
                }
            }
        } else if (mDispatchState == State.SEARCHING) {
            if (dataSnapshot.getValue() != null) {
                Map<String, Object> dispatchRequests = (Map<String, Object>) dataSnapshot.getValue();
                Map<String, Object> dispatchRequest;

                if (dispatchRequests.get(mDispatchRequestKey) != null) {
                    dispatchRequest = (Map<String, Object>) dispatchRequests.get(mDispatchRequestKey);
                    for (Map.Entry<String, Object> request : dispatchRequest.entrySet()) {
                        if (request.getKey().equals("Connected")) {
                            // 12 A. Driver has responded, send connect request, update state to "connected"
                            Log.d(DEBUG_REQUEST_DISPATCH, "12 A. Driver has responded, send connect request, update state to \"connected\"");
                            String driverName = request.getValue().toString();
                            Toast.makeText(mContext, "Connecting to " + driverName + "...", Toast.LENGTH_LONG).show();
                            mFirebaseAvailableDrivers.child(driverName).child("Dispatch Request").setValue("Connected");
                            mDispatchState = State.CONNECTED;
                        }
                    }
                } else {
                    //TODO: Handle Error
                }
            }
        }
    }

    // Connected to dispatch
    public void onConnectedToDriver() {

        // 1. Hide dispatch request buttons
        Log.d(DEBUG_ON_CONNECTED, "1. Hide dispatch request buttons");
        mCancelDispatch.setVisibility(View.GONE);
        mActivityWheel.setVisibility(View.GONE);

        // 2. Clear map
        Log.d(DEBUG_ON_CONNECTED, "2. Clear map");
        mMap.clear();

        // 3. Update my marker
        Log.d(DEBUG_ON_CONNECTED, "3. Update my marker");
        updateMyMarker(mLastKnownLocation);

        // 4. Listen for driver location updates
        Log.d(DEBUG_ON_CONNECTED, "4. Listen for driver location updates");
        mUserDispatchRequestDriverListener = mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").addValueEventListener(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {

                    Map<String, Object> driverInfo = (Map<String, Object>) dataSnapshot.getValue();

                    if (mDriverLocationMarker != null) {
                        mDriverLocationMarker.remove();
                    }

                    // Tracking driver location...
                    Log.d(DEBUG_ON_CONNECTED, "Tracking driver location...");

                    if ((driverInfo.get("latitude") != null) &&
                    (driverInfo.get("longitude") != null) &&
                            (driverInfo.get("name") != null) &&
                            (driverInfo.get("number") != null)) {
                        mDriverLocationMarker = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng((Double) driverInfo.get("latitude"), (Double) driverInfo.get("longitude")))
                                .title((String) driverInfo.get("name"))
                                .snippet((String) driverInfo.get("number"))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                    } else {
                        //TODO: Handle error
                    }
                } else {
                    //TODO: Handle error
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(mContext, "Dispatch Cancelled. There was a database error!", Toast.LENGTH_LONG).show();
                // 0 B. Dispatch cancelled from database error 5
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 5");
                destroyDispatchRequest();
            }
        });

        // 5. Listen for driver ending dispatch
        Log.d(DEBUG_ON_CONNECTED, "5. Listen for driver ending dispatch");
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.getValue().equals("Driver Cancelled")) {
                        // 6. Driver ended dispatch
                        Log.d(DEBUG_ON_CONNECTED, "6. Driver ended dispatch");
                        Toast.makeText(mContext, "Dispatch ended by driver", Toast.LENGTH_LONG).show();
                        mDispatchState = State.IDLE;

                        // 0 D. Dispatch ended from driver
                        Log.d(DEBUG_ON_CANCEL, "0 D. Dispatch ended from driver");
                        destroyDispatchRequest();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(mContext, "Dispatch Cancelled. There was a database error!", Toast.LENGTH_LONG).show();
                // 0 B. Dispatch cancelled from database error 6
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 6");
                destroyDispatchRequest();
            }
        });

    }

    // Cancel a dispatch
    public void cancelDispatch(View view) {
        // 0 A. Dispatch cancelled from cancel button click
        Log.d(DEBUG_ON_CANCEL, "0 A. Dispatch cancelled from cancel button click");
        destroyDispatchRequest();
    }

    public void destroyDispatchRequest() {

        // 1. Change dispatch state to "not requesting a dispatch"
        Log.d(DEBUG_ON_CANCEL, "1. Change dispatch state to \"not requesting a dispatch\"");
        if (mDispatchState != State.IDLE) {
            Toast.makeText(mContext, "Dispatch Cancelled!", Toast.LENGTH_LONG).show();
        }

        // 2. Show fab and hide dispatch request state views, enable action bar buttons
        Log.d(DEBUG_ON_CANCEL, "2. Show fab and hide dispatch request state views, enable action bar buttons");
        mCancelDispatch.setVisibility(View.GONE);
        mActivityWheel.setVisibility(View.GONE);
        mFab.setVisibility(View.VISIBLE);
        mActionBarButtonState = true;

        // 3. Let the device sleep, clear driver marker
        if (mDriverLocationMarker != null) {
            mDriverLocationMarker.remove();
        }
        Log.d(DEBUG_ON_CANCEL, "3. Let the device sleep");
        //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (mDispatchState == State.SEARCHING) {
            // 4 A. Destroy current search query
            Log.d(DEBUG_ON_CANCEL, "4 A. Destroy current search query");
            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
        }
        if (mDispatchState == State.CONNECTED) {
            // 4 B. Set connected node to "Cancelled" if connected
            Log.d(DEBUG_ON_CANCEL, "4 B. Set connected node to \"Cancelled\" if connected");
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue("User Cancelled");
        }
        if (mDispatchRequestKey != null) {
            // 4 C. Remove dispatch request from the database
            Log.d(DEBUG_ON_CANCEL, "4 C. Remove dispatch request from the database");
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).removeValue();
            mDispatchRequestKey = null;
        }

        mDispatchState = State.IDLE;
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
    }

    @Override
    public void onLocationChanged(Location location) {

        mLastKnownLocation = location;

        updateMyMarker(location);
        
        if (mDispatchState == State.CONNECTED) {
            trackUserLocation(location);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
    }

    public void trackUserLocation(Location location) {
        // Tracking my location...
        Log.d(DEBUG_ON_CONNECTED, "Tracking my location...");

        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("longitude").setValue(location.getLongitude());
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("latitude").setValue(location.getLatitude());
    }

    public void updateMyMarker(Location location) {

        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        mLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }
}
