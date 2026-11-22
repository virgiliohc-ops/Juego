package com.patronia.taller;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.Window;
import java.io.OutputStream;

public class MainActivity extends Activity {
  private static final int PICK_IMAGE=4301, SAVE_FILE=4302;
  private ValueCallback<Uri[]> chosenImages;
  private WebView web;
  private byte[] pendingData;
  private String pendingName;
  private class AndroidBridge {
    @JavascriptInterface public void saveFile(String name,String mime,String b64){
      if(b64==null || b64.length()>32_000_000) return;
      try {
        final byte[] bytes=Base64.decode(b64,Base64.DEFAULT);
        runOnUiThread(()->{
          pendingData=bytes; pendingName=name;
          Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType((mime==null || mime.isEmpty())?"application/octet-stream":mime);
          i.putExtra(Intent.EXTRA_TITLE, name);
          try { startActivityForResult(i,SAVE_FILE); } catch(Exception e){Toast.makeText(MainActivity.this,"No se pudo abrir el selector de archivos",Toast.LENGTH_LONG).show();}
        });
      }catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"No se pudo preparar el documento",Toast.LENGTH_LONG).show());}
    }
  }
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    try{
      getWindow().setStatusBarColor(0xffdcefe5);
      getWindow().setNavigationBarColor(0xffdcefe5);
      web=new WebView(this);
      web.getSettings().setJavaScriptEnabled(true);
      web.getSettings().setDomStorageEnabled(true);
      web.getSettings().setAllowFileAccess(true);
      web.getSettings().setAllowContentAccess(true);
      web.setWebViewClient(new WebViewClient());
      web.setWebChromeClient(new WebChromeClient(){
        @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
          if(chosenImages!=null)chosenImages.onReceiveValue(null);
          chosenImages=callback;
          try{
            Intent intent=params.createIntent();
            startActivityForResult(intent,PICK_IMAGE);
            return true;
          }catch(Exception ex){chosenImages=null;return false;}
        }
      });
      web.addJavascriptInterface(new AndroidBridge(),"PatronAndroid");
      setContentView(web);
      web.loadUrl("file:///android_asset/index.html");
    }catch(Throwable t){
      android.widget.TextView error=new android.widget.TextView(this);
      error.setPadding(30,30,30,30);
      error.setText("PatronIA no pudo iniciar: "+t.getClass().getSimpleName()+" "+t.getMessage());
      setContentView(error);
    }
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==PICK_IMAGE){
      if(chosenImages!=null){
        Uri[] uris=WebChromeClient.FileChooserParams.parseResult(result,data);
        chosenImages.onReceiveValue(uris);
        chosenImages=null;
      }
      return;
    }
    if(request==SAVE_FILE){
      if(result==RESULT_OK && data!=null && data.getData()!=null && pendingData!=null){
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
          if(out==null)throw new IllegalStateException("El destino no permite escritura");
          out.write(pendingData);
          Toast.makeText(this,"Guardado: "+pendingName,Toast.LENGTH_LONG).show();
        }catch(Exception ex){Toast.makeText(this,"Error al guardar: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
      }
      pendingData=null; pendingName=null;
    }
  }
  @Override public void onBackPressed(){
    if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();
  }
}