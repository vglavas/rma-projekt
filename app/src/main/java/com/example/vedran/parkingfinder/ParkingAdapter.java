package com.example.vedran.parkingfinder;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Vedran on 22.11.2017..
 */

public class ParkingAdapter extends BaseAdapter {
    private Context ctx;
    private ArrayList<Parking> parkinglist;

    public ParkingAdapter(Context ctx, ArrayList<Parking> parkinglist){
        super();
        this.ctx = ctx;
        this.parkinglist = parkinglist;
    }

    @Override
    public int getCount() { return this.parkinglist.size(); }

    @Override
    public Object getItem(int position) { return parkinglist.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView (int position, View convertView, ViewGroup parent){
        if(convertView == null){
            convertView = View.inflate(ctx, R.layout.parking_list_layout, null);
        }

        Parking current = parkinglist.get(position);

        TextView tvAddress = (TextView) convertView.findViewById(R.id.tvAddress);
        TextView tvDistance = (TextView) convertView.findViewById(R.id.tvDistance);

        tvAddress.setText(current.getAddress());
        tvDistance.setText(current.getDistance());

        return convertView;
    }


}
