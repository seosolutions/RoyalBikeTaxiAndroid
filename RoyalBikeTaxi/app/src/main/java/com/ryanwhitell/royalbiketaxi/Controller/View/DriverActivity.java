package com.ryanwhitell.royalbiketaxi.Controller.View;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;

import java.util.Map;

public class DriverActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // TODO: LIFECYCLE and CLEAR LISTENERS

    /******* VARIABLES *******/
    // Debugging
    private final String DEBUG_REQUEST_DISPATCH = "Request Dispatch";
    private final String DEBUG_DRIVER_LOCATIONS = "Request Locations";
    private final String DEBUG_ON_CONNECTED = "Connected";
    private final String DEBUG_ON_CANCEL = "Cancelled";
    private final String DEBUG_ACTIVITY_LC = "Lifecycle";

    // Alerts
    private AlertDialog.Builder mIncomingDispatchAlert;

    // Driver Information
    private String mName;
    private String mNumber;
    private Location mLastKnownLocation;

    // Firebase
    private DatabaseReference mFirebaseAvailableDrivers;
    private DatabaseReference mFirebaseLocationRequest;
    private DatabaseReference mFirebaseUserDispatchRequest;
    private String mDispatchRequestKey;

    //Flow Control
    private enum State {
        AVAILABLE, ON_DISPATCH
    }
    private State mDispatchState;

    // Google Api - Location, Map
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Marker mLocationMarker;
    private Marker mUserLocationMarker;

    //Navigation
    private Button mEndButton;
    private FloatingActionButton mRefreshFab;


    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        // DEBUGGING keep screen alive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        /******* Initialize Alerts *******/
        mIncomingDispatchAlert = new AlertDialog.Builder(this)
                .setTitle("Accept Incoming Dispatch")
                .setMessage(
                        "You have 10 seconds to accept the incoming " +
                                "dispatch. Please ACCEPT or DECLINE.")
                .setPositiveButton("ACCEPT", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        incomingDispatchAlert(which);
                    }
                })
                .setNegativeButton("DECLINE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        incomingDispatchAlert(which);
                    }
                });


        /******** Initialize Driver Information *******/
        mDispatchState = State.AVAILABLE;

        Intent intent = getIntent();
        mName = intent.getStringExtra("name");
        mNumber = intent.getStringExtra("number");


        /******* Initialize Firebase *******/
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = database.getReference("Available Drivers");
        mFirebaseUserDispatchRequest = database.getReference("Dispatch Request");
        mFirebaseLocationRequest = database.getReference("Location Request");

        /******* Initialize Google Api - Location, Map *******/
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        /******* Initialize Navigation *******/
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("You are logged in as " + mName);
        setSupportActionBar(toolbar);

        mRefreshFab = (FloatingActionButton) findViewById(R.id.fab);
        assert mRefreshFab != null;
        mRefreshFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestDriverLocations();
            }
        });

        mEndButton = (Button) findViewById(R.id.end_button);
        mEndButton.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mFirebaseAvailableDrivers.child(mName).child("Dispatch Request").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.getValue().equals("Connected")) {
                        connectedToCustomer(dataSnapshot);
                    } else {
                        mDispatchRequestKey = dataSnapshot.getValue().toString();
                        mIncomingDispatchAlert.create().show();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mFirebaseLocationRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (mDispatchState == State.AVAILABLE) {
                    driverLocationRequestReceived();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // TODO: handle
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }


    /******* DRIVER LOGIC *******/
    // Updating Driver Map
    public void requestDriverLocations() {
        String key = mFirebaseLocationRequest.push().getKey();
        mFirebaseLocationRequest.child(key).setValue("REQUEST");
        mFirebaseLocationRequest.child(key).removeValue();
    }

    public void driverLocationRequestReceived() {
        mGoogleApiClient.connect();
        if ((mLastKnownLocation != null) && (mDispatchState == State.AVAILABLE)) {
            mFirebaseAvailableDrivers.child(mName).child("latitude").setValue(mLastKnownLocation.getLatitude());
            mFirebaseAvailableDrivers.child(mName).child("longitude").setValue(mLastKnownLocation.getLongitude());
            mFirebaseAvailableDrivers.child(mName).child("phone number").setValue(mNumber);
        }
        updateMap();
        mFirebaseAvailableDrivers.child(mName).child("phone number").setValue(mNumber);
    }

    public void updateMap(){
        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                mMap.clear();

                for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                    if (!driver.getKey().equals(mName)) {
                        Double lat = Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString());
                        Double lon = Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString());
                        String number = ((Map<String, Object>) driver.getValue()).get("phone number").toString();

                        mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(lat, lon))
                                .title(driver.getKey().toString())
                                .snippet(number)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: handle
            }
        });
    }

    // Dispatch Logic
    public void incomingDispatchAlert(int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue(mName);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            mFirebaseAvailableDrivers.child(mName).child("Dispatch Request").removeValue();
        }
    }

    public void connectedToCustomer(DataSnapshot dataSnapshot) {

        mMap.clear();

        mEndButton.setVisibility(View.VISIBLE);
        mRefreshFab.setVisibility(View.GONE);

        updateMyMarker(mLastKnownLocation);

        mFirebaseAvailableDrivers.child(mName).removeValue();
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").child("name").setValue(mName);
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").child("number").setValue(mNumber);
        mDispatchState = State.ON_DISPATCH;
        mGoogleApiClient.connect();

        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                Map<String, Object> userInfo = (Map<String, Object>) dataSnapshot.getValue();

                if (mUserLocationMarker != null) {
                    mUserLocationMarker.remove();
                }

                mUserLocationMarker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng((Double) userInfo.get("latitude"), (Double) userInfo.get("longitude")))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.getValue().equals("Cancelled")) {
                        disconnectFromDispatchRequest();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: Handle Error
            }
        });
    }

    public void onEndButtonClick(View view) {
        disconnectFromDispatchRequest();
    }

    public void disconnectFromDispatchRequest() {
        mDispatchState = State.AVAILABLE;
        mEndButton.setVisibility(View.GONE);
        mRefreshFab.setVisibility(View.VISIBLE);

        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue("Cancelled");

        mMap.clear();

        requestDriverLocations();
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
    }

    @Override
    public void onLocationChanged(Location location) {

        mLastKnownLocation = location;

        updateMyMarker(location);

        if (mDispatchState == State.AVAILABLE) {
            mGoogleApiClient.disconnect();
        } else if (mDispatchState == State.ON_DISPATCH) {
            trackDriverLocation(location);
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
    }

    public void trackDriverLocation(Location location) {
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver")
                .child("latitude")
                .setValue(location.getLatitude());
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver")
                .child("longitude")
                .setValue(location.getLongitude());

    }

    public void updateMyMarker(Location location) {

        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        mLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title("ME")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        if (mDispatchState == State.AVAILABLE) {
            mFirebaseAvailableDrivers.child(mName).child("latitude").setValue(location.getLatitude());
            mFirebaseAvailableDrivers.child(mName).child("longitude").setValue(location.getLongitude());
        }
    }

}
