package com.patronia.taller;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class CaptureProvider extends ContentProvider {
  @Override public boolean onCreate(){return true;}
  private File checked(Uri u)throws FileNotFoundException{
    String p=u.getLastPathSegment();
    if(p==null||!p.matches("snap_[0-9]+\\.jpg"))throw new FileNotFoundException("Ruta incorrecta");
    return new File(getContext().getCacheDir(),p);
  }
  @Override public String getType(Uri u){return "image/jpeg";}
  @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String sortOrder){
    String[] keys=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
    MatrixCursor c=new MatrixCursor(keys);Object[] row=new Object[keys.length];
    for(int i=0;i<keys.length;i++){
      if(OpenableColumns.DISPLAY_NAME.equals(keys[i]))row[i]="foto-temporal.jpg";
      else if(OpenableColumns.SIZE.equals(keys[i]))try{row[i]=checked(u).length();}catch(Exception ignored){row[i]=0L;}
    }
    c.addRow(row);return c;
  }
  @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{
    return ParcelFileDescriptor.open(checked(u),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE);
  }
  @Override public Uri insert(Uri u,ContentValues values){return null;}
  @Override public int delete(Uri u,String selection,String[] args){return 0;}
  @Override public int update(Uri u,ContentValues values,String selection,String[] args){return 0;}
}