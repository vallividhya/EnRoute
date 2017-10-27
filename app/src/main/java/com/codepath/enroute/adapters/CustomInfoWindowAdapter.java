package com.codepath.enroute.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.codepath.enroute.R;
import com.codepath.enroute.models.YelpBusiness;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/**
 * Created by vidhya on 10/25/17.
 */

public class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

    private Context mContext;
    YelpBusiness mYelpBusiness;
    private Map<String, Integer> mCategoryIconMap;
    String mCategory;

    public CustomInfoWindowAdapter(Context context, String category) {
        mContext = context;
        mCategoryIconMap =  YelpBusiness.loadCategoryIcons();
        mCategory = category;
    }


    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        mYelpBusiness = (YelpBusiness) marker.getTag();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.marker_info_contents, null);
        TextView tvName = (TextView) view.findViewById(R.id.tvPlaceName);

        ImageView ivCategory = (ImageView) view.findViewById(R.id.ivCategory);


        if (mYelpBusiness != null) {
            tvName.setText(mYelpBusiness.getName());

            RatingBar rbReviews = (RatingBar) view.findViewById(R.id.rbPlaceRating);
            rbReviews.setRating((float) mYelpBusiness.getRating());

            TextView tvClosedNow = (TextView) view.findViewById(R.id.tvClosedNow);
            tvClosedNow.setText(mYelpBusiness.isOpenNow() ? "Open" : "Closed Now");

            TextView tvDetourTime = (TextView) view.findViewById(R.id.tvDetourTime);
            DecimalFormat df = new DecimalFormat("##.#");

            String detourStr = "+ " + df.format(mYelpBusiness.getDistance()) + " miles";
            tvDetourTime.setText(detourStr);
            if (mCategory.equals("gas")) {
                Glide.with(mContext).load(R.drawable.ic_placeholder_gas).placeholder(R.drawable.ic_placeholder_gas).override(50, 50).into(ivCategory);
            } else if (mCategory.equals("coffee")) {
                Glide.with(mContext).load(R.drawable.ic_coffee_placeholder).placeholder(R.drawable.ic_coffee_placeholder).override(50, 50).into(ivCategory);
            } else {
                Glide.with(mContext).load(getImageForCateory(mYelpBusiness)).placeholder(R.drawable.ic_food_placeholder).override(50, 50).into(ivCategory);
            }

        }
        return view;
    }

    private int getImageForCateory(YelpBusiness yelpBusiness) {
        int imageVal = mCategoryIconMap.get("placeholder_food");
        List<String> categories = yelpBusiness.getCategoriesList();
        for (String category: categories) {
            if (category != null) {
                Log.d("vvv: category", category.toLowerCase());
                if (mCategoryIconMap.containsKey(category.toLowerCase())) {
                    imageVal = mCategoryIconMap.get(category.toLowerCase());
                    break;
                }
            }
        }
    return imageVal;
    }
}
