package com.example.vedran.parkingfinder;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

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
        OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final float DEFAULT_CAMERA_ZOOM = 15f;
    private ImageView ivCenterMarker;
    private ImageView ivShowInfo;
    private ImageView ivGetDirections;
    private Button btnOdrediste;
    private LatLng parkingLatLng;
    private LatLng userLatLng;
    private GoogleMap mMap;
    private Marker parkingMarker;
    private Marker userMarker;
    private Parking parking;
    PolylineOptions lineOptions = null;
    Polyline polyline = null;
    private static boolean hasDirections = false;

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
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasDirections = false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        MarkerOptions options;
        MarkerOptions userOpt;
        mMap = googleMap;

        //  Postavi marker na korisnikovoj lokaciji
        userLatLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
        userOpt = new MarkerOptions()
                .position(userLatLng)
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
                        String url = getDirectionsUrl(userLatLng, parkingLatLng);
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
