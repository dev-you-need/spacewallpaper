package com.deerslab.spacewallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


/**
 * Created by keeper on 01.04.2016.
 */
public class SpaceWallPaperService extends WallpaperService {

    private final String TAG = this.getClass().getSimpleName();
    private final String HIMAWARI_JSON_PATH = "http://himawari8-dl.nict.go.jp/himawari8/img/D531106/latest.json";
    // "http://himawari8-dl.nict.go.jp/himawari8/img/D531106/{масштаб}d/{ширина кадра}/{год}/{месяц}/{день}/{время}_{ячейка по высоте}_{ячейка по ширине}.png"
    private final String HIMAWARI_PIC_PATH = "http://himawari8-dl.nict.go.jp/himawari8/img/D531106/%dd/%d/%s_%d_%d.png";

    private final String DSCOVR_JSON_PATH = "https://epic.gsfc.nasa.gov/api/natural";//"http://epic.gsfc.nasa.gov/api/images.php";
    private final String DSCOVR_PIC_PATH = "https://api.nasa.gov/EPIC/archive/natural/%s.jpg?api_key=DEMO_KEY";

    private final String filename = "downloaded.png";
    private final String jsonFilename = "temp.json";

    private String temp = new String();

    private Tracker mTracker;

    //himawari prefs
    // Масштаб: 4, 8, 16, 20. Финальное изображение состоит из level*level частей
    private final static int level = 4;
    private final static int width = 550;
    private Bitmap[][] bitmaps = new Bitmap[level][level];



    @Override
    public Engine onCreateEngine() {

        try {
            AnalyticsTrackers.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new SpaceEngine();
    }


    public class SpaceEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private Paint paint = new Paint();
        private int canvas_width;
        private int canvas_height;
        private boolean drawOK;

        private int pauseHours;
        private int pauseMinutes;
        private boolean refresh;
        private boolean wifi_only;
        private Const.Satellite satellite;
        private boolean cropImage;

        private SharedPreferences preferences;
        private ConnectivityManager connManager;
        private NetworkInfo activeNetwork;
        private String loading;


        private final Handler handler = new Handler();
        private final Runnable loadRunner = new Runnable() {
            @Override
            public void run() {
                Thread d = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            if (refresh) {

                                connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                                activeNetwork = connManager.getActiveNetworkInfo();

                                if (activeNetwork != null) {

                                    if (!(!(wifi_only) || (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI))) {
                                        loading = getResources().getString(R.string.loadingNoWiFi);
                                        Log.d(TAG, "no wi-fi");
                                        return;
                                    }
                                } else {
                                    loading = getResources().getString(R.string.loadingNoNetwork);
                                    Log.d(TAG, "no network");
                                    return;
                                }

                                switch (satellite){
                                    case HIMAWARI:
                                        loadPicHimawari();
                                        break;
                                    case DSCOVR:
                                        loadPicDSCOVR();
                                        break;
                                }

                                try {
                                    mTracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
                                    mTracker.setScreenName(TAG);
                                    mTracker.send(new HitBuilders.EventBuilder().setCategory("pic download").setLabel(satellite.toString()).build());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }

                            draw();

                        } catch (Exception e) {

                            try {
                                mTracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
                                mTracker.setScreenName(TAG);
                                mTracker.send(new HitBuilders.EventBuilder().setCategory("ERROR").setLabel(e.toString()).build());
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }

                        } finally {
                            handler.removeCallbacks(loadRunner);
                            handler.postDelayed(loadRunner, (1000 * 60 * pauseMinutes) + (1000 * 60 * 60 * pauseHours));
                        }

                    }
                });
                d.start();
            }
        };

        public SpaceEngine() {


        }

        private void draw() {

            Log.d(TAG, "start draw");

            float scale = 1f;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;

            Log.d(TAG, "drawOK: " + drawOK);
            if(drawOK) {
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        //Проверяем, существует ли файл
                        File file = new File(getFilesDir(), filename);
                        if (file.exists()) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.RGB_565;

                            canvas.drawColor(Color.BLACK);

                            Bitmap bitmap = BitmapFactory.decodeFile(getFilesDir() + File.separator + filename, options);
                            if (bitmap != null) {
                                if (cropImage) {
                                    int size = (int) (Math.max(canvas_height, canvas_width) * scale);
                                    if (canvas_height > canvas_width) {
                                        canvas.drawBitmap(getResizedBitmap(bitmap, size, size), canvas_width/2 - size/2, 0, paint);
                                    } else {
                                        canvas.drawBitmap(getResizedBitmap(bitmap, size, size), canvas_height/2 - size/2, 0, paint);
                                    }
                                } else {
                                    int size = (int) (Math.min(canvas_height, canvas_width) * scale);
                                    if (canvas_height > canvas_width) {
                                        canvas.drawBitmap(getResizedBitmap(bitmap, size, size), 0, canvas_height/2 - size/2, paint);
                                    } else {
                                        canvas.drawBitmap(getResizedBitmap(bitmap, size, size), canvas_width/2 - size/2, 0, paint);
                                    }
                                }
                                bitmap.recycle();
                            }

                        } else {
                            paint.setAntiAlias(true);
                            paint.setTextAlign(Paint.Align.CENTER);
                            paint.setColor(Color.WHITE);
                            paint.setTextSize(dp2px(25));
                            canvas.drawText(loading == null ? getResources().getString(R.string.loadingTrue) : loading, canvas_width / 2, canvas_height / 2, paint);
                        }
                    }
                } catch (Exception e){
                    mTracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
                    mTracker.setScreenName(TAG);
                    mTracker.send(new HitBuilders.EventBuilder().setCategory("ERROR").setLabel(e.toString()).build());
                }
                finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
            }

            Log.d(TAG, "finish draw");
        }

        public Bitmap getResizedBitmap(Bitmap bm, int newHeight, int newWidth) {
            int width = bm.getWidth();
            int height = bm.getHeight();
            float scaleWidth = ((float) newWidth) / width;
            float scaleHeight = ((float) newHeight) / height;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleHeight);
            return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        }

        private void loadPicDSCOVR() throws Exception{
            Log.d(TAG, "loadPicDSCOVR start");

            String picPath = String.format(
                    Locale.getDefault(),
                    DSCOVR_PIC_PATH,
                    parseDscovrJson(callURL(DSCOVR_JSON_PATH)));

            Log.d(TAG, "picPath" + picPath);

            if (temp.equals(picPath)){
                return;
            } else {
                temp = picPath;
            }

            URL url = new URL(picPath);
            Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());

            FileOutputStream stream = openFileOutput(filename, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
            stream.close();

            bitmap.recycle();

        }

        private String parseDscovrJson(String jsonContent) throws Exception{

            Log.d(TAG + " parseDscovrJson", jsonContent);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://epic.gsfc.nasa.gov/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            DSCOVRdata data = retrofit.create(DSCOVRdata.class);
            Call call = data.getPhotos();
            Response<List<DSCOVRdata.Photo>> r = call.execute();
            List<DSCOVRdata.Photo> photos = r.body();

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
            int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
            int photoHourMaximum = 0;
            int photoHourMaximiumIndex = 0;

            for (int i=0; i<photos.size(); i++){
                int photoHour = Integer.parseInt(photos.get(i).date.substring(11,13));

                if (currentHour >= photoHour){
                    if (photoHour > photoHourMaximum){
                        photoHourMaximum = photoHour;
                        photoHourMaximiumIndex = i;
                    }
                }
            }


            String[] date = photos.get(photoHourMaximiumIndex).date.split(" ")[0].split("-");

            Log.d("piece of date", date.toString());
            Log.d("piece of url", photos.get(photoHourMaximiumIndex).image);
            Log.d("piece of answer", date[0]+"/"+date[1]+"/"+date[2]+"/"+photos.get(photoHourMaximiumIndex).image);


            return date[0]+"/"+date[1]+"/"+date[2]+"/jpg/"+photos.get(photoHourMaximiumIndex).image;

        }

        private void loadPicHimawari() throws Exception{

            Log.d(TAG, "loadPicHimawari start");

            temp = parseHimawariJson(callURL(HIMAWARI_JSON_PATH));

            for (int y = 0; y < level; y++) {
                for (int x = 0; x < level; x++) {
                    String picPath = String.format(
                            Locale.getDefault(),
                            HIMAWARI_PIC_PATH,
                            level,
                            width,
                            temp,
                            x,
                            y);
                    URL url = new URL(picPath);
                    Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    bitmaps[y][x] = bmp;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width * level, width * level, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);

            for (int y = 0; y < level; y++) {
                for (int x = 0; x < level; x++) {
                    canvas.drawBitmap(
                            bitmaps[y][x],
                            x * width,
                            y * width,
                            paint);
                }
            }

            FileOutputStream stream = openFileOutput(filename, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
            stream.close();

            bitmap.recycle();

            Log.d(TAG, "loadPicHimawari finish");

        }

        private String parseHimawariJson(String jsonContent){
            StringBuilder sb = new StringBuilder();

            String[] jsonContentArray = jsonContent.split("_");
            sb.append(jsonContentArray[2].substring(0, 4) + "/");
            sb.append(jsonContentArray[2].substring(4, 6) + "/");
            sb.append(jsonContentArray[2].substring(6, 8) + "/");
            sb.append(jsonContentArray[3] + "00");

            return sb.toString();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d("TAG", "onSharedPreferenceChanged");

            try {
                mTracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
                mTracker.setScreenName(TAG);
                mTracker.send(new HitBuilders.EventBuilder().setCategory("pref change").setLabel(key).build());
            } catch (Exception e) {
                e.printStackTrace();
            }

            prefRefresh();

            if (key.equals("sat_key")) {
                handler.removeCallbacks(loadRunner);
                handler.post(loadRunner);
            } else {
                draw();
            }

        }

        protected void prefRefresh(){

            Log.d(TAG, "prefRefresh");

            //preferences = PreferenceManager.getDefaultSharedPreferences(this);
            pauseHours = preferences.getInt("pauseHours", 0);
            pauseMinutes = preferences.getInt("pauseMinutes", 10);
            wifi_only = preferences.getBoolean("wifi_only", false);
            refresh = preferences.getBoolean("service_on", true);
            cropImage = preferences.getBoolean("cropImage", false);

            if (preferences.getString("sat_key", "DSCOVR").equals("HIMAWARI")){
                satellite = Const.Satellite.HIMAWARI;
            } else {
                satellite = Const.Satellite.DSCOVR;
            }

            Log.d(TAG, "satellite:" + satellite);


            //if ((pauseHours==0) && (pauseMinutes<10)) pauseMinutes = 10;
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "onSurfaceCreated");

            preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            preferences.registerOnSharedPreferenceChangeListener(this);

            prefRefresh();

            super.onSurfaceCreated(holder);
            handler.post(loadRunner);
            drawOK = true;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "onSurfaceChanged");

            this.canvas_width = width;
            this.canvas_height = height;
            super.onSurfaceChanged(holder, format, width, height);
            draw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "onSurfaceDestroyed");

            synchronized (this) {
                super.onSurfaceDestroyed(holder);
                handler.removeCallbacks(loadRunner);
                drawOK = false;
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d(TAG, "onVisibilityChanged: " + visible);

            super.onVisibilityChanged(visible);

                drawOK = true;
        }

        private float dp2px(int dp) {
            Resources r = getResources();
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
        }
    }

    public String callURL(String myURL){

        Log.d(TAG, "start download " + myURL);

        StringBuilder sb = new StringBuilder();
        URLConnection urlConn = null;
        InputStreamReader in = null;

        try {
            URL url = new URL(myURL);
            urlConn = url.openConnection();
            if (urlConn != null)
                urlConn.setReadTimeout(60 * 1000);
            if (urlConn != null && urlConn.getInputStream() != null) {
                in = new InputStreamReader(urlConn.getInputStream(),
                        Charset.defaultCharset());
                BufferedReader bufferedReader = new BufferedReader(in);
                if (bufferedReader != null) {
                    int cp;
                    while ((cp = bufferedReader.read()) != -1) {
                        sb.append((char) cp);
                    }
                    bufferedReader.close();
                }
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(TAG, "result download :" + sb.toString());

        return sb.toString();
    }

}
