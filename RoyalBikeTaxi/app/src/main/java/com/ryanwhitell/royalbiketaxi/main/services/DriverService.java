package com.ryanwhitell.royalbiketaxi.main.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;
import com.ryanwhitell.royalbiketaxi.main.activities.DriverActivity;

import java.util.Map;

/**
 * Created by Ryan on 7/15/2016.
 */
public class DriverService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    /******* VARIABLES *******/
    //Firebase
    private DatabaseReference mFirebaseAvailableDrivers;
    private ValueEventListener mMyDispatchRequestListener;
    private DatabaseReference mFirebaseLocationRequest;
    private ValueEventListener mLocationRequestListener;

    //Global
    public static boolean sIsActive;
    public static String sDriverName;
    public static String sDriverNumber;
    public static String sDispatchRequestKey;

    //Google Api - Location
    private GoogleApiClient mGoogleApiClient;
    private Location mLastKnownLocation;

    //Notifications
    private Notification mNotification;
    private NotificationManager mNotificationManager;

    //Context
    private Context mContext;


    /******* SERVICE *******/
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.

        String action = intent.getAction();

        if (action.equals("Start") && (!sIsActive)) {
            onStartService();
        } else if (action.equals("ACCEPT")) {
            //onAcceptDispatchRequest();

        } else if (action.equals("DECLINE")) {
            //onDeclineDispatchRequest();

        } else if (action.equals("Stop") && (sIsActive)) {
            Toast.makeText(this, "Foreground Service Stopped", Toast.LENGTH_LONG).show();
            stopForeground(true);
            stopListening();
            stopSelf();
            sIsActive = false;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        sIsActive = false;
        Toast.makeText(this, "Foreground Service Destroyed", Toast.LENGTH_LONG).show();
    }

    // Intent Control
    public void onStartService() {
        Toast.makeText(this, "Foreground Service Started", Toast.LENGTH_LONG).show();
        sIsActive = true;

        //Google API CLIENT
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //Context
        mContext = this;

        //Firebase
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = firebaseDatabase.getReference().child("Available Drivers");
        mFirebaseLocationRequest = firebaseDatabase.getReference("Location Request");

        makeNotification();

        startListening();
    }

    public void onStopService() {

    }

    public void makeNotification() {

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(this, DriverActivity.class);
        notificationIntent.setAction("Main");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0,
                notificationIntent,
                0);

        Intent yesIntent = new Intent(this, DriverService.class);
        yesIntent.setAction("Yes");
        PendingIntent pYesIntent = PendingIntent.getService(this, 0,
                yesIntent, 0);

        Intent noIntent = new Intent(this, DriverService.class);
        noIntent.setAction("No");
        PendingIntent pNoIntent = PendingIntent.getService(this, 0,
                noIntent, 0);

        mNotification = new NotificationCompat.Builder(this)
                .setContentTitle("You are online as " + sDriverName)
                .setContentText("Currently awaiting a dispatch...")
                .setSmallIcon(R.drawable.ic_directions_bike_white)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();

        startForeground(7, mNotification);
    }

    /******* FIREBASE *******/
    public void startListening() {

        mMyDispatchRequestListener = mFirebaseAvailableDrivers.child(sDriverName).child("Dispatch Request").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {

                    Intent acceptIntent = new Intent(mContext, DriverService.class);
                    acceptIntent.setAction("ACCEPT");
                    PendingIntent pAcceptIntent = PendingIntent.getService(mContext, 0,
                            acceptIntent, 0);

                    Intent declineIntent = new Intent(mContext, DriverService.class);
                    declineIntent.setAction("DECLINE");
                    PendingIntent pDeclineIntent = PendingIntent.getService(mContext, 0,
                            declineIntent, 0);

                    mNotification = new NotificationCompat.Builder(mContext)
                            .setContentTitle("Incoming dispatch!")
                            .setContentText("Please accept or decline")
                            .setSmallIcon(R.drawable.ic_directions_bike_white)
                            .setContentIntent(null)
                            .setOngoing(true)
                            .addAction(R.drawable.ic_done_black_24dp,
                                    "ACCEPT", pAcceptIntent)
                            .addAction(R.drawable.ic_clear_black_24dp, "DECLINE",
                                    pDeclineIntent).build();

                    mNotificationManager.notify(7, mNotification);

                    sDispatchRequestKey = dataSnapshot.getValue().toString();

                } else {
                    Intent notificationIntent = new Intent(mContext, DriverActivity.class);
                    notificationIntent.setAction("Main");
                    notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(mContext,
                            0,
                            notificationIntent,
                            0);

                    mNotification = new NotificationCompat.Builder(mContext)
                            .setContentTitle("You are online as " + sDriverName)
                            .setContentText("Currently awaiting a dispatch...")
                            .setSmallIcon(R.drawable.ic_directions_bike_white)
                            .setContentIntent(pendingIntent)
                            .setOngoing(true).build();

                    mNotificationManager.notify(7, mNotification);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mLocationRequestListener = mFirebaseLocationRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                driverLocationRequestReceived();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //showToast("Could not handle location request. There was a database error!");
            }
        });
    }

    private void stopListening() {
    }


    public void driverLocationRequestReceived() {

        mGoogleApiClient.connect();

        if ((mLastKnownLocation != null)) {
            mFirebaseAvailableDrivers.child(sDriverName).child("latitude").setValue(mLastKnownLocation.getLatitude());
            mFirebaseAvailableDrivers.child(sDriverName).child("longitude").setValue(mLastKnownLocation.getLongitude());
            mFirebaseAvailableDrivers.child(sDriverName).child("phoneNumber").setValue(sDriverNumber);
        }
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

        mFirebaseAvailableDrivers.child(sDriverName).child("latitude").setValue(location.getLatitude());
        mFirebaseAvailableDrivers.child(sDriverName).child("longitude").setValue(location.getLongitude());
        mFirebaseAvailableDrivers.child(sDriverName).child("phoneNumber").setValue(sDriverNumber);

        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
    }

}