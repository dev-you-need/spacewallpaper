package com.deerslab.spacewallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

public class MainActivity extends Activity  {

    private final String TAG = this.getClass().getSimpleName();

    private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
/*
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new Prefs1Fragment()).commit();
*/
        try {
            AnalyticsTrackers.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mTracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
            mTracker.setScreenName(TAG);
            mTracker.send(new HitBuilders.EventBuilder().setAction("start").build());
        } catch (Exception e) {
            e.printStackTrace();
        }


        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, SpaceWallPaperService.class));
        startActivity(intent);
        finish();


    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    public static class Prefs1Fragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.main_preferences);
        }
    }
}
