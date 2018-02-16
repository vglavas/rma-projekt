package com.example.vedran.parkingfinder;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

/**
 * Created by Vedran on 16.2.2018..
 */

public class GeofenceRegistrationService extends IntentService {

    private static final String TAG = "GeofenceService";

    public GeofenceRegistrationService(){
        super("GeofenceService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if(geofencingEvent.hasError()){
            Log.d(TAG, "onHandleIntent: error" + geofencingEvent.getErrorCode());
        } else {
            int transition = geofencingEvent.getGeofenceTransition();
            List<Geofence> geofences = geofencingEvent.getTriggeringGeofences();
            Geofence geofence = geofences.get(0);
            if(transition == Geofence.GEOFENCE_TRANSITION_ENTER){
                Log.d(TAG, "onHandleIntent: You have arrived to your destination");

                NotificationManager nm = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
                Notification n = builder.setContentTitle("Stigli ste na odredište!")
                        .setContentText(geofence.getRequestId())
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS)
                        .build();
                geofences.clear();
                nm.notify(1,n);
            }
        }
    }
}
