package com.deerslab.spacewallpaper;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Created by keeper on 04.04.2016.
 */
public interface DSCOVRdata {

    @GET("/api/images.php")
    Call<List<Photo>> getPhotos(/*@Query("date") String date*/);

    static class Photo {
        String image;
        String caption;
        String coords;
        String date;
    }
}
