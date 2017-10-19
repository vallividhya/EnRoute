package com.codepath.enroute.activities;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.codepath.enroute.Manifest;
import com.codepath.enroute.R;
import com.codepath.enroute.connection.YelpClient;
import com.codepath.enroute.models.YelpBusiness;
import com.codepath.enroute.util.MapUtil;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PolylineOptions;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.parceler.Parcels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

import static android.media.CamcorderProfile.get;
import static com.codepath.enroute.util.MapUtil.addMarker;
import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;


/*
* Activity with the map view showing route between current location and destination
*
* */
@RuntimePermissions
public class PlacesActivity extends AppCompatActivity implements GoogleMap.OnMarkerClickListener {

    public static final String KEY_ADDRESS_INVALID = "KEY_ADDRESS_INVALID";
    public static final int RESPONSE_CODE = 400 ;
    private long UPDATE_INTERVAL = 60000 * 3;  /* 60 secs  * 3 */
    private long FASTEST_INTERVAL = 5000; /* 5 secs */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final static String KEY_LOCATION = "location";
    private final static String KEY_POINTS_OF_INTEREST = "points_of_interest";

    private SupportMapFragment mapFragment;
    private GoogleMap map;
    private LocationRequest mLocationRequest;
    Location mCurrentLocation;
    LatLng mCurrentLatLng;

    private String origin= "";
    private String destination = "";

    private JSONObject directionsJson;

    //private LatLng testLatLng = new LatLng(37.37, -122.03); // TODO: Get location from previous activity through intent
    private List<LatLng> directionPoints;
    //private LatLng destinationLatLng;

    //This should contain a list of Points Of Interest;
    private Map<LatLng,YelpBusiness> mPointsOfInterest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_places);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        try {
            directionsJson = new JSONObject(bundle.getString(SearchActivity.KEY_RESPONSE_JSON));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        directionPoints = MapUtil.decodePolyLine(bundle.getString(SearchActivity.KEY_DIRECTIONS));

        if (savedInstanceState != null && savedInstanceState.keySet().contains(KEY_LOCATION)) {
            // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
            // is not null.
            mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCurrentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        }

        if(savedInstanceState!= null && savedInstanceState.keySet().contains(KEY_POINTS_OF_INTEREST)){
            mPointsOfInterest = savedInstanceState.getParcelable(KEY_POINTS_OF_INTEREST);
        }else{
            mPointsOfInterest = new HashMap<>();
        }
        // Get location here and get directions:

        mapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap map) {
                    loadMap(map);
                }
            });
        } else {
            Log.e(this.getClass().toString(), "Error - Map Fragment was null!!");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_places, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // perform query here
                searchView.clearFocus();
                Intent i = new Intent(getApplicationContext(), DetailActivity.class);
 //               i.putExtra("q", query);
                startActivity(i);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display the connection status

        if (mCurrentLocation != null) {
            Log.d(this.getClass().toString(), "GPS location was found!");
            mCurrentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
            zoomToLocation();
        } else {
            Log.e(this.getClass().toString(), "Current location was null, enable GPS on emulator!");
        }
        PlacesActivityPermissionsDispatcher.startLocationUpdatesWithCheck(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_LOCATION, mCurrentLocation);
        //outState.putParcelable(KEY_POINTS_OF_INTEREST,mPointsOfInterest);
        super.onSaveInstanceState(outState);
    }

    protected void loadMap(GoogleMap googleMap) {
        map = googleMap;
        if (map != null) {
            // Map is ready
            map.setMyLocationEnabled(true);
            mCurrentLatLng = directionPoints.get(0);
            Log.d(this.getClass().toString(), "Map Fragment was loaded properly!");
            mCurrentLocation = new Location("");
            mCurrentLocation.setLatitude(mCurrentLatLng.latitude);
            mCurrentLocation.setLongitude(mCurrentLatLng.longitude);
            onLocationChanged(mCurrentLocation);
            //PlacesActivityPermissionsDispatcher.getMyLocationWithCheck(this);
            PlacesActivityPermissionsDispatcher.startLocationUpdatesWithCheck(this);

            map.setOnMarkerClickListener(this);
        } else {
            Log.e(this.getClass().toString(), "Error - Map was null!!");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PlacesActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }


    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    protected void startLocationUpdates() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        //mLocationRequest.setInterval(UPDATE_INTERVAL);
        // mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);
        //noinspection MissingPermission
        getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        onLocationChanged(locationResult.getLastLocation());
                    }
                },
                Looper.myLooper());
    }

    public void onLocationChanged(Location location) {
        // GPS may be turned off
        if (location == null) {
            return;
        }
        BitmapDescriptor defaultMarker =
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
        mCurrentLocation = location;
        mCurrentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        Marker fromMarker = addMarker(map, mCurrentLatLng, "Current Location", "", defaultMarker);
        zoomToLocation();
        drawDirections();
        getYelpBusinesses(directionsJson);
    }


    private void getYelpBusinesses(JSONObject response) {
        //TESTME Jim
        List<LatLng> googlePoints = null;
        try {
            googlePoints = MapUtil.getLatLngFromOverView(response, 1609);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //The following is an example how to use YelpApi.
        YelpClient client = YelpClient.getInstance();
        RequestParams params = new RequestParams();
        params.put("term", "food");
        params.put("radius", 1000);
        for (int i = 0; i < googlePoints.size(); i++) {

            params.put("latitude", googlePoints.get(i).latitude);
            params.put("longitude", googlePoints.get(i).longitude);
            client.getSearchResult(params, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    super.onSuccess(statusCode, headers, response);

                    try {
                        JSONArray yelpBusinesses = response.getJSONArray("businesses");
                        BitmapDescriptor icon =
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
                        for (int i = 0; i < yelpBusinesses.length(); i++) {
                            YelpBusiness aYelpBusiness = YelpBusiness.fromJson(yelpBusinesses.getJSONObject(i));

                            LatLng newLatLng = new LatLng(aYelpBusiness.getLatitude(),aYelpBusiness.getLongitude());

                            //Skip if there is a duplicate.
                            if (!mPointsOfInterest.containsKey(newLatLng)){
                                mPointsOfInterest.put(newLatLng,aYelpBusiness);
                                Marker markerAdded = MapUtil.addMarker(map, newLatLng, aYelpBusiness.getName(), "No Description yet", icon);
                                markerAdded.setTag(aYelpBusiness);
                        }}
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                    super.onSuccess(statusCode, headers, response);
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseString) {
                    super.onSuccess(statusCode, headers, responseString);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                    super.onFailure(statusCode, headers, responseString, throwable);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    Log.e(this.getClass().toString(), "Error fetching Yelp businesses: " + errorResponse.toString());
                    //super.onFailure(statusCode, headers, throwable, errorResponse);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                    super.onFailure(statusCode, headers, throwable, errorResponse);
                }
            });

        }
    }

    private void handleInvalidAddress() {
        Intent in = new Intent();
        in.putExtra(KEY_ADDRESS_INVALID, true);
        setResult(RESPONSE_CODE, in);
        finish();
    }

    private void getPointsOfInterest() {
        YelpClient yelpClient = YelpClient.getInstance();
//        RequestParams params = new RequestParams();
//        params.put("latitude", mCurrentLocation.getLatitude());
//        params.put("longitude", mCurrentLocation.getLongitude());
        BitmapDescriptor icon =
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
        List<YelpBusiness> pointsOfInterestList = yelpClient.getPointsOfInterestEnRoute();
        for (YelpBusiness point : pointsOfInterestList) {
            Marker markerAdded = addMarker(map, point.getLatLng(), point.getName(), point.getDescription(), icon);
            markerAdded.setTag(point);
        }

    }

    private void drawDirections() {
        Log.d("DEBUG", "Drawing directions");
        PolylineOptions lineOptions = new PolylineOptions();
        for (LatLng latLng : directionPoints) {
            lineOptions.add(latLng);
        }
        map.addPolyline(lineOptions);



        // Add marker for destination
        BitmapDescriptor defaultMarker =
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
        Marker toMarker = addMarker(map, directionPoints.get(directionPoints.size() - 1), "", "", defaultMarker);





    }

    //The return value is distance in Miles.
    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        //dist = dist * 1.609344;

        return (dist);
    }

    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private static double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }

    /*
    * Click listener for tool bar menu item to switch to list view
    *
    * */
    public void onClickSwitchView(MenuItem item) {
        Log.d(this.getClass().toString(), "Switching to List view");
        Intent intent = new Intent(getApplicationContext(), ListActivity.class);
        startActivity(intent);
    }

    private void zoomToLocation() {

        double distance = distance(directionPoints.get(0).latitude,
                directionPoints.get(0).longitude,
                directionPoints.get(directionPoints.size()-1).latitude,
                directionPoints.get(directionPoints.size()-1).longitude);

        int zoomLevel = 15;

        //ZoomLevel
        //1 world
        //5 continent
        //10 city
        //15 street
        //20 buildings

        //1000,500,200,100,50,20,10,5,2,


        if (distance<0.2){
            zoomLevel = 15;
        }else if (distance<0.5){
            zoomLevel = 14;
        }else if (distance<1){
            zoomLevel = 13;
        }else if (distance<2){
            zoomLevel = 12;
        }else if (distance<5){
            zoomLevel = 11;
        }else if (distance<10){
            zoomLevel = 10;
        }else if (distance<20){
            zoomLevel = 9;
        }else if (distance<50){
            zoomLevel = 8;
        }else if (distance<100){
            zoomLevel = 7;
        }else if (distance<200){
            zoomLevel = 6;
        }else if (distance< 500) {
            zoomLevel = 5;
        }else if (distance< 1000){
            zoomLevel =  4;
        }else if (distance< 2000){
            zoomLevel = 3;
        }else if (distance<5000){
            zoomLevel = 2;  
        }else if (distance<10000){
            zoomLevel =1;
        }
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(mCurrentLatLng, zoomLevel);
        map.animateCamera(cameraUpdate);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {

        YelpBusiness aYelpBusiness = (YelpBusiness) marker.getTag();
        if (aYelpBusiness == null) {
            return false;
        }else{
            //invoke detail ;


            Intent detailActivity = new Intent(this, DetailActivity.class);
            detailActivity.putExtra("YELP_BUSINESS",Parcels.wrap(aYelpBusiness));
            startActivity(detailActivity);
            return true;
        }
    }

}
