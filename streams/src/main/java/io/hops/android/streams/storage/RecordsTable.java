package io.hops.android.streams.storage;

import io.hops.android.streams.records.Record;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;


public class RecordsTable extends Table{

    private static final String TABLE_NAME = "records";

    // Record details to query for
    private static final Column ID = new Column("_id", "INTEGER PRIMARY KEY");
    private static final Column RECORD_UUID = new Column("recordUUID", "TEXT");
    private static final Column BOOT_NUM =  new Column("bootNum", "INTEGER");
    private static final Column BOOT_MILLIS = new Column("bootMillis", "INTEGER");
    private static final Column EPOCH_MILLIS = new Column("epochMillis", "INTEGER");
    private static final Column ACKED = new Column("acked", "INTEGER");

    // Metadata of the record (Saves the class of the Record)
    private static final Column TYPE = new Column("type", "TEXT");

    // This is where the actual record is stored in json format
    private static final Column PAYLOAD = new Column("payload", "TEXT");

    @Override
    protected String getTableName(){
        return TABLE_NAME;
    }

    @Override
    protected Column[] getColumns() {
        Column[] columns = {
                ID, RECORD_UUID, BOOT_NUM, BOOT_MILLIS, EPOCH_MILLIS, ACKED, TYPE, PAYLOAD};
        return columns;
    }

    private static String[] getColumnNames(){
        Column[] columns = new RecordsTable().getColumns();
        String[] columnsAsString = new String[columns.length];
        for (int i=0; i<columns.length; i++){
            columnsAsString[i] = columns[i].name;
        }
        return columnsAsString;
    }

    private static ContentValues getContentValues(Record record){
        ContentValues values = new ContentValues();
        values.put(RECORD_UUID.name, record.getRecordUUID());
        values.put(BOOT_NUM.name, record.getBootNum());
        values.put(BOOT_MILLIS.name, record.getBootMillis());
        values.put(EPOCH_MILLIS.name, record.getEpochMillis());
        values.put(ACKED.name, record.getAcked());
        values.put(TYPE.name, record.getClassType());
        values.put(PAYLOAD.name, record.toJson());

        return values;
    }

    //------------------------ SINGLE RECORD OPS --------------------------------------------------

    public static boolean insert(SQLiteDatabase db, Record record){
        return (db.insert(TABLE_NAME, null, getContentValues(record)) != -1);
    }

    public static boolean update(SQLiteDatabase db, Record record){
        String whereClause = RECORD_UUID.name + "= ?";
        String[] whereArgs = new String[] { record.getRecordUUID() };
        return (db.update(
                TABLE_NAME, getContentValues(record), whereClause, whereArgs) > 0);
    }

    public static boolean write(SQLiteDatabase db, Record record){
        return update(db, record) || insert(db, record);
    }

    public static boolean delete(SQLiteDatabase db, String recordUUID){
        String whereClause = RECORD_UUID.name + "= ?";
        String[] whereArgs = new String[] { recordUUID };
        return (db.delete(TABLE_NAME, whereClause, whereArgs) > 0);
    }

    public static Record read(SQLiteDatabase db, String recordUUID, Class<? extends Record> type){
        Cursor cursor = null;
        try{
            String[] columns = {ACKED.name, PAYLOAD.name};
            String selection = RECORD_UUID.name + " = ? AND " + TYPE.name + " = ?";
            String[] selectionArgs = {recordUUID, type.getName()};

            cursor = db.query(
                    TABLE_NAME, columns, selection, selectionArgs, null, null, null, null);
            if (cursor.moveToFirst()){
                String payload = cursor.getString(cursor.getColumnIndex(PAYLOAD.name));
                long acked = cursor.getLong(cursor.getColumnIndex(PAYLOAD.name));
                Object temp = Record.fromJson(payload, type);
                if (temp != null){
                    Record record = (Record) temp;
                    record.setAcked(acked);
                    return record;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if (cursor != null){
                cursor.close();
            }
        }
        return null;
    }

    //------------------------ MULTI RECORD OPS --------------------------------------------------

    public static ArrayList<Record> readRecords(
            SQLiteDatabase db, Class<? extends Record> type,
            String selection, String[] selectionArgs,
            String orderBy, String limit){

        Cursor cursor = null;

        try{
            cursor = db.query(
                    TABLE_NAME,
                    RecordsTable.getColumnNames(),
                    selection,
                    selectionArgs,
                    null,
                    null,
                    orderBy,
                    limit);

            ArrayList<Record> records = new ArrayList<>();
            while(cursor.moveToNext()){
                String payload = cursor.getString(cursor.getColumnIndex(PAYLOAD.name));
                Object temp = Record.fromJson(payload, type);
                if (temp != null){
                    Record record = (Record) temp;
                    record.setAcked(cursor.getLong(cursor.getColumnIndex(ACKED.name)));
                    records.add((Record) temp);
                }
            }
            return records;
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if (cursor != null){
                cursor.close();
            }
        }
        return null;
    }

    public static ArrayList<Record> readAll(
            SQLiteDatabase db, Class<? extends Record> type){
        String selection = TYPE.name + " = ?";
        String[] selectionArgs = {type.getName()};
        return readRecords(db, type, selection, selectionArgs, null, null);
    }

    public static ArrayList<Record> readAllNotAcked(
            SQLiteDatabase db, Class<? extends Record> type){
        String selection = TYPE.name + " = ? AND " + ACKED.name + " = ?";
        String[] selectionArgs = {type.getName(), "0"};
        return readRecords(db, type, selection, selectionArgs, null, null);
    }

    public static ArrayList<Record> readAllNotAckedLimit(
            SQLiteDatabase db, Class<? extends Record> type, long limit){
        String selection = TYPE.name + " = ? AND " + ACKED.name + " = ?";
        String orderBy = BOOT_NUM.name + ", " + BOOT_MILLIS;
        String limitStr = String.valueOf(limit);
        String[] selectionArgs = {type.getName(), "0"};
        return readRecords(db, type, selection, selectionArgs, orderBy, limitStr);
    }

    public static ArrayList<Record> readAllSinceReboot(
            SQLiteDatabase db, Class<? extends Record> type, long bootNum){
        String selection = TYPE.name + " = ? AND " + BOOT_NUM.name + " = ?";
        String[] selectionArgs = {type.getName(), String.valueOf(bootNum)};
        return readRecords(db, type, selection, selectionArgs, null, null);
    }

    public static ArrayList<Record> readAllSinceRebootWithoutEpoch(
            SQLiteDatabase db,  Class<? extends Record> type, long bootNum){
        String selection =
                TYPE.name + " = ? AND " +
                BOOT_NUM.name + " = ? AND " +
                EPOCH_MILLIS.name + " = ?";
        String[] selectionArgs = {type.getName(), String.valueOf(bootNum), "-1"};
        return readRecords(db, type, selection, selectionArgs, null, null);
    }


    public static boolean deleteAllRecords(
            SQLiteDatabase db, Class<? extends Record> type){
        String whereClause = TYPE.name + " = ?";
        String[] whereArgs = new String[] {type.getName()};
        return (db.delete(TABLE_NAME, whereClause, whereArgs) > 0);
    }

    public static boolean deleteAckedRecords(
            SQLiteDatabase db, Class<? extends Record> type){
        String whereClause = ACKED.name + "> ? AND " + TYPE.name + " = ?";
        String[] whereArgs = new String[] { "0", type.getName()};
        return (db.delete(TABLE_NAME, whereClause, whereArgs) > 0);
    }

}