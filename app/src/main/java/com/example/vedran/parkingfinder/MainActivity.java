package com.example.vedran.parkingfinder;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Collections;
import java.util.Comparator;

import static com.example.vedran.parkingfinder.SplashScreenActivity.myParkings;
import static com.example.vedran.parkingfinder.SplashScreenActivity.userLocation;

public class MainActivity extends AppCompatActivity implements
        AdapterView.OnItemClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_REQUEST_PRIORITY = LocationRequest.PRIORITY_HIGH_ACCURACY;
    private static final long LOCATION_REQUEST_INTERVAL = 10 * 1000;
    private static final long LOCATION_REQUEST_FASTEST_INTERVAL = 2 * 1000;
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final String[] permissions = {FINE_LOCATION, COARSE_LOCATION};
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 9000;
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9002;
    private static boolean locationPermissionsGranted = false;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    protected ListView lvMain;
    protected ImageView ivRefresh;
    ParkingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        ivRefresh = (ImageView) findViewById(R.id.ivRefresh);
        lvMain = (ListView) findViewById(R.id.listViewMain);
        adapter = new ParkingAdapter(this, myParkings);
        lvMain.setAdapter(adapter);
        lvMain.setOnItemClickListener(this);

        //  Kreiraj Google API klijenta
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        //  Kreiraj zahtjev za lokaciju sa postavljenim kriterijima
        mLocationRequest = LocationRequest.create()
                .setPriority(LOCATION_REQUEST_PRIORITY)
                .setInterval(LOCATION_REQUEST_INTERVAL)
                .setFastestInterval(LOCATION_REQUEST_FASTEST_INTERVAL);

        //  Osvježi listu parkinga kada korisnik klikne na ikonu za osvježavanje
        ivRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RefreshData().execute();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        //  Ako je Google API klijent spojen ukloni obnavljanje lokacije i odspoji Google API klijenta
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //  Spoji Google API klijenta
        mGoogleApiClient.connect();
    }

    //  Kada korisnik klikne na određeno parkiralište u listi prikaži ga na mapi u novoj aktivnosti
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Parking clicked = (Parking) adapter.getItem(position);
        Intent i = new Intent(MainActivity.this, MapActivity.class);
        i.putExtra("parkingLocation", clicked);
        startActivity(i);
    }

    //  Asinkroni zadatak za osvježavanje podataka o parkiralištima
    private class RefreshData extends AsyncTask<Void, Void, Void>{

        ProgressDialog pd;

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(MainActivity.this, R.style.AppCompatAlertDialogStyle);
            pd.setMessage(getResources().getString(R.string.refreshing));
            pd.setCancelable(false);
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.show();
            getLocationPermission();
            //noinspection MissingPermission
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, MainActivity.this);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            adapter = new ParkingAdapter(MainActivity.this, myParkings);
            adapter.notifyDataSetChanged();
            Collections.sort(myParkings, new Comparator<Parking>() {
                @Override
                public int compare(Parking o1, Parking o2) {
                    Float d1 = o1.getFloatDistance();
                    Float d2 = o2.getFloatDistance();

                    return d1.compareTo(d2);
                }
            });

            lvMain.setAdapter(adapter);

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pd.dismiss();
                }
            }, 1000);
        }

        @Override
        protected Void doInBackground(Void... params) {
            Location parkingLocation = new Location("");

            for(int i = 0; i < adapter.getCount(); i++){
                parkingLocation.setLatitude(myParkings.get(i).getLatitude());
                parkingLocation.setLongitude(myParkings.get(i).getLongitude());
                myParkings.get(i).setDistance(userLocation.distanceTo(parkingLocation));
            }

            return null;
        }
    }

    private void getLocationPermission() {
        Log.d(TAG, "getLocationPermission: Getting location permissions");

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationPermissionsGranted = true;
            } else {
                ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: Called");
        locationPermissionsGranted = false;

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            locationPermissionsGranted = false;
                            Log.d(TAG, "onRequestPermissionsResult: Permission failed");
                            return;
                        }
                    }
                    Log.d(TAG, "onRequestPermissionsResult: Permission granted");
                    locationPermissionsGranted = true;
                }
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected: Location service connected.");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            getLocationPermission();
            return;
        }
        userLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if(userLocation == null){
            Log.d(TAG, "onConnected: Location = null, requesting location update.");
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: Location service suspended. Please reconnect.");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if(connectionResult.hasResolution()){
            try{
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "onConnectionFailed: Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged: Location changed.");
        userLocation = location;
    }
}
