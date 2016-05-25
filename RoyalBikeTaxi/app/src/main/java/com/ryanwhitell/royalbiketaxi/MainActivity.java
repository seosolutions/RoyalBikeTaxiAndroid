package com.ryanwhitell.royalbiketaxi;

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

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


    /******* VARIABLES *******/
    private final String DEBUG_LOG = "RBT Debug Log";

    // Logic
    private boolean mBoundsDisplayed;
    private boolean mDispatchState;
    private int mDriverClickCounter;

    // Alerts
    private AlertDialog.Builder mConfirmDispatchAlert;
    private AlertDialog.Builder mOutOfBoundsAlert;
    private ProgressBar mActivityWheel;

    // Google Api - Location, Map
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Polygon mBounds;
    private Marker mLocationMarker;

    // Firebase database
    private DatabaseReference mDatabaseRef;
    private String mDispatchRequestKey;

    // Navigation
    private Button mCancelDispatch;
    private FloatingActionButton mFab;



    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Logic
        mBoundsDisplayed = false;
        mDriverClickCounter = 1;
        mDispatchState = false;

        // Initialize Alerts
        mConfirmDispatchAlert = new AlertDialog.Builder(this)
                .setTitle("Confirm Dispatch to My Location")
                .setMessage(
                        "By clicking 'CONFIRM' you agree " +
                                "to accepting a ride from our nearest " +
                                "driver. A bike will be dispatched to your " +
                                "location, please wait in a convenient pickup " +
                                "location and do not navigate away " +
                                "from the current screen. Thank you!")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmDispatch(which);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
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
                                "'TOGGLE BOUNDS'")
                .setPositiveButton("Toggle Bounds", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleBoundaries(true);
                    }
                })
                .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleBoundaries(false);
                    }
                });

        // Initialize Navigation
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

        // Initialize Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mDatabaseRef = database.getReference();
        mDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                firebaseDataChanged(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError firebaseError) {
                // Failed to read value
                // TODO: Handle error
                Log.w(DEBUG_LOG, "Failed to read value.", firebaseError.toException());
            }
        });

        // Google Api - Location, Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
        destroyDispatchRequest();
    }

    @Override
    protected void onStop() {
        super.onStop();
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
            // 1. Change dispatch state to "currently requesting a dispatch"
            mDispatchState = true;

            // 2. Hide fab and show dispatch request state views
            mCancelDispatch.setVisibility(View.VISIBLE);
            mActivityWheel.setVisibility(View.VISIBLE);
            mFab.setVisibility(View.GONE);
            Toast.makeText(this, "Requesting a driver...", Toast.LENGTH_LONG).show();

            // 3. Create a dispatch request in the database
            mDispatchRequestKey = mDatabaseRef.child("Dispatch Request").push().getKey();
        }
    }

    public void cancelDispatch(View view) {
        // 1. Change dispatch state to "not requesting a dispatch"
        mDispatchState = false;

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
            mDatabaseRef.child("Dispatch Request").child(mDispatchRequestKey).removeValue();
            mDispatchRequestKey = null;
        }
    }

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


    /******* DATABASE *******/
    public void firebaseDataChanged(DataSnapshot dataSnapshot) {
        // TODO: Use to update view of driver on route to dispatch
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
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        
        if (mDispatchState) {
            trackUserLocation(location);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
        Log.d(DEBUG_LOG,"onConnectionFailed Fired");
    }

    public void trackUserLocation(Location location) {
        mDatabaseRef.child("Dispatch Request").child(mDispatchRequestKey).setValue(location);
    }
}
