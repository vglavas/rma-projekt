package com.example.vedran.parkingfinder;

import android.Manifest;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.example.vedran.parkingfinder.SplashScreenActivity.userLocation;

public class MapActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 9000;
    private static final float DEFAULT_CAMERA_ZOOM = 15f;
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final String[] permissions = {FINE_LOCATION, COARSE_LOCATION};
    private ImageView ivCenterMarker;
    private ImageView ivShowInfo;
    private ImageView ivGetDirections;
    private ImageView ivOdrediste;
    private LatLng parkingLatLng;
    private GoogleMap mMap;
    private Marker parkingMarker;
    private Marker userMarker;
    private Parking parking;
    PolylineOptions lineOptions = null;
    Polyline polyline = null;
    private static boolean hasDirections = false;
    private GeofencingRequest geofencingRequest;
    private GoogleApiClient googleApiClient;
    private boolean isMonitoring = false;
    private PendingIntent pendingIntent;
    MarkerOptions userOpt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        ivCenterMarker = (ImageView) findViewById(R.id.ivCenterMarker);
        ivShowInfo = (ImageView) findViewById(R.id.ivShowInfo);
        ivGetDirections = (ImageView) findViewById(R.id.ivGetDirections);
        Intent parkingLocationIntent = getIntent();
        parking = (Parking) parkingLocationIntent.getSerializableExtra("parkingLocation");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        ivOdrediste = (ImageView) findViewById(R.id.ivOdrediste);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void startLocationMonitor(){
        Log.d(TAG, "startLocationMonitor: started location monitoring");
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(3000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        try{
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if(userMarker != null){
                        userMarker.remove();
                        userOpt = new MarkerOptions();
                        userOpt.position(new LatLng(location.getLatitude(), location.getLongitude()));
                        userOpt.title("Vaša lokacija");
                        userOpt.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_location_marker));
                        userMarker = mMap.addMarker(userOpt);
                        userLocation = location;
                        hasDirections = false;
                        Log.d(TAG, "onLocationChanged: Location changed");
                    } else {
                        Log.d(TAG, "onLocationChanged: user location is null");
                    }
                }
            });
        } catch (SecurityException e) {
            Log.d(TAG, "startLocationMonitor: " + e.getMessage());
        }
    }

    private void startGeofencing(){
        Log.d(TAG, "startGeofencing: ");
        pendingIntent = getGeofencePendingIntent();
        geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(getGeofence())
                .build();

        if(!googleApiClient.isConnected()){
            Log.d(TAG, "startGeofencing: Google API client not connected");
        } else {
            try{
                LocationServices.GeofencingApi.addGeofences(googleApiClient, geofencingRequest, pendingIntent).setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if(status.isSuccess()){
                            Log.d(TAG, "onResult: Successfully Geofencing connected");
                        } else {
                            Log.d(TAG, "onResult: Failed to add Geofencing " + status.getStatus());
                        }
                    }
                });
            } catch (SecurityException e) {
                Log.d(TAG, "startGeofencing: error " + e.getMessage());
            }
        }
        isMonitoring = true;
    }

    @NonNull
    private Geofence getGeofence(){
        return new Geofence.Builder()
                .setRequestId(parking.getAddress())
                .setExpirationDuration(30 * 60 * 1000)  //30 minuta
                .setCircularRegion(parking.getLatitude(), parking.getLongitude(), 100.00f)
                .setNotificationResponsiveness(1000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build();
    }

    private PendingIntent getGeofencePendingIntent(){
        if(pendingIntent != null){
            return pendingIntent;
        }
        Intent intent = new Intent(this, GeofenceRegistrationService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void stopGeoFencing(){
        pendingIntent = getGeofencePendingIntent();
        LocationServices.GeofencingApi.removeGeofences(googleApiClient, pendingIntent)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if(status.isSuccess()){
                            Log.d(TAG, "onResult: Stop Geofencing");
                        } else {
                            Log.d(TAG, "onResult: Error! Not stopping geofencing");
                        }
                    }
                });
        isMonitoring = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasDirections = false;
        int response = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MapActivity.this);
        if(response != ConnectionResult.SUCCESS){
            Log.d(TAG, "onResume: Google Play service not available");
            GoogleApiAvailability.getInstance().getErrorDialog(MapActivity.this, response, 1).show();
        } else {
            Log.d(TAG, "onResume: Google Play services available");
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        googleApiClient.reconnect();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        MarkerOptions options;
        mMap = googleMap;

        //  Postavi marker na korisnikovoj lokaciji
        userOpt = new MarkerOptions()
                .position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()))
                .title("Vaša lokacija")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_location_marker));
        userMarker = mMap.addMarker(userOpt);

        //  Postavi marker na lokaciji parkirališta
        parkingLatLng = new LatLng(parking.getLatitude(), parking.getLongitude());
        options = new MarkerOptions()
                .position(parkingLatLng)
                .title("Parking")
                .snippet(parking.getAddress())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_parking_location_marker));
        parkingMarker = mMap.addMarker(options);
        moveCamera(parkingLatLng, DEFAULT_CAMERA_ZOOM);

        //  Dohvati rutu, ako ne postoji, kada korinsik klikne na gumb za dohvaćanje rute
        ivGetDirections.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    //  Ako je korisnik prvi put kliknuo na gumb za preuzimanje rute, preuzmi podatke
                    if(!hasDirections){
                        if(polyline != null && polyline.isVisible()){
                            polyline.setVisible(false);
                        }
                        String url = getDirectionsUrl(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()), parkingLatLng);
                        DownloadTask downloadTask = new DownloadTask();
                        downloadTask.execute(url);
                    } else {
                        // Ako je ruta nacrtana ukloni ju, u suprotnom ju prikaži
                        if(polyline.isVisible()){
                            polyline.setVisible(false);
                        } else {
                            polyline.setVisible(true);
                        }
                    }
                } catch (NullPointerException e) {
                    Log.d(TAG, "onClick: NullPointerException: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        //  Centriraj kameru na marker za parkiralište i prikaži podatke o parkiralištu
        ivShowInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    if(parkingMarker.isInfoWindowShown()){
                        parkingMarker.hideInfoWindow();
                    } else {
                        parkingMarker.showInfoWindow();
                        moveCamera(parkingLatLng, DEFAULT_CAMERA_ZOOM);
                    }
                } catch (NullPointerException e) {
                    Log.d(TAG, "onClick: NullPointerException: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        //  Centriraj kameru na marker za parkiralište
        ivCenterMarker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveCamera(parkingLatLng, DEFAULT_CAMERA_ZOOM);
            }
        });

        ivOdrediste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(new ContextThemeWrapper(MapActivity.this, R.style.CustomAlertDialog));
                dialog.setMessage("Primit ćete obavjest kada budete u krugu od 100m od parkirališta '" + parking.getAddress() + "'")
                        .setTitle("Postavi odredište")
                        .setPositiveButton("Postavi", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startGeofencing();
                            }
                        })
                        .setNegativeButton("Odustani", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                dialog.create().show();
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle){
        Log.d(TAG, "onConnected: Google API client connected");
    }

    @Override
    public void onConnectionSuspended(int i){
        Log.d(TAG, "onConnectionSuspended: Google Connection Suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult){
        isMonitoring = false;
        Log.d(TAG, "onConnectionFailed: Connection Failed: " + connectionResult.getErrorMessage());
    }

    //  Funkcija za pomjeranje kamere na marker
    private void moveCamera(LatLng latLng, float zoom) {
        Log.d(TAG, "moveCamera: Moving the camera to: " + latLng.latitude + ", " + latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
    }

    //  Funkcija za konstrukciju URL-a s kojega će se preuzeti podaci za crtanje rute
    //  Parametri URL-a: origin - lokacija korisnika
    //                   destination - lokacija parkirališta
    //                   mode - postavljen na vozilo
    //                   units - prikaži podatke u metričkim jedinicama
    //                   key - API ključ aplikacije
    //                   output - zatraži JSON od servera
    private String getDirectionsUrl (LatLng origin, LatLng dest){
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String sensor = "sensor=false";
        String mode = "mode=driving";
        String units = "units=metric";
        String key = "key=" + getString(R.string.google_maps_API_KEY);
        String parameters = str_origin + "&" +str_dest + "&" + sensor + "&" + mode + "&" + units + "&" + key;
        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }

    //  Funkcija za preuzimanje podataka za crtanje rute
    private String downloadUrl (String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while((line = br.readLine()) != null){
                sb.append(line);
            }
            data = sb.toString();
            br.close();
            iStream.close();
            urlConnection.disconnect();
        } catch (Exception e) {
            Log.d(TAG, "downloadUrl: Exception: " + e.getMessage());
            e.printStackTrace();
        }
        return data;
    }

    //  Asinkroni zadatak za preuzimanje podatak za crtanje rute
    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... url) {
            String data = "";
            try{
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d(TAG, "doInBackground: Exception: " + e.getMessage());
                e.printStackTrace();
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);
        }
    }

    //  Asinkroni zadatak za parsiranje preuzetih podatak za crtanje rute u JSON formatu
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> results) {
            ArrayList points = null;
            MarkerOptions markerOptions = new MarkerOptions();

            try{
                for(int i = 0; i < results.size(); i++){
                    points = new ArrayList();
                    lineOptions = new PolylineOptions();
                    List<HashMap<String, String>> path = results.get(i);
                    for(int j = 0; j < path.size(); j++){
                        HashMap<String, String> point = path.get(j);
                        double lat = Double.parseDouble(point.get("lat"));
                        double lng = Double.parseDouble(point.get("lng"));
                        LatLng position = new LatLng(lat, lng);
                        points.add(position);
                    }
                    lineOptions.addAll(points);
                    lineOptions.width(12);
                    lineOptions.color(getResources().getColor(R.color.colorPrimary));
                    lineOptions.geodesic(true);
                }
                polyline = mMap.addPolyline(lineOptions);
                hasDirections = true;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MapActivity.this, "Došlo je do greške", Toast.LENGTH_LONG).show();
            }

        }

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;
            try{
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();
                routes = parser.parse(jObject);
            } catch (Exception e) {
                Log.d(TAG, "doInBackground: Exception: " + e.getMessage());
                e.printStackTrace();
            }
            return routes;
        }
    }

}
