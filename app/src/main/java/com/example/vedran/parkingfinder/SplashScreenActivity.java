package com.example.vedran.parkingfinder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class SplashScreenActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "SplashScreenActivity";
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final String[] permissions = {FINE_LOCATION, COARSE_LOCATION};
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 9000;
    private static final int ERROR_DIALOG_REQUEST = 9001;
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9002;
    private static final int LOCATION_REQUEST_PRIORITY = LocationRequest.PRIORITY_HIGH_ACCURACY;
    private static final long LOCATION_REQUEST_INTERVAL = 3 * 60 * 1000;    //3 minute
    private static final long LOCATION_REQUEST_FASTEST_INTERVAL = 2 * 60 * 1000;    //2 minute
    private static boolean locationPermissionsGranted = false;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    public static Location userLocation;
    public static ArrayList<Parking> myParkings;
    private ParkingAdapter adapter;
    private FetchData fetchData = new FetchData();
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        adapter = new ParkingAdapter(this, myParkings);

        //  Provjeri jeli omogućeno dohvaćanje lokacije
        locationEnabled();

        //  Provjeri je li korisnik omogućio korištenje lokacijskih usluga
        getLocationPermission();

        //  Testiraj Google Play usluge
        //  Ako postoji greška prikaži upozorenje
        if (!isServicesOK()) {
            tvStatus.setTextColor(Color.RED);
            tvStatus.setText(getResources().getString(R.string.servicesError));
        }

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
        //  Provjeri je li korisnik omogućio korištenje lokacijskih usluga
        locationEnabled();
        //  Kreiraj novu listu parkinga kako bi se spriječilo dupliciranje podataka
        //  Spoji Google API klijenta
        myParkings = new ArrayList<Parking>();
        mGoogleApiClient.connect();
    }

    //  Funkcija testira Google Play uslugu
    //  Ako je sve u redu, nastavi sa radom
    //  Ako se pojavila greška, ali se može otkloniti, prikaži skočni prozor sa porukom
    //  Ako se pojavila greška koja se ne može otkloniti, prikaži Toast poruku i zaustavi izvođenje aplikacije
    public boolean isServicesOK() {
        Log.d(TAG, "isServicesOK: Checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(SplashScreenActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            Log.d(TAG, "isServicesOK: Google play services is working");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            Log.d(TAG, "isServicesOK: Error occured but we can fix it");
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(SplashScreenActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
        } else {
            Toast.makeText(this, "There is a problem with Google Play Services", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    //  Funkcija koja provjerava je li korisnik omogućio korištenje lokacijskih usluga
    //  Ako lokacijske usluge nisu omogućene, zatraži od korisnika da ih omogući
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

    //  Funkcija povratnog poziva koja se poziva nakon što korisnik omogući (ili onemogući) korištenje lokacijskih usluga
    //  Rezultat funkcije je promjena stanja boolean varijable 'locationPermissionsGranted' koja se koristi za provjeru jesu li lokacijske usluge omogućene
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

    //  Funkcija povratnog poziva koja se poziva nakon što se uspostavi veza za lokacijske usluge
    //  Zatraži od korisnika da omogući lokacijske usluge ukoliko nisu omogućene, u suprotnom dohvati GPS lokaciju korisnika
    //  Na kraju pokreni FetchData() za preuzimanje podataka o parkiralištima
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
        else {
            fetchData.execute();
            Log.d(TAG, "onConnected: Got the user location, getting the parking list.");
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

    //  Funkcija povratnog poziva koja se poziva kada se promijeni GPS lokacija korisnika
    //  Sprema trenutnu lokaciju korisnika u varijablu 'userLocation'
    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged: Location changed.");
        userLocation = location;
        fetchData.execute();
    }

    //  Asinkroni zadatak za rad u pozadini
    //  Preuzima podatke o parkiralištima u JSON formatu
    private class FetchData extends AsyncTask<Void, Void, Void> {
        //private static final String URL_ADDRESS = "http://10.0.2.2:8000/parkings";
        private static final String URL_ADDRESS = "https://rmaparkingfinder.000webhostapp.com/parkings";
        private static final int CONNECTION_TIMEOUT = 10 * 1000;
        private String ADDRESS;
        private Double LATITUDE;
        private Double LONGITUDE;
        private Float DISTANCE;
        private String data = "";
        private String line = "";
        private boolean hasError = false;
        private String errorMsg = "";
        private JSONArray JA = null;
        private JSONObject JO = null;

        @Override
        protected void onPreExecute() {
            tvStatus.setText(getResources().getString(R.string.loading));
        }

        //  Preuzmi podatke o parkiralištima
        @Override
        protected Void doInBackground(Void... params) {
            Location parkingLocation = new Location("");
            try {
                URL url = new URL(URL_ADDRESS);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                InputStream inputStream = connection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                while(line != null){
                    line = bufferedReader.readLine();
                    data = data + line;
                }

                JA = new JSONArray(data);
                for(int i=0; i<JA.length(); i++) {
                    JO = (JSONObject) JA.get(i);
                    ADDRESS = JO.getString("address");
                    LATITUDE = JO.getDouble("latitude");
                    LONGITUDE = JO.getDouble("longitude");
                    parkingLocation.setLatitude(LATITUDE);
                    parkingLocation.setLongitude(LONGITUDE);
                    DISTANCE = userLocation.distanceTo(parkingLocation);
                    myParkings.add(new Parking(ADDRESS, LATITUDE, LONGITUDE, DISTANCE));
                }
                inputStream.close();
                connection.disconnect();
            } catch (MalformedURLException e) {
                Log.d(TAG, "doInBackground: MailformedURLException thrown: " + e.getMessage());
                hasError = true;
                errorMsg = getResources().getString(R.string.malformedURLException);
                e.printStackTrace();
            } catch (IOException e) {
                Log.d(TAG, "doInBackground: IOException thrown: " + e.getMessage());
                hasError = true;
                errorMsg = getResources().getString(R.string.ioException);
                e.printStackTrace();
            } catch (JSONException e) {
                Log.d(TAG, "doInBackground: JSONException thrown: " + e.getMessage());
                hasError = true;
                errorMsg = getResources().getString(R.string.jsonException);
                e.printStackTrace();
            } catch (NullPointerException e) {
                Log.d(TAG, "doInBackground: NullPointerExceptionthrown: " + e.getMessage());
                hasError = true;
                errorMsg = getResources().getString(R.string.nullPointerException);
                e.printStackTrace();
            } catch (Exception e) {
                Log.d(TAG, "doInBackground: Exception thrown: " + e.getMessage());
                hasError = true;
                errorMsg = getResources().getString(R.string.exception);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            //  Provjei je li došlo do problema pri preuzimanju podataka
            //  Ako je, prikaži poruku greške i gumb za ponovno pokretanje
            if(hasError){
                tvStatus.setTextColor(Color.RED);
                tvStatus.setText(errorMsg);
                Button btnRetry = (Button) findViewById(R.id.btnRetry);
                btnRetry.setVisibility(View.VISIBLE);

                btnRetry.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Activity activity = SplashScreenActivity.this;
                        activity.recreate();
                    }
                });
            } else {

                //  Poredaj parkinge po udaljenosti od korisnika od najbližeg do najudaljenijeg
                adapter.notifyDataSetChanged();
                Collections.sort(myParkings, new Comparator<Parking>() {
                    @Override
                    public int compare(Parking o1, Parking o2) {
                        Float d1 = o1.getFloatDistance();
                        Float d2 = o2.getFloatDistance();

                        return d1.compareTo(d2);
                    }
                });

                //  Pokreni novu aktivnosti za prikaz svih parkirališta
                Intent i = new Intent(SplashScreenActivity.this, MainActivity.class);
                startActivity(i);
                finish();
            }
        }
    }

    public void locationEnabled(){
        LocationManager lm = (LocationManager)SplashScreenActivity.this.getSystemService(this.LOCATION_SERVICE);
        boolean gps_enabled = false;
        boolean network_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {}

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception ex) {}

        if(!gps_enabled && !network_enabled) {
            // notify user
            AlertDialog.Builder dialog = new AlertDialog.Builder(new ContextThemeWrapper(this,R.style.CustomAlertDialog));
            dialog.setMessage(this.getResources().getString(R.string.dialogMessage));
            dialog.setPositiveButton(this.getResources().getString(R.string.settings), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(myIntent);
                }
            });
            dialog.setNegativeButton(this.getResources().getString(R.string.close), new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    tvStatus.setText(getResources().getString(R.string.locationsError));
                }
            });
            dialog.show();
        }
    }
}
