package com.packruler.googlefontsservice;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.webfonts.Webfonts;
import com.google.api.services.webfonts.WebfontsRequestInitializer;
import com.google.api.services.webfonts.model.Webfont;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Created by Packruler on 2/19/2015.
 */
public class GoogleFontsService extends Service {
    private final String TAG = getClass().getSimpleName();
    private static final File fontDir = new File(Environment.getExternalStorageDirectory() + "/Packruler Creations/Google Fonts/");
    private final File JSONStorage = new File(fontDir.getAbsolutePath() + "/All_Fonts.JSON");
    private String sortOrderString = "popularity";
    private SharedPreferences sharedPreferences;

    private String WEBFONTS_API = "AIzaSyCEfGufTxShTNJ9grUlVBNbTUNLq7_wAPc";

    private static final String ALL_FONTS = "ALL_FONTS";
    private static final String SORT_ORDER = "SORT_ORDER";
    private static final String SANS_SERIF = "sans-serif";
    private static final String SERIF = "serif";
    private static final String DISPLAY = "display";
    private static final String HANDWRITING = "handwriting";
    private static final String MONOSPACE = "monospace";

    private final HashMap<String, Map<String, LocalWebfont>> allFonts = new HashMap<>();
    private HashMap<String, LocalWebfont> sansSerif = new HashMap<>();
    private HashMap<String, LocalWebfont> serif = new HashMap<>();
    private HashMap<String, LocalWebfont> display = new HashMap<>();
    private HashMap<String, LocalWebfont> handwriting = new HashMap<>();
    private HashMap<String, LocalWebfont> monospace = new HashMap<>();

    private ThreadPoolExecutor threadPool;
    private LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
    private Webfonts.WebfontsOperations.List list;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getSharedPreferences(getPackageName() + ".FONTS", MODE_PRIVATE);

        int numProcessors = Runtime.getRuntime().availableProcessors();
        Log.i(TAG, "Number of processors: " + numProcessors);
        threadPool = new ThreadPoolExecutor(numProcessors, numProcessors, 10, TimeUnit.MILLISECONDS, workQueue);
        if (!fontDir.exists())
            Log.i(TAG, "Make Font Directory: " + fontDir.mkdirs());

        if (!JSONStorage.exists())
            try {
                Log.i(TAG, "New JSONStorage: " + JSONStorage.createNewFile());
            } catch (IOException e) {
                e.printStackTrace();
            }

        if (sharedPreferences.getString(ALL_FONTS, null) != null) {
//        if (false) {
            try {
                JSONObject jsonObject = new JSONObject(sharedPreferences.getString(ALL_FONTS, null));
                Log.i(TAG, "On device JSON: " + jsonObject.toString());
                sortOrderString = jsonObject.getString(SORT_ORDER);
                final JSONObject sansSerifJSON = jsonObject.getJSONObject(SANS_SERIF);
                final JSONObject serifJSON = jsonObject.getJSONObject(SERIF);
                final JSONObject displayJSON = jsonObject.getJSONObject(DISPLAY);
                final JSONObject handwritingJSON = jsonObject.getJSONObject(HANDWRITING);
                final JSONObject monospaceJSON = jsonObject.getJSONObject(MONOSPACE);

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            allFonts.put(SANS_SERIF, readLocalWebfontsArray(sansSerifJSON));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            allFonts.put(SERIF, readLocalWebfontsArray(serifJSON));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            allFonts.put(DISPLAY, readLocalWebfontsArray(displayJSON));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            allFonts.put(HANDWRITING, readLocalWebfontsArray(handwritingJSON));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            allFonts.put(MONOSPACE, readLocalWebfontsArray(monospaceJSON));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                allRatingsDone = true;

            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    sortOrderString = sharedPreferences.getString("sort_order", "alpha");
                    updateFonts();
                }
            });
        }

    }

    @Override
    public void onDestroy() {
        threadPool.shutdown();
        super.onDestroy();
    }

    private void setAllFonts() {
        allFonts.put(SANS_SERIF, new HashMap<String, LocalWebfont>());
        allFonts.put(SERIF, new HashMap<String, LocalWebfont>());
        allFonts.put(DISPLAY, new HashMap<String, LocalWebfont>());
        allFonts.put(HANDWRITING, new HashMap<String, LocalWebfont>());
        allFonts.put(MONOSPACE, new HashMap<String, LocalWebfont>());
    }

    private int complete = 0;
    private GetSortRatings popularity = new GetSortRatings(LocalWebfont.POPULARITY);
    private GetSortRatings style = new GetSortRatings(LocalWebfont.STYLE);
    private GetSortRatings trending = new GetSortRatings(LocalWebfont.TRENDING);

    private boolean allRatingsDone = false;

    private void getAllRatings() {
        Log.i(TAG, "getAllRatings");
        complete = 0;
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Remove popularity: " + threadPool.remove(popularity));
                Log.i(TAG, "Remove style: " + threadPool.remove(style));
                Log.i(TAG, "Remove trending: " + threadPool.remove(trending));

                Log.i(TAG, LocalWebfont.POPULARITY + " Complete");
                complete++;
                long start = SystemClock.elapsedRealtime();
                threadPool.execute(style);
                Log.i(TAG, "Style sort done!");
                threadPool.execute(trending);
                Log.i(TAG, "Trending sort done!");

                final Object SYNC = new Object();
                synchronized (SYNC) {
                    while (complete < 3) {
                        try {
//                            Log.i(TAG, "Active threads: " + threadPool.getActiveCount());
//                            Log.i(TAG, LocalWebfont.STYLE + " Position: " + style.position);
//                            Log.i(TAG, LocalWebfont.TRENDING + " Position: " + trending.position);
//                            Log.i(TAG, "Completed: " + complete);
                            SYNC.wait(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.i(TAG, "Sort update duration: " + (SystemClock.elapsedRealtime() - start));
                allRatingsDone = true;

                JSONObject allFontsJson = new JSONObject();
                try {
                    allFontsJson.put(SORT_ORDER, sortOrderString);

                    allFontsJson.put(SANS_SERIF, getCategoryJSONObject(allFonts.get(SANS_SERIF)));
                    allFontsJson.put(SERIF, getCategoryJSONObject(allFonts.get(SERIF)));
                    allFontsJson.put(DISPLAY, getCategoryJSONObject(allFonts.get(DISPLAY)));
                    allFontsJson.put(HANDWRITING, getCategoryJSONObject(allFonts.get(HANDWRITING)));
                    allFontsJson.put(MONOSPACE, getCategoryJSONObject(allFonts.get(MONOSPACE)));
                    final String output = allFontsJson.toString();
                    Log.i(TAG, "JSON: " + output);

                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject allFontsJson = new JSONObject(allFonts);
                                FileWriter writer = new FileWriter(JSONStorage);
                                writer.write(output);
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    sharedPreferences.edit().putString(ALL_FONTS, output).apply();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private HashMap<String, LocalWebfont> readLocalWebfontsArray(JSONObject in) throws JSONException {
        HashMap<String, LocalWebfont> output = new HashMap<>();
        Iterator<String> iterator = in.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            output.put(key, new LocalWebfont(in.getJSONObject(key)));
        }
        return output;
    }

    private class GetSortRatings implements Runnable {
        private String sortOrder;
        protected int position = 0;

        public GetSortRatings(String sort) {
            sortOrder = sort;
        }

        @Override
        public void run() {
            Log.i(TAG, "Start " + sortOrder);
            try {
                list.setSort(sortOrder);
                List<Webfont> webfontList = list.execute().getItems();
                position = 0;

                for (Webfont current : webfontList) {
//                    LocalWebfont localWebfont = new LocalWebfont(current);
//                    Log.i(TAG, "Sort: " + sortOrder + " Position: " + position);
//                    localWebfont.setRating(sortOrder, position);
                    position++;

                    switch (current.getCategory()) {
                        case SANS_SERIF:
                            allFonts.get(SANS_SERIF).get(current.getFamily()).setRating(sortOrder, position);
                            break;
                        case SERIF:
                            allFonts.get(SERIF).get(current.getFamily()).setRating(sortOrder, position);
                            break;
                        case DISPLAY:
                            allFonts.get(DISPLAY).get(current.getFamily()).setRating(sortOrder, position);
                            break;
                        case HANDWRITING:
                            allFonts.get(HANDWRITING).get(current.getFamily()).setRating(sortOrder, position);
                            break;
                        case MONOSPACE:
                            allFonts.get(MONOSPACE).get(current.getFamily()).setRating(sortOrder, position);
                            break;
                    }
                }
                complete++;
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.i(TAG, sortOrder + " Complete");
        }
    }

    /**
     * Re-retrieves Google WebFonts in a new sort order
     *
     * @param sortOrder
     *         Options are:
     *         alpha - Sort the list alphabetically
     *         date - Sort the list by date added (most recent font added or updated first)
     *         popularity - Sort the list by popularity (most popular family first)
     *         style - Sort the list by number of styles available (family with most styles first)
     *         trending - Sort the list by families seeing growth in usage (family seeing the most
     *         growth first)
     *
     * @return {@link #updateFonts()} sorted in specified order
     */
    public HashMap<String, Map<String, LocalWebfont>> updateSort(String sortOrder) {
        if (sortOrder.equals(sortOrderString))
            return allFonts;
        sortOrderString = sortOrder;

        sharedPreferences.edit().putString(ALL_FONTS, sortOrder).apply();
        return updateFonts();
    }

    /**
     * Update data of Google Fonts and store local cache of information
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public HashMap<String, Map<String, LocalWebfont>> updateFonts() {
        Log.i(TAG, "Update Fonts");
        allRatingsDone = false;
        final HashMap<String, Map<String, LocalWebfont>> temp = allFonts;
        allFonts.clear();
        NetHttpTransport netHttpTransport = new NetHttpTransport();
        GsonFactory gsonFactory = new GsonFactory();
        WebfontsRequestInitializer requestInitializer = new WebfontsRequestInitializer(WEBFONTS_API);
        Webfonts.Builder builder = new Webfonts.Builder(netHttpTransport, gsonFactory, null);
        builder.setApplicationName(getPackageName());
        builder.setWebfontsRequestInitializer(requestInitializer);
        Webfonts webfonts = builder.build();
        Webfonts.WebfontsOperations operations = webfonts.webfonts();


        try {
            Log.i(TAG, "Sort: " + sortOrderString);
            list = operations.list();
//                list.setSort(sortOrderString);
            list.setSort(LocalWebfont.POPULARITY);
            List<Webfont> webfontList;
            webfontList = list.execute().getItems();
            Log.i(TAG, "Size: " + webfontList.size());

            synchronized (allFonts) {
                int position = 0;
                for (Webfont current : webfontList) {
                    LocalWebfont localWebfont = new LocalWebfont(current);
//                    Log.i(TAG, "Sort: " + sortOrderString + " Rating: " + position);
                    localWebfont.setRating(LocalWebfont.POPULARITY, position);
                    position++;
                    switch (localWebfont.getCategory()) {
                        case SANS_SERIF:
                            if (allFonts.get(SANS_SERIF) == null)
                                allFonts.put(SANS_SERIF, new HashMap<String, LocalWebfont>());
                            allFonts.get(SANS_SERIF).put(localWebfont.getFamily(), localWebfont);
                            break;
                        case SERIF:
                            if (allFonts.get(SERIF) == null)
                                allFonts.put(SERIF, new HashMap<String, LocalWebfont>());
                            allFonts.get(SERIF).put(localWebfont.getFamily(), localWebfont);
                            break;
                        case DISPLAY:
                            if (allFonts.get(DISPLAY) == null)
                                allFonts.put(DISPLAY, new HashMap<String, LocalWebfont>());
                            allFonts.get(DISPLAY).put(localWebfont.getFamily(), localWebfont);
                            break;
                        case HANDWRITING:
                            if (allFonts.get(HANDWRITING) == null)
                                allFonts.put(HANDWRITING, new HashMap<String, LocalWebfont>());
                            allFonts.get(HANDWRITING).put(localWebfont.getFamily(), localWebfont);
                            break;
                        case MONOSPACE:
                            if (allFonts.get(MONOSPACE) == null)
                                allFonts.put(MONOSPACE, new HashMap<String, LocalWebfont>());
                            allFonts.get(MONOSPACE).put(localWebfont.getFamily(), localWebfont);
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            allFonts.clear();
            allFonts.putAll(temp);
        }

        Log.i(TAG, "Sans-Serif: " + allFonts.get(SANS_SERIF).size());
        Log.i(TAG, "Serif: " + allFonts.get(SERIF).size());
        Log.i(TAG, "Display: " + allFonts.get(DISPLAY).size());
        Log.i(TAG, "Handwriting: " + allFonts.get(HANDWRITING).size());
        Log.i(TAG, "Monospace: " + allFonts.get(MONOSPACE).size());

//
//        JSONObject allFontsJson = new JSONObject();
//        try {
//            allFontsJson.put(SORT_ORDER, sortOrderString);
//
//            allFontsJson.put(SANS_SERIF, getCategoryJSONObject(allFonts.get(SANS_SERIF)));
//            allFontsJson.put(SERIF, getCategoryJSONObject(allFonts.get(SERIF)));
//            allFontsJson.put(DISPLAY, getCategoryJSONObject(allFonts.get(DISPLAY)));
//            allFontsJson.put(HANDWRITING, getCategoryJSONObject(allFonts.get(HANDWRITING)));
//            allFontsJson.put(MONOSPACE, getCategoryJSONObject(allFonts.get(MONOSPACE)));
//            final  JSONObject toFileJSON = allFontsJson;
//            Log.i(TAG, "SANS_SERIF JSON: " + allFontsJson.getJSONObject(SANS_SERIF).toString());
//
//            threadPool.execute(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        FileWriter writer = new FileWriter(JSONStorage);
//                        writer.write(toFileJSON.toString());
//                        writer.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//            sharedPreferences.edit().putString(ALL_FONTS, allFontsJson.toString()).apply();
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
        try {
            getAllRatings();
        } catch (RejectedExecutionException e) {
            //Shutting down
        }

        return allFonts;
    }

    private JSONObject getCategoryJSONObject(Map<String, LocalWebfont> in) throws JSONException {
        JSONObject object = new JSONObject();
        for (String key : in.keySet()) {
            object.put(key, in.get(key).getJsonObject());
        }
        return object;
    }

    /**
     * Get current information relating to Google Fonts data
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public HashMap<String, Map<String, LocalWebfont>> getAllFonts() {
        return allFonts;
    }

    public List<LocalWebfont> getAllFontList(String sort) {
        if (allRatingsDone) {
            ArrayList<LocalWebfont> entryList = new ArrayList<>();

            Map<String, LocalWebfont> tree = allFonts.get(SERIF);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            tree = allFonts.get(SANS_SERIF);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            tree = allFonts.get(HANDWRITING);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            tree = allFonts.get(DISPLAY);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            tree = allFonts.get(MONOSPACE);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }
            Collections.sort(entryList, new SortComparable(sort));
            return entryList;
        }
        return null;
    }

    /**
     * Get current information regarding Serif fonts
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public Map<String, LocalWebfont> getSerif() {
        synchronized (allFonts) {
            return allFonts.get(SERIF);
        }
    }

    public List<LocalWebfont> getSerifList(String sort) {
        if (allRatingsDone) {
            ArrayList<LocalWebfont> entryList = new ArrayList<>();

            Map<String, LocalWebfont> tree = allFonts.get(SERIF);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            Collections.sort(entryList, new SortComparable(sort));
            return entryList;
        }
        return null;
    }

    /**
     * Get current information regarding Sans Serif fonts
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public Map<String, LocalWebfont> getSansSerif() {
        synchronized (allFonts) {
            return allFonts.get(SANS_SERIF);
        }
    }

    public List<LocalWebfont> getSansSerifList(String sort) {
        if (allRatingsDone) {
            ArrayList<LocalWebfont> entryList = new ArrayList<>();

            Map<String, LocalWebfont> tree = allFonts.get(SANS_SERIF);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            Collections.sort(entryList, new SortComparable(sort));
            return entryList;
        }
        return null;
    }

    /**
     * Get current information regarding Display fonts
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public Map<String, LocalWebfont> getDisplay() {
        synchronized (allFonts) {
            return allFonts.get(DISPLAY);
        }
    }

    public List<LocalWebfont> getDisplayList(String sort) {
        if (allRatingsDone) {
            ArrayList<LocalWebfont> entryList = new ArrayList<>();

            Map<String, LocalWebfont> tree = allFonts.get(DISPLAY);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            Collections.sort(entryList, new SortComparable(sort));
            return entryList;
        }
        return null;
    }

    /**
     * Get current information regarding Handwriting fonts
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public Map<String, LocalWebfont> getHandwriting() {
        synchronized (allFonts) {
            return allFonts.get(HANDWRITING);
        }
    }

    public List<LocalWebfont> getHandwritingList(String sort) {
        if (allRatingsDone) {
            ArrayList<LocalWebfont> entryList = new ArrayList<>();

            Map<String, LocalWebfont> tree = allFonts.get(HANDWRITING);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }

            Collections.sort(entryList, new SortComparable(sort));
            return entryList;
        }
        return null;
    }

    /**
     * Get current information regarding Monospace fonts
     *
     * @return {@link java.util.HashMap}<{@link java.lang.String}, {@link
     * com.packruler.googlefontsservice.LocalWebfont}>
     */
    public Map<String, LocalWebfont> getMonospace() {
        synchronized (allFonts) {
            return allFonts.get(MONOSPACE);
        }
    }

    public List<LocalWebfont> getMonospaceList(String sort) {
        if (allRatingsDone) {
            ArrayList<LocalWebfont> entryList = new ArrayList<>();

            Map<String, LocalWebfont> tree = allFonts.get(MONOSPACE);
            for (LocalWebfont webfont : tree.values()) {
                entryList.add(webfont);
            }
            Collections.sort(entryList, new SortComparable(sort));
            return entryList;
        }
        return null;
    }

    /**
     * Deletes all fonts in font download location {@link android.os.Environment#getExternalStorageDirectory()}/Packruler
     * Creations/Google Fonts/
     */
    public void deleteAllFonts() {
        for (File file : fontDir.listFiles()) {
            Log.i(TAG, "Delete " + file.getName() + ": " + file.delete());
        }
    }

    /**
     * Get {@link java.io.File} representing requested font
     *
     * @param family
     *         Name of the font family
     * @param variant
     *         Name of the font variant
     *
     * @return {@link java.io.File} representing requested font. Use {@link java.io.File#exists()}
     * to determine if file exists
     */
    public File getFontFile(String family, String variant) {
        return new File(fontDir.getPath() + "/" + family + "/" + variant + ".ttf");
    }

    /**
     * Get sort order of current Google Fonts data cache
     *
     * @return sort order of current Google Fonts data cache
     */
    public String getSortOrder() {
        return sortOrderString;
    }

    /**
     * Get local font directory
     *
     * @return {@link java.io.File} for local font storage
     */
    public static File getFontDir() {
        return fontDir;
    }

    public IGoogleFontsService.Stub mBinder = new IGoogleFontsService.Stub() {
        @Override
        public HashMap<String, Map<String, LocalWebfont>> updateSort(String sortOrder) throws RemoteException {
            return GoogleFontsService.this.updateSort(sortOrder);
        }

        @Override
        public HashMap<String, Map<String, LocalWebfont>> updateFonts() throws RemoteException {
            return GoogleFontsService.this.updateFonts();
        }

        @Override
        public HashMap<String, Map<String, LocalWebfont>> getAllFonts() throws RemoteException {
            return GoogleFontsService.this.getAllFonts();
        }

        @Override
        public List getAllFontList(String sort) throws RemoteException {
            return GoogleFontsService.this.getAllFontList(sort);
        }

        @Override
        public Map<String, LocalWebfont> getSerif() throws RemoteException {
            return GoogleFontsService.this.getSerif();
        }

        @Override
        public List getSerifList(String sort) throws RemoteException {
            return GoogleFontsService.this.getSerifList(sort);
        }

        @Override
        public Map<String, LocalWebfont> getSansSerif() throws RemoteException {
            return GoogleFontsService.this.getSansSerif();
        }

        @Override
        public List getSansSerifList(String sort) throws RemoteException {
            return GoogleFontsService.this.getSansSerifList(sort);
        }

        @Override
        public Map<String, LocalWebfont> getDisplay() throws RemoteException {
            return GoogleFontsService.this.getDisplay();
        }

        @Override
        public List getDisplayList(String sort) throws RemoteException {
            return GoogleFontsService.this.getDisplayList(sort);
        }

        @Override
        public Map<String, LocalWebfont> getHandwriting() throws RemoteException {
            return GoogleFontsService.this.getHandwriting();
        }

        @Override
        public List getHandwritingList(String sort) throws RemoteException {
            return GoogleFontsService.this.getHandwritingList(sort);
        }

        @Override
        public Map<String, LocalWebfont> getMonospace() throws RemoteException {
            return GoogleFontsService.this.getMonospace();
        }

        @Override
        public List getMonospaceList(String sort) throws RemoteException {
            return GoogleFontsService.this.getMonospaceList(sort);
        }

        @Override
        public void deleteAllFonts() throws RemoteException {
            GoogleFontsService.this.deleteAllFonts();
        }

        @Override
        public String getFontFilePath(String family, String variant) throws RemoteException {

            return getFontFile(family, variant).getAbsolutePath();
        }

        @Override
        public String getSortOrder() throws RemoteException {
            return GoogleFontsService.this.getSortOrder();
        }

        @Override
        public String getFontDirPath() throws RemoteException {
            return GoogleFontsService.getFontDir().getAbsolutePath();
        }
    };
}
