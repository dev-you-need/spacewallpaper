package com.deerslab.spacewallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Created by keeper on 01.04.2016.
 */
public class SpaceWallPaperService extends WallpaperService implements SharedPreferences.OnSharedPreferenceChangeListener  {

    private final String TAG = this.getClass().getSimpleName();
    private final String HIMAWARI_JSON_PATH = "http://himawari8-dl.nict.go.jp/himawari8/img/D531106/latest.json";
    // "http://himawari8-dl.nict.go.jp/himawari8/img/D531106/{масштаб}d/{ширина кадра}/{год}/{месяц}/{день}/{время}_{ячейка по высоте}_{ячейка по ширине}.png"
    private final String HIMAWARI_PIC_PATH = "http://himawari8-dl.nict.go.jp/himawari8/img/D531106/%dd/%d/%s_%d_%d.png";

    private final String DSCOVR_JSON_PATH = "http://epic.gsfc.nasa.gov/api/images.php";
    private final String DSCOVR_PIC_PATH = "http://epic.gsfc.nasa.gov/epic-archive/jpg/%s.jpg";

    private final String filename = "downloaded.png";
    private final String jsonFilename = "temp.json";

    private String temp = new String();

    //himawari prefs
    // Масштаб: 4, 8, 16, 20. Финальное изображение состоит из level*level частей
    private final static int level = 4;
    private final static int width = 550;
    private Bitmap[][] bitmaps = new Bitmap[level][level];

    private int pauseHours;
    private int pauseMinutes;
    private boolean refresh;
    private boolean wifi_only;
    private String satellite;

    @Override
    public Engine onCreateEngine() {

        return new SpaceEngine();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "root onSharedPreferenceChanged");
    }

    public class SpaceEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private Paint paint = new Paint();
        private int canvas_width;
        private int canvas_height;

        private SharedPreferences preferences;


        private final Handler handler = new Handler();
        private final Runnable loadRunner = new Runnable() {
            @Override
            public void run() {
                Thread d = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            if (refresh) {

                                ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                                NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();

                                if (activeNetwork != null) {

                                    if (!(!(wifi_only) || (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI))) {
                                        Log.d(TAG, "no wi-fi");
                                        return;
                                    }
                                } else {
                                    Log.d(TAG, "no network");
                                    return;
                                }

                                if (satellite.equals("HIMAWARI")) {
                                    loadPicHimawari();
                                } else {
                                    loadPicDSCOVR();
                                }

                                draw();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
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

            Log.d(TAG, "SpaceEngine constructor");

            preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            preferences.registerOnSharedPreferenceChangeListener(this);

            prefRefresh();

            handler.post(loadRunner);

        }

        private void draw() {

            Log.d(TAG, "start draw");

            float scale = 1f;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    //Проверяем, существует ли файл
                    File file = new File(getFilesDir(), filename);
                    if (file.exists()) {
                        int size = (int) (Math.max(canvas_height, canvas_width) * scale);

                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.RGB_565;

                        Bitmap bitmap = BitmapFactory.decodeFile(getFilesDir() + File.separator + filename, options);
                        if (bitmap != null) {
                            canvas.drawBitmap(getResizedBitmap(bitmap, size, size), canvas_width / 2 - size / 2, 0, paint);
                            bitmap.recycle();
                        }

                    } else {
                        // Если нет, то напишем, что файл пока загружается и подождем, пока метод будет вызван после загрузки
                        canvas.drawText("Loading///", canvas_width / 2, canvas_height / 2, paint);
                    }
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
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

            String jsonContent2 = "[{\"image\":\"epic_1b_20160402005027_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.325646, \"lon\": 156.996805},n\"dscovr_j2000_position\" : { \"x\" : 1518064.875000, \"y\" : 40591.382812, \"z\" : 61769.992188 },n\"lunar_j2000_position\" : { \"x\" : 192395.093750, \"y\" : -312521.531250, \"z\" : -106497.960938 },n\"sun_j2000_position\" : { \"x\" : 145995120.000000, \"y\" : 29690972.000000, \"z\" : 12870902.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.001770, \"q1\" : 0.793190, \"q2\" : -0.608490, \"q3\" : 0.024290 } }\",\"date\":\"2016-04-02 00:50:27\"},{\"image\":\"epic_1b_20160402023829_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.375660, \"lon\": 129.993055},n\"dscovr_j2000_position\" : { \"x\" : 1517877.375000, \"y\" : 42099.722656, \"z\" : 62890.507812 },n\"lunar_j2000_position\" : { \"x\" : 196112.843750, \"y\" : -306610.156250, \"z\" : -105407.898438 },n\"sun_j2000_position\" : { \"x\" : 145956416.000000, \"y\" : 29864616.000000, \"z\" : 12946173.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.001610, \"q1\" : 0.792730, \"q2\" : -0.609070, \"q3\" : 0.024830 } }\",\"date\":\"2016-04-02 02:38:29\"},{\"image\":\"epic_1b_20160402061433_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.475688, \"lon\": 75.935547},n\"dscovr_j2000_position\" : { \"x\" : 1517492.875000, \"y\" : 45123.777344, \"z\" : 65133.601562 },n\"lunar_j2000_position\" : { \"x\" : 201327.890625, \"y\" : -298045.531250, \"z\" : -103127.093750 },n\"sun_j2000_position\" : { \"x\" : 145878288.000000, \"y\" : 30211782.000000, \"z\" : 13096663.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.001280, \"q1\" : 0.791820, \"q2\" : -0.610200, \"q3\" : 0.025960 } }\",\"date\":\"2016-04-02 06:14:33\"},{\"image\":\"epic_1b_20160402095038_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.525702, \"lon\": 21.878039},n\"dscovr_j2000_position\" : { \"x\" : 1517095.250000, \"y\" : 48157.281250, \"z\" : 67379.164062 },n\"lunar_j2000_position\" : { \"x\" : 207685.843750, \"y\" : -294204.156250, \"z\" : -100720.320312 },n\"sun_j2000_position\" : { \"x\" : 145799184.000000, \"y\" : 30558752.000000, \"z\" : 13247067.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.001048, \"q1\" : 0.790910, \"q2\" : -0.611314, \"q3\" : 0.027328 } }\",\"date\":\"2016-04-02 09:50:38\"},{\"image\":\"epic_1b_20160402113841_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.575715, \"lon\": -5.125712},n\"dscovr_j2000_position\" : { \"x\" : 1516891.375000, \"y\" : 49677.425781, \"z\" : 68502.781250 },n\"lunar_j2000_position\" : { \"x\" : 212547.046875, \"y\" : -293152.093750, \"z\" : -99472.359375 },n\"sun_j2000_position\" : { \"x\" : 145759264.000000, \"y\" : 30732150.000000, \"z\" : 13322230.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.000894, \"q1\" : 0.790460, \"q2\" : -0.611873, \"q3\" : 0.027957 } }\",\"date\":\"2016-04-02 11:38:41\"},{\"image\":\"epic_1b_20160402132644_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.625729, \"lon\": -32.179469},n\"dscovr_j2000_position\" : { \"x\" : 1516684.125000, \"y\" : 51200.132812, \"z\" : 69627.156250 },n\"lunar_j2000_position\" : { \"x\" : 218730.828125, \"y\" : -291848.781250, \"z\" : -98195.062500 },n\"sun_j2000_position\" : { \"x\" : 145719088.000000, \"y\" : 30905522.000000, \"z\" : 13397383.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.000730, \"q1\" : 0.790010, \"q2\" : -0.612440, \"q3\" : 0.028490 } }\",\"date\":\"2016-04-02 13:26:44\"},{\"image\":\"epic_1b_20160402185052_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.775771, \"lon\": -113.240728},n\"dscovr_j2000_position\" : { \"x\" : 1516042.125000, \"y\" : 55781.839844, \"z\" : 73003.632812 },n\"lunar_j2000_position\" : { \"x\" : 241773.656250, \"y\" : -281236.000000, \"z\" : -94185.812500 },n\"sun_j2000_position\" : { \"x\" : 145597120.000000, \"y\" : 31425314.000000, \"z\" : 13622699.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.000280, \"q1\" : 0.788630, \"q2\" : -0.614120, \"q3\" : 0.030260 } }\",\"date\":\"2016-04-02 18:50:52\"},{\"image\":\"epic_1b_20160402203854_00\",\"caption\":null,\"coords\":\"{ \"centroid_coordinates\" : {\"lat\": 2.825785, \"lon\": -140.294485},n\"dscovr_j2000_position\" : { \"x\" : 1515821.250000, \"y\" : 57313.718750, \"z\" : 74130.328125 },n\"lunar_j2000_position\" : { \"x\" : 248982.906250, \"y\" : -274912.687500, \"z\" : -92788.054688 },n\"sun_j2000_position\" : { \"x\" : 145555984.000000, \"y\" : 31598484.000000, \"z\" : 13697764.000000 },n\"attitude_quaternions\" : { \"q0\" : 0.000100, \"q1\" : 0.788177, \"q2\" : -0.614676, \"q3\" : 0.030802 } }\",\"date\":\"2016-04-02 20:38:54\"}]";

            JsonParser parser = new JsonParser();
            JsonArray obj = parser.parse(jsonContent2).getAsJsonArray();

            Log.d(TAG, "obj.size()" + obj.size());

            DscovrImageData[] dscovrImageData = new Gson().fromJson(jsonContent2, DscovrImageData[].class);

            Log.d(TAG, "*******************" + dscovrImageData.length + "****************************");

            StringBuilder sb = new StringBuilder();

            String[] jsonContentArray = jsonContent.split("\"");
            sb.append(jsonContentArray[3]);

            return sb.toString();
        }



        private void loadPicHimawari() throws Exception{

            Log.d(TAG, "loadPicHimawari start");

            for (int y = 0; y < level; y++) {
                for (int x = 0; x < level; x++) {
                    String picPath = String.format(
                            Locale.getDefault(),
                            HIMAWARI_PIC_PATH,
                            level,
                            width,
                            parseHimawariJson(callURL(HIMAWARI_JSON_PATH)),
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
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            this.canvas_width = width;
            this.canvas_height = height;
            super.onSurfaceChanged(holder, format, width, height);
            draw();
        }


        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d("TAG", "onSharedPreferenceChanged");

            prefRefresh();
            handler.removeCallbacks(loadRunner);
            handler.post(loadRunner);

        }

        protected void prefRefresh(){

            Log.d(TAG, "prefRefresh");

            //preferences = PreferenceManager.getDefaultSharedPreferences(this);
            pauseHours = preferences.getInt("pauseHours", 0);
            pauseMinutes = preferences.getInt("pauseMinutes", 10);
            wifi_only = preferences.getBoolean("wifi_only", true);
            satellite = preferences.getString("sat_key", "DSCOVR");
            Log.d(TAG, "sat_key:" + satellite);
            refresh = preferences.getBoolean("service_on", true);

            //if ((pauseHours==0) && (pauseMinutes<10)) pauseMinutes = 10;
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
            throw new RuntimeException("Exception while calling URL:"+ myURL, e);
        }

        Log.d(TAG, "result download :" + sb.toString());

        return sb.toString();
    }




}
