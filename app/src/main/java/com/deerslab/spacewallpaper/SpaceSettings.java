package com.deerslab.spacewallpaper;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by keeper on 03.04.2016.
 */
public class SpaceSettings extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);
    }

}
