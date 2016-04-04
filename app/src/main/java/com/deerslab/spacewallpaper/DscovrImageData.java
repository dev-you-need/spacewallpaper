package com.deerslab.spacewallpaper;

import java.io.Serializable;

/**
 * Created by keeper on 02.04.2016.
 */
public class DscovrImageData implements Serializable {

    String image = new String();
    String caption = new String();
    Coordinates coords = new Coordinates();
    String date = new String();

    private class Coordinates implements Serializable {

        Centroid_coordinates centroid_coordinates = new Centroid_coordinates();
        Dscovr_j2000_position dscovr_j2000_position = new Dscovr_j2000_position();
        Lunar_j2000_position lunar_j2000_position = new Lunar_j2000_position();
        Sun_j2000_position sun_j2000_position = new Sun_j2000_position();
        Attitude_quaternions attitude_quaternions = new Attitude_quaternions();

        private class Centroid_coordinates{
            float lat;
            float lon;
        }

        private class Dscovr_j2000_position{
            float x;
            float y;
            float z;
        }

        private class Lunar_j2000_position{
            float x;
            float y;
            float z;
        }

        private class Sun_j2000_position{
            float x;
            float y;
            float z;
        }

        private class Attitude_quaternions{
            float q0;
            float q1;
            float q2;
            float q3;
        }

    }
}
