package com.deerslab.spacewallpaper;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by keeper on 03.04.2016.
 */
public class SpaceSettings extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addPreferencesFromResource(R.xml.main_preferences);
        } else {
            addPreferencesFromResource(R.xml.main_preferences_old);
        }
    }

}
