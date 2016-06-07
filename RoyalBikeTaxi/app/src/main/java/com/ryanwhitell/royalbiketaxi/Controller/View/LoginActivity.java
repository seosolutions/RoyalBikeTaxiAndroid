package com.ryanwhitell.royalbiketaxi.Controller.View;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;
import com.ryanwhitell.royalbiketaxi.Controller.Model.Driver;

public class LoginActivity extends AppCompatActivity {

    // Driver array
    Driver[] mDrivers;

    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Database and driver array
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference databaseRefDrivers = database.getReference("Drivers");
        databaseRefDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int numberOfDrivers = (int) dataSnapshot.getChildrenCount();
                mDrivers = new Driver[numberOfDrivers];

                for (int i = 0; i < numberOfDrivers; i++) {
                    mDrivers[i] = dataSnapshot.child(Integer.toString(i)).getValue(Driver.class);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: Handle. Check the docks
            }
        });

    }

    /******* LOGIN LOGIC *******/
    public void login(View view) {

        // Initialize text fields
        EditText eName = (EditText) findViewById(R.id.text_view_name);
        EditText ePass = (EditText) findViewById(R.id.text_view_password);
        String name = eName.getText().toString();
        String pass = ePass.getText().toString();
        String number = getNumber(name);

        if (checkName(name)) {
            if (checkPassword(pass, name)) {
                Intent intent = new Intent(this, DriverActivity.class);
                intent.putExtra("name", name);
                intent.putExtra("number", number);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Invalid Password", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Invalid Name", Toast.LENGTH_LONG).show();
        }
    }

    public boolean checkName(String name) {
        for (Driver driver : mDrivers) {
            if (name.equals(driver.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean checkPassword(String pass, String name) {
        for (Driver driver : mDrivers) {
            if ((pass.equals(driver.getPassword())) && (name.equals(driver.getName()))) {
                return true;
            }
        }
        return false;
    }

    public String getNumber(String name) {
        for (Driver driver : mDrivers) {
            if (name.equals(driver.getName())) {
                return driver.getNumber();
            }
        }
        return "0000000000";
    }

}
