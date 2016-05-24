package com.ryanwhitell.royalbiketaxi;

import android.Manifest;
import android.content.DialogInterface;
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
import android.widget.TextView;
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

    // Google map Api
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Polygon mBounds;
    private boolean mBoundsDisplayed;

    // Firebase database
    private DatabaseReference mDatabaseRef;

    // Location services
    private Marker mLocationMarker;

    // Logic and Navigation
    private boolean mDispatchState;
    private int mDriverClickCounter;
    private AlertDialog.Builder mAlert;
    private Button mCancelDispatch;
    private FloatingActionButton mFAB;
    private ProgressBar mActivityWheel;


    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Initialize Variables
        mBoundsDisplayed = false;
        mDriverClickCounter = 1;
        mDispatchState = false;
        mAlert = new AlertDialog.Builder(this)
                .setTitle("Confirm Dispatch to My Location")
                .setMessage(
                        "By clicking 'Confirm' you agree " +
                                "to accepting a ride from our nearest " +
                                "driver. A bike will be dispatched to your " +
                                "location, please wait in a convenient pickup " +
                                "location. Thank you!")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertButtonClick(dialog, which);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertButtonClick(dialog, which);
                    }
                });
        mCancelDispatch = (Button) findViewById(R.id.cancel__dispatch_button);
        assert mCancelDispatch != null;
        mCancelDispatch.setVisibility(View.GONE);
        mActivityWheel = (ProgressBar) findViewById(R.id.progress_bar);
        assert mActivityWheel != null;
        mActivityWheel.setVisibility(View.GONE);


        // Database
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


        // Navigation
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mFAB = (FloatingActionButton) findViewById(R.id.fab);
        assert mFAB != null;
        mFAB.setOnClickListener(new View.OnClickListener() {
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


        // Google Map
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
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
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
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
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_toggle_bounds) {
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
            Log.d(DEBUG_LOG, "toggle mBounds");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
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


    /******* USER DISPATCH LOGIC *******/
    public void onClickFab(View view){
        mAlert.create();
        mAlert.show();
    }

    public void trackUser(Location location) {

    }

    public void alertButtonClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mDispatchState = true;
            mCancelDispatch.setVisibility(View.VISIBLE);
            mActivityWheel.setVisibility(View.VISIBLE);
            mFAB.setVisibility(View.GONE);
            Toast.makeText(this, "Requesting a driver...", Toast.LENGTH_LONG).show();
        }
    }

    public void cancelDispatch(View view) {
        mDispatchState = false;
        view.setVisibility(View.GONE);
        mActivityWheel.setVisibility(View.GONE);
        mFAB.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Dispatch Cancelled!", Toast.LENGTH_LONG).show();
    }


    /******* NAVIGATE TO DRIVER VIEW *******/
    public void signInAsDriver(View view) {
        if (mDriverClickCounter < 7) {
            mDriverClickCounter++;
        } else {
            Log.d(DEBUG_LOG, "login");
        }
        Log.d(DEBUG_LOG, "driver");
    }


    /******* DATABASE *******/
    public void firebaseDataChanged(DataSnapshot dataSnapshot) {
        Log.d(DEBUG_LOG, "database Changed");
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

        // Move the camera to Savannah
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
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(500);

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
                .title("My Location"));
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
        Log.d(DEBUG_LOG,"onConnectionFailed Fired");
    }
}
