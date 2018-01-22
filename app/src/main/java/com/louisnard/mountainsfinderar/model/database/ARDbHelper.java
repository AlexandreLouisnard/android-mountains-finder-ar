package com.louisnard.mountainsfinderar.model.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.louisnard.mountainsfinderar.BuildConfig;
import com.louisnard.mountainsfinderar.model.objects.Point;
import com.louisnard.mountainsfinderar.model.services.PointService;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for managing the {@link SQLiteDatabase}.<br>
 *
 * @author Alexandre Louisnard
 */
public class ARDbHelper extends SQLiteOpenHelper {

    // Tag
    private static final String TAG = ARDbHelper.class.getSimpleName();

    // Database information
    private static final String DATABASE_NAME = "database.db";
    private static final int DATABASE_VERSION = 1;

    // Singleton pattern
    private static ARDbHelper sInstance;

    /**
     * {@link ARDbHelper} asynchronous operations listener.
     */
    public interface ARDbHelperListener {
        /**
         * Called whenever an {@link ARDbHelper} asynchronous {@link Point}s insertion operation completes.
         * @param insertedPointsNumber the number of inserted {@link Point}s.
         */
        void onPointsInserted(long insertedPointsNumber);
    }

    // SQL requests
    private static final String SQL_CREATE_TABLE_POINTS = "CREATE TABLE " + ARDbContract.PointsColumns.TABLE_NAME
            + " (" + ARDbContract.PointsColumns._ID + " INTEGER PRIMARY KEY,"
            + ARDbContract.PointsColumns.COLUMN_NAME + " TEXT,"
            + ARDbContract.PointsColumns.COLUMN_DESCRIPTION + " TEXT,"
            + ARDbContract.PointsColumns.COLUMN_LATITUDE + " REAL,"
            + ARDbContract.PointsColumns.COLUMN_LONGITUDE + " REAL,"
            + ARDbContract.PointsColumns.COLUMN_ALTITUDE + " INTEGER,"
            + "UNIQUE(" + ARDbContract.PointsColumns.COLUMN_LATITUDE + "," + ARDbContract.PointsColumns.COLUMN_LONGITUDE + "," + ARDbContract.PointsColumns.COLUMN_ALTITUDE + ") ON CONFLICT REPLACE)";

    /**
     * Constructs a new instance of {@link ARDbHelper}.<br>
     * Private constructor to prevent accidental instantiation.
     * @param applicationContext the {@link Context} to use to open or create the database.
     */
    private ARDbHelper(Context applicationContext) {
        super(applicationContext, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Initializes if necessary and returns the singleton instance of {@link ARDbHelper}.
     * @param applicationContext the application context to avoid leaking an activity context.
     * @return the singleton instance of {@link ARDbHelper}.
     */
    public static synchronized ARDbHelper getInstance(Context applicationContext) {
        if (sInstance == null) {
            sInstance = new ARDbHelper(applicationContext);
        }
        return sInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_POINTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing to do for the moment
    }

    /**
     * Static method that returns the database name.
     * @return the database name.
     */
    public static String getDbName() {
        return DATABASE_NAME;
    }

    /**
     * Clears the given table.
     * @param tableName the table name.
     */
    public void clearTable(String tableName) {
        final SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + tableName);
        db.close();
    }

    /**
     * Returns all points from the {@link SQLiteDatabase}.
     * @return the {@link List<Point>}.
     */
    public List<Point> getAllPoints() {
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.query(ARDbContract.PointsColumns.TABLE_NAME, null, null, null, null, null, null);
        final List<Point> points = new ArrayList<>();
        while (cursor.moveToNext()) {
            points.add(new Point(cursor));
        }
        cursor.close();
        db.close();
        return points;
    }

    /**
     * Returns all points from the {@link SQLiteDatabase} around the given {@link Point}.<br>
     * Actually, the points are located in a square of size 2x{@param distance} and centered on the given {@param point}.
     * @param location the {@link Location} around which the points have to be located.
     * @param distance the half-size of the square around the {@link Point} where the points have to be located.
     * @return the {@link List<Point>} of all points located around the given {@link Point}.
     */
    public List<Point> getPointsAround(Location location, int distance) {
        // Delimit the square within which to find points
        final String latMin = String.valueOf((location.getLatitude() - PointService.metersToDegrees(distance)) % 90);
        final String latMax = String.valueOf((location.getLatitude() + PointService.metersToDegrees(distance)) % 90);
        final String lonMin = String.valueOf((location.getLongitude() - PointService.metersToDegrees(distance)) % 180);
        final String lonMax = String.valueOf((location.getLongitude() + PointService.metersToDegrees(distance)) % 180);
        // Read database
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.query(ARDbContract.PointsColumns.TABLE_NAME, null,
                ARDbContract.PointsColumns.COLUMN_LATITUDE + " >= ? AND " + ARDbContract.PointsColumns.COLUMN_LATITUDE + " <= ? AND " + ARDbContract.PointsColumns.COLUMN_LONGITUDE + " >= ? AND " + ARDbContract.PointsColumns.COLUMN_LONGITUDE + " <= ?",
                new String[] {latMin, latMax, lonMin, lonMax}, null, null, null);
        final List<Point> points = new ArrayList<>();
        while (cursor.moveToNext()) {
            points.add(new Point(cursor));
        }
        cursor.close();
        db.close();
        return points;
    }

    /**
     * Returns the points from the {@link SQLiteDatabase} whose name contains the given name.
     * @param name the name to search for.
     * @return the {@link List<Point>}.
     */
    public List<Point> findPointsByName(String name) {
        // Read database
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.query(ARDbContract.PointsColumns.TABLE_NAME, null, ARDbContract.PointsColumns.COLUMN_NAME + " LIKE '%" + name + "%'", null, null, null, null);
        final List<Point> points = new ArrayList<>();
        while (cursor.moveToNext()) {
            points.add(new Point(cursor));
        }
        cursor.close();
        db.close();
        return points;
    }

    /**
     * Adds the given {@link Point} to the {@link SQLiteDatabase}.
     * @param point the {@link Point} to insert.
     * @return the row id of the newly inserted row, or -1 if an error occurred.
     */
    public long addPoint(Point point) {
        final SQLiteDatabase db = getWritableDatabase();
        final long result = insertPoint(point, db);
        db.close();
        return result;
    }

    /**
     * Adds the given {@link List<Point>} to the {@link SQLiteDatabase}.
     * @param points the {@link List<Point>} to insert.
     * @return the number of successfully inserted rows, or -1 if an error occurred on one or many rows.
     */
    public long addPoints(List<Point> points) {
        final SQLiteDatabase db = getWritableDatabase();
        long result = 0;
        for (Point point : points) {
            if (insertPoint(point, db) != -1 && result != -1) {
                result++;
            } else {
                result = -1;
            }
        }
        db.close();
        return result;
    }

    /**
     * Inserts a {@link Point} in the given {@link SQLiteDatabase}.<br>
     * The {@link SQLiteDatabase} must be closed after calling this function.
     * @param point the {@link List<Point>} to insert.
     * @param db the {@link SQLiteDatabase} to insert the point into.
     * @return the row id of the newly inserted row, or -1 if an error occurred.
     */
    private long insertPoint(Point point, SQLiteDatabase db) {
        final ContentValues values = new ContentValues();
        values.put(ARDbContract.PointsColumns.COLUMN_NAME, point.getName());
        values.put(ARDbContract.PointsColumns.COLUMN_DESCRIPTION, point.getDescription());
        values.put(ARDbContract.PointsColumns.COLUMN_LATITUDE, point.getLatitude());
        values.put(ARDbContract.PointsColumns.COLUMN_LONGITUDE, point.getLongitude());
        values.put(ARDbContract.PointsColumns.COLUMN_ALTITUDE, point.getAltitude());
        final long result = db.insert(ARDbContract.PointsColumns.TABLE_NAME, null, values);
        if (result == -1) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Error inserting the point: \"" + point.getName() + "\" into the database");
        }
        return result;
    }

    /**
     * Adds the given {@link List<Point>} to the {@link SQLiteDatabase} asynchronously.
     * @param points the {@link List<Point>} to insert.
     * @param listener the {@link ARDbHelperListener} to notify when the operation completes.
     * @return the number of successfully inserted rows, or -1 if an error occurred on one or many rows.
     */
    public void addPointsAsynchronously(List<Point> points, ARDbHelperListener listener) {
        InsertPointsAsynchronously insertPoints = new InsertPointsAsynchronously(points, listener);
        insertPoints.execute();
    }

    /**
     * Insert {@link Point}s asynchronously.
     */
    private class InsertPointsAsynchronously extends AsyncTask<Void, Void, Void> {

        private List<Point> mPoints;
        private ARDbHelperListener mListener;
        private long mInsertedPointsNumber;

        public InsertPointsAsynchronously (List<Point> points, ARDbHelperListener listener) {
            mPoints = points;
            mListener = listener;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mInsertedPointsNumber = addPoints(mPoints);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mListener.onPointsInserted(mInsertedPointsNumber);
        }
    }
}