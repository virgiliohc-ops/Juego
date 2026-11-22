package com.patronia.taller;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Base64;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public final class MainActivity extends Activity {
  private static final int CAMERA = 601, GALLERY = 602, SAVE = 603;
  private static final String AUTH = "com.patronia.taller.v05.capture";
  private WebView web;
  private ValueCallback<Uri[]> chooser;
  private Uri capturedUri;
  private File capturedFile;
  private byte[] pendingSave;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(220,239,229));
    getWindow().setNavigationBarColor(Color.rgb(243,247,244));
    getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    web = new WebView(this);
    web.setBackgroundColor(Color.rgb(251,250,247));
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(true);
    web.getSettings().setAllowContentAccess(true);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params) {
        if (chooser!=null) chooser.onReceiveValue(null);
        chooser=callback;
        if(params.isCaptureEnabled()) return openCamera();
        return openGallery();
      }
    });
    web.addJavascriptInterface(new Bridge(),"PatronAndroid");
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
  }
  private void cancel(){if(chooser!=null){chooser.onReceiveValue(null);chooser=null;}}
  private boolean openCamera() {
    try {
      if(capturedFile!=null) capturedFile.delete();
      capturedFile=new File(getCacheDir(),"snap_"+System.currentTimeMillis()+".jpg");
      if(!capturedFile.createNewFile())throw new IOException("No se pudo crear la foto temporal");
      capturedUri=Uri.parse("content://"+AUTH+"/"+capturedFile.getName());
      Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      i.putExtra(MediaStore.EXTRA_OUTPUT,capturedUri);
      i.setClipData(ClipData.newUri(getContentResolver(),"PatronIA temporal",capturedUri));
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      startActivityForResult(i,CAMERA);
    } catch (Exception e) { cancel();Toast.makeText(this,"No se pudo abrir la cámara",Toast.LENGTH_LONG).show(); }
    return true;
  }
  private boolean openGallery() {
    try {
      Intent i=new Intent(Intent.ACTION_GET_CONTENT);
      i.setType("image/*");
      i.addCategory(Intent.CATEGORY_OPENABLE);
      startActivityForResult(Intent.createChooser(i,"Seleccionar foto"),GALLERY);
    }catch(Exception e){cancel();Toast.makeText(this,"No se pudo abrir la galería",Toast.LENGTH_LONG).show();}
    return true;
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(req==CAMERA||req==GALLERY){
      Uri u = result==RESULT_OK?(req==CAMERA?capturedUri:(data==null?null:data.getData())):null;
      if(chooser!=null){chooser.onReceiveValue(u==null?null:new Uri[]{u});chooser=null;}
      if(req==CAMERA&&result!=RESULT_OK&&capturedFile!=null){capturedFile.delete();capturedFile=null;}
    } else if(req==SAVE){
      if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingSave!=null){
        try(OutputStream os=getContentResolver().openOutputStream(data.getData())){if(os!=null)os.write(pendingSave);}
        catch(Exception e){Toast.makeText(this,"No se pudo guardar el archivo",Toast.LENGTH_LONG).show();}
      }
      pendingSave=null;
    }
  }
  public final class Bridge {
    @JavascriptInterface public void saveFile(String filename,String mime,String base64) {
      try {
        byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
        runOnUiThread(()->{
          try {
            pendingSave=bytes;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType(mime==null||mime.isEmpty()?"application/octet-stream":mime);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,filename);
            startActivityForResult(i,SAVE);
          } catch(Exception e) {Toast.makeText(MainActivity.this,"No se pudo abrir Guardar como",Toast.LENGTH_LONG).show();}
        });
      }catch(Exception ignored){}
    }
  }
  @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){cancel();if(web!=null)web.destroy();if(capturedFile!=null)capturedFile.delete();super.onDestroy();}
}