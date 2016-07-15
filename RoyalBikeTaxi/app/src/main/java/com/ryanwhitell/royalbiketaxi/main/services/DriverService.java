package com.ryanwhitell.royalbiketaxi.main.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

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
public class DriverService extends Service {

    /******* VARIABLES *******/
    //Firebase
    private DatabaseReference mFirebaseAvailableDrivers;

    //Global
    public static boolean sIsActive;
    public static String sDriverName;
    public static String sDriverNumber;

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
            Toast.makeText(this, "Foreground Service Started", Toast.LENGTH_LONG).show();
            sIsActive = true;

            mContext = this;

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

//                    .addAction(android.R.drawable.ic_menu_add,
//                            "Yes", pYesIntent)
//                    .addAction(android.R.drawable.ic_menu_delete, "No",
//                            pNoIntent).build();

            startListening();

            startForeground(7, mNotification);

        } else if (action.equals("Yes")) {
            Toast.makeText(this, "Yes", Toast.LENGTH_LONG).show();

        } else if (action.equals("No")) {
            Toast.makeText(this, "No", Toast.LENGTH_LONG).show();
            Toast.makeText(this, "Foreground Service Stopped", Toast.LENGTH_LONG).show();
            stopForeground(true);
            stopSelf();
            sIsActive = false;

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


    /******* FIREBASE *******/
    public void startListening() {
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = firebaseDatabase.getReference().child("Available Drivers");
        mFirebaseAvailableDrivers.child(sDriverName).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    Map<String, String> driverData = (Map<String, String>) dataSnapshot.getValue();
                    if (driverData.get("Dispatch Request") != null) {
                        Intent notificationIntent = new Intent(mContext, DriverActivity.class);
                        notificationIntent.setAction("Main");
                        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        PendingIntent pendingIntent = PendingIntent.getActivity(mContext,
                                0,
                                notificationIntent,
                                0);

                        Intent yesIntent = new Intent(mContext, DriverService.class);
                        yesIntent.setAction("Yes");
                        PendingIntent pYesIntent = PendingIntent.getService(mContext, 0,
                                yesIntent, 0);

                        Intent noIntent = new Intent(mContext, DriverService.class);
                        noIntent.setAction("No");
                        PendingIntent pNoIntent = PendingIntent.getService(mContext, 0,
                                noIntent, 0);

                        mNotification = new NotificationCompat.Builder(mContext)
                                .setContentTitle("You are online as " + sDriverName)
                                .setContentText("Currently awaiting a dispatch...")
                                .setSmallIcon(R.drawable.ic_directions_bike_white)
                                .setContentIntent(pendingIntent)
                                .setOngoing(true)
                                .addAction(android.R.drawable.ic_menu_add,
                                        "Yes", pYesIntent)
                                .addAction(android.R.drawable.ic_menu_delete, "No",
                                        pNoIntent).build();

                        mNotificationManager.notify(7, mNotification);
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
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }
    private void stopListening() {
    }
}