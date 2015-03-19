package com.packruler.googlefontsservice;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.api.client.util.DateTime;
import com.google.api.services.webfonts.model.Webfont;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by Packruler on 2/19/2015.
 */
public class LocalWebfont implements Parcelable {
    private final String TAG = this.getClass().getSimpleName();

    public static final String ALL_FONTS = "ALL_FONTS";
    public static final String SANS_SERIF = "sans-serif";
    public static final String SERIF = "serif";
    public static final String DISPLAY = "display";
    public static final String HANDWRITING = "handwriting";
    public static final String MONOSPACE = "monospace";

    public static final String ALPHA = "alpha";
    public static final String DATE = "date";
    public static final String POPULARITY = "popularity";
    public static final String STYLE = "style";
    public static final String TRENDING = "trending";


    private static final File fontDir = new File(Environment.getExternalStorageDirectory() + "/Packruler Creations/Google Fonts/");

    private String category;
    private String family;
    private Map<String, String> files = new HashMap<>();
    private String kind;
    private DateTime lastModified;
    private List<String> subsets = new ArrayList<>();
    private List<String> variants = new ArrayList<>();
    private String version;
    private TreeMap<String, Integer> ratings = new TreeMap<>();
    private JSONObject jsonObject = new JSONObject();

    private File familyDirectory;

    public LocalWebfont(Webfont webfont) {
        jsonObject = new JSONObject();
        setCategory(webfont.getCategory());
        setFamily(webfont.getFamily());
        setFiles(webfont.getFiles());
        setKind(webfont.getKind());
        setLastModified(webfont.getLastModified());
        setSubsets(webfont.getSubsets());
        setVariants(webfont.getVariants());
        setVersion(webfont.getVersion());
        setRating("popularity", -1);
        setRating("style", -1);
        setRating("trending", -1);
    }

    public LocalWebfont(JSONObject jsonObject) throws JSONException {
        Log.i(TAG, "JSON: " + jsonObject.toString());
        setCategory(jsonObject.getString(CATEGORY));
        setFamily(jsonObject.getString(FAMILY));

        JSONObject filesJson = jsonObject.getJSONObject(FILES);
        Iterator<String> iterator = filesJson.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            files.put(key, filesJson.getString(key));
        }
        setFiles(files);

        setKind(jsonObject.getString(KIND));
        setLastModified(new DateTime(jsonObject.getLong(LAST_MODIFIED)));

        JSONArray jsonArray = jsonObject.getJSONArray(SUBSETS);
        subsets = new ArrayList<>();
        for (int x = 0; x < jsonArray.length(); x++) {
            subsets.add(jsonArray.getString(x));
        }
        setSubsets(subsets);

        jsonArray = jsonObject.getJSONArray(VARIANTS);
        variants = new ArrayList<>();
        for (int x = 0; x < jsonArray.length(); x++) {
            variants.add(jsonArray.getString(x));
        }
        setVariants(variants);

        setVersion(jsonObject.getString(VERSION));

        JSONObject jsonRatings = jsonObject.getJSONObject(RATINGS);
        setRating("popularity", jsonRatings.getInt(POPULARITY));
        setRating("style", jsonRatings.getInt(STYLE));
        setRating("trending", jsonRatings.getInt(TRENDING));
    }

    public LocalWebfont(String in) throws JSONException {
        this(new JSONObject(in));
    }

    public LocalWebfont(Parcel in) throws JSONException {
        this(in.readString());
    }

    public static final Creator<LocalWebfont> CREATOR = new Creator<LocalWebfont>() {
        public LocalWebfont createFromParcel(Parcel in) {
            try {
                return new LocalWebfont(in);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        public LocalWebfont[] newArray(int size) {
            return new LocalWebfont[size];
        }
    };

    public static final String CATEGORY = "category";
    public static final String FAMILY = "family";
    public static final String FILES = "files";
    public static final String KIND = "kind";
    public static final String LAST_MODIFIED = "lastModified";
    public static final String SUBSETS = "subsets";
    public static final String VARIANTS = "variants";
    public static final String VERSION = "version";
    public static final String RATINGS = "ratings";

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public void setCategory(String in) {
        category = in;
        try {
            jsonObject.put(CATEGORY, category);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setFamily(String in) {
        family = in;
        familyDirectory = new File(fontDir.getPath() + "/" + category + "/" + family);
        if (!familyDirectory.exists())
            Log.i(TAG, "Make " + family + " Directory: " + familyDirectory.mkdirs());
        try {
            jsonObject.put(FAMILY, family);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setFiles(Map<String, String> in) {
        files = in;
//        for (Map.Entry<String, String> entry : files.entrySet()) {
//            File file = getFile((String) entry.getKey());
//            if (file.exists())
//                entry.setValue(file.getPath());
//        }
        try {
            jsonObject.put(FILES, new JSONObject(files));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setKind(String in) {
        kind = in;
        try {
            jsonObject.put(KIND, kind);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setLastModified(DateTime in) {
        lastModified = in;
        try {
            jsonObject.put(LAST_MODIFIED, lastModified.getValue());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setSubsets(List<String> in) {
        subsets = in;
        try {
            jsonObject.put(SUBSETS, new JSONArray(subsets));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setVariants(List<String> in) {
        variants = in;
        try {
            jsonObject.put(VARIANTS, new JSONArray(variants));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setVersion(String in) {
        version = in;
        try {
            jsonObject.put(VERSION, version);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setRating(String sortOrder, int rank) {
        ratings.put(sortOrder, rank);
        try {
            jsonObject.put(RATINGS, new JSONObject(ratings));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setRatings(Map<String, Integer> in) {
        ratings = new TreeMap<>(in);
        try {
            jsonObject.put(RATINGS, new JSONObject(ratings));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getCategory() {
        return category;
    }

    public String getFamily() {
        return family;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public String getKind() {
        return kind;
    }

    public DateTime getLastModified() {
        return lastModified;
    }

    public List<String> getSubsets() {
        return subsets;
    }

    public List<String> getVariants() {
        return variants;
    }

    public String getVersion() {
        return version;
    }

    public TreeMap<String, Integer> getRatings() {
        return ratings;
    }

    public boolean downloadFont(String variant, boolean force) throws IOException {
        if (files.containsKey(variant)) {
            URL u = new URL(files.get(variant));
            if (u.getProtocol().equals("http")) {
                if (!familyDirectory.exists()) {
                    Log.i(TAG, "Create new " + family + ": " + familyDirectory.mkdirs());
                    familyDirectory.setReadable(true, false);
//                familyDirectory.setWritable(true, false);
                    Log.i(TAG, "Family path: " + familyDirectory.getAbsolutePath() + " Is dir: " + familyDirectory.isDirectory());
                }

                File file = getFile(variant);
                if (!file.exists()) {
                    Log.i(TAG, "Create new " + family + " | " + variant + ": " + file.createNewFile());
                    Log.i(TAG, "Font path: " + file.getAbsolutePath() + " file exist: " + file.exists());
                    force = true;
                }

                if (force) {
                    Log.i(TAG, "URL: " + u);
                    InputStream is = u.openStream();

                    DataInputStream dis = new DataInputStream(is);

                    byte[] buffer = new byte[4096];
                    int length;

                    FileOutputStream fos = new FileOutputStream(file, false);
                    while ((length = dis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.close();
                    Log.i(TAG, "DONE");
                    return true;
                } else {
                    Log.i(TAG, "Already Downloaded");
                }
            }

        }
        return false;
    }

    public File getFile(String variant) {
        variant += ".ttf";
        return new File(familyDirectory + "/" + variant);
    }

    public static File getFontFile(String category, String family, String variant) {
        return new File(fontDir.getPath() + "/" + category + "/" + family + "/" + variant + ".ttf");
    }

    public static File getFontFile(String family, String variant) {
        File category = new File(fontDir.getPath() + "/" + SANS_SERIF);
        ArrayList<String> contains = new ArrayList<>();
        Collections.addAll(contains, category.list());
        if (contains.contains(family))
            return new File(category.getPath() + "/" + family + "/" + variant + ".ttf");

        category = new File(fontDir.getPath() + "/" + SERIF);
        contains.clear();
        Collections.addAll(contains, category.list());
        if (contains.contains(family))
            return new File(category.getPath() + "/" + family + "/" + variant + ".ttf");

        category = new File(fontDir.getPath() + "/" + DISPLAY);
        contains.clear();
        Collections.addAll(contains, category.list());
        if (contains.contains(family))
            return new File(category.getPath() + "/" + family + "/" + variant + ".ttf");

        category = new File(fontDir.getPath() + "/" + HANDWRITING);
        contains.clear();
        Collections.addAll(contains, category.list());
        if (contains.contains(family))
            return new File(category.getPath() + "/" + family + "/" + variant + ".ttf");

        category = new File(fontDir.getPath() + "/" + MONOSPACE);
        contains.clear();
        Collections.addAll(contains, category.list());
        if (contains.contains(family))
            return new File(category.getPath() + "/" + family + "/" + variant + ".ttf");

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LocalWebfont && ((LocalWebfont) o).getFamily().equals(getFamily()))
            return true;
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getJsonObject().toString());
    }
}
