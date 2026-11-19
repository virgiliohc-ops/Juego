package es.mando.lg;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okio.ByteString;

public class MainActivity extends Activity {
  final int BG=Color.rgb(244,248,246), GREEN=Color.rgb(205,234,221), LAV=Color.rgb(234,227,244), INK=Color.rgb(40,64,65);
  SharedPreferences prefs;
  TextView status;
  EditText ipBox;
  WebSocket control,pointer;
  OkHttpClient secure,legacy;
  String ip="", currentCert="";
  volatile boolean connected=false,pointerReady=false,muted=false;
  int id=0;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs=getSharedPreferences("remote",MODE_PRIVATE);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(20),dp(18),dp(28)); scroll.addView(root);setContentView(scroll);
    addTitle(root,"MI MANDO LG",14);
    addTitle(root,"Control de tu televisor",24);
    addText(root,"Prototipo local · LG webOS · Wi-Fi");
    ipBox=new EditText(this);
    ipBox.setSingleLine(true);ipBox.setText(prefs.getString("ip",""));ipBox.setHint("IP de la TV, p. ej. 192.168.1.50");
    ipBox.setTextSize(16);ipBox.setPadding(dp(15),dp(8),dp(15),dp(8));
    root.addView(ipBox,new LinearLayout.LayoutParams(-1,dp(55)));
    row(root,{"Conectar"},new Runnable[]{()->connect()});
    status=addText(root,"Móvil y TV deben estar en la misma red.");
    addTitle(root,"Encendido y navegación",17);
    row(root,new String[]{"⏻ Apagar","Inicio","Atrás"}, new Runnable[]{()->req("ssap://system/turnOff",null),()->key("HOME"),()->key("BACK")});
    row(root,new String[]{"","▲",""},new Runnable[]{()->{},()->key("UP"),()->{}});
    row(root,new String[]{"◀","OK","▶"},new Runnable[]{()->key("LEFT"),()->key("ENTER"),()->key("RIGHT")});
    row(root,new String[]{"","▼",""},new Runnable[]{()->{},()->key("DOWN"),()->{}});
    addTitle(root,"Volumen y canales",17);
    row(root,new String[]{"VOL +","Silencio","CH +"},new Runnable[]{()->req("ssap://audio/volumeUp",null),()->toggleMute(),()->req("ssap://tv/channelUp",null)});
    row(root,new String[]{"VOL −","▶/⏸","CH −"},new Runnable[]{()->req("ssap://audio/volumeDown",null),()->key("PLAY"),()->req("ssap://tv/channelDown",null)});
    addTitle(root,"Números",17);
    for(int n=1;n<=9;n+=3) { final int a=n;row(root,new String[]{""+a,""+(a+1),""+(a+2)},new Runnable[]{()->key(""+a),()->key(""+(a+1)),()->key(""+(a+2))});}
    row(root,new String[]{"","0",""},new Runnable[]{()->{},()->key("0"),()->{}});
    addText(root,"Para vincular, introduce la IP de la LG, pulsa Conectar y acepta el aviso en la televisión. El encendido desde apagado no está incluido en este prototipo.");
  }
  int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+.5f);}
  void addTitle(LinearLayout root,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTypeface(null,Typeface.BOLD);t.setTextColor(INK);t.setPadding(0,dp(12),0,dp(8));root.addView(t);}
  TextView addText(LinearLayout root,String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(INK);t.setTextSize(13);t.setPadding(0,dp(5),0,dp(12));root.addView(t);return t;}
  void row(LinearLayout parent,String[] labels,Runnable[] actions){
    LinearLayout row=new LinearLayout(this);row.setOrientation(0);
    for(int i=0;i<labels.length;i++){Button btn=new Button(this);btn.setText(labels[i]);btn.setTextSize(14);btn.setTextColor(INK);btn.setAllCaps(false);
      GradientDrawable shape=new GradientDrawable();shape.setColor(i==1?GREEN:LAV);shape.setCornerRadius(dp(14));btn.setBackground(shape);
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(55),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(btn,p);
      if(labels[i].length()>0){final Runnable act=actions[i];btn.setOnClickListener(v->act.run());}else btn.setVisibility(View.INVISIBLE);
    }
    parent.addView(row);
  }
  void state(String s){runOnUiThread(()->{if(status!=null)status.setText(s);});}
  boolean local(String x){
    try {String[] p=x.split("\\.");if(p.length!=4)return false;int[] n=new int[4];for(int i=0;i<4;i++){n[i]=Integer.parseInt(p[i]);if(n[i]<0||n[i]>255)return false;}
      return n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168)||(n[0]==169&&n[1]==254);
    } catch(Exception e){return false;}
  }
  synchronized void connect(){
    String next=ipBox.getText().toString().trim();
    if(!local(next)){state("Escribe la IP local de tu TV.");return;}
    connected=false;pointerReady=false;if(control!=null)control.cancel();if(pointer!=null)pointer.cancel();ip=next;
    prefs.edit().putString("ip",ip).apply();
    try {
      X509TrustManager tm=new X509TrustManager(){
        public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
        public void checkClientTrusted(X509Certificate[] x,String y){}
        public void checkServerTrusted(X509Certificate[] cs,String a)throws CertificateException{
          if(cs==null||cs.length==0)throw new CertificateException("Sin certificado");
          try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(cs[0].getEncoded());StringBuilder sb=new StringBuilder();
            for(byte b:digest)sb.append(String.format("%02x",b&255));String hash=sb.toString();
            String old=prefs.getString("pin:"+ip,"");if(!old.isEmpty()&&!old.equals(hash))throw new CertificateException("Certificado LG diferente");
            currentCert=hash;
          }catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException(e);}
        }
      };
      SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{tm},new SecureRandom());
      secure=new OkHttpClient.Builder().sslSocketFactory(tls.getSocketFactory(),tm).hostnameVerifier((h,s)->h.equals(ip)).connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
      legacy=new OkHttpClient.Builder().connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
      open(false);
    }catch(Exception e){state("No se pudo preparar conexión: "+e.getMessage());}
  }
  void open(boolean ws){
    state(ws?"Probando conexión antigua…":"Conectando a LG…");
    OkHttpClient client=ws?legacy:secure;
    String url=(ws?"ws://":"wss://")+ip+(ws?":3000":":3001");
    control=client.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){
      @Override public void onOpen(WebSocket w,Response r){state("Acepta la solicitud en tu televisor LG.");try{JSONObject p=new JSONObject();p.put("pairingType","PROMPT");p.put("forcePairing",false);p.put("manifest",new JSONObject(readManifest()));String k=prefs.getString("key:"+ip,"");if(!k.isEmpty())p.put("client-key",k);
          JSONObject request=new JSONObject();request.put("type","register");request.put("id","reg_1");request.put("payload",p);w.send(request.toString());
        }catch(Exception e){state("Error de registro: "+e.getMessage());}}
      @Override public void onMessage(WebSocket w,String body){handle(body);}
      @Override public void onFailure(WebSocket w,Throwable t,Response r){
        connected=false;pointerReady=false;
        if(!ws&&prefs.getString("pin:"+ip,"").isEmpty()){open(true);}else state("No se conecta: comprueba IP, Wi-Fi y permisos LG. "+t.getClass().getSimpleName());
      }
      @Override public void onClosed(WebSocket w,int c,String reason){connected=false;pointerReady=false;state("TV desconectada");}
    });
  }
  String readManifest() throws Exception{
    java.io.InputStream in=getAssets().open("manifest_signed.json");
    java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] block=new byte[4096];int n;
    while((n=in.read(block))!=-1)b.write(block,0,n);in.close();
    return b.toString("UTF-8");
  }
  void handle(String content){
    try{JSONObject msg=new JSONObject(content);String type=msg.optString("type"),rid=msg.optString("id");JSONObject p=msg.optJSONObject("payload");
      if("registered".equals(type)){connected=true;if(p!=null){String key=p.optString("client-key","");if(!key.isEmpty())prefs.edit().putString("key:"+ip,key).apply();}
        if(!currentCert.isEmpty())prefs.edit().putString("pin:"+ip,currentCert).apply();
        state("LG conectada. Ya puedes usar el mando.");
        req("ssap://com.webos.service.networkinput/getPointerInputSocket",null);
      } else if("error".equals(type)){state("Error LG: "+msg.optString("error","solicitud rechazada"));}
      else if(p!=null&&p.has("socketPath")) {
        String socket=p.optString("socketPath");OkHttpClient client=socket.startsWith("wss://")?secure:legacy;
        pointer=client.newWebSocket(new Request.Builder().url(socket).build(),new WebSocketListener(){
          @Override public void onOpen(WebSocket w,Response r){pointerReady=true;state("LG conectada · Navegación lista");}
          @Override public void onFailure(WebSocket w,Throwable t,Response r){pointerReady=false;state("Conectada, pero las flechas no están disponibles.");}
        });
      }
    }catch(Exception e){state("Respuesta LG no reconocida.");}
  }
  void req(String uri,JSONObject payload){
    if(!connected||control==null){state("Primero conecta la televisión.");return;}
    try{JSONObject m=new JSONObject();m.put("type","request");m.put("id","cmd_"+(++id));m.put("uri",uri);if(payload!=null)m.put("payload",payload);control.send(m.toString());}
    catch(Exception e){state("Error enviando orden.");}
  }
  void key(String k){
    if(!pointerReady||pointer==null){state("Conecta la TV y autoriza el control del mando.");return;}
    pointer.send("type:button\nname:"+k+"\n\n");
  }
  void toggleMute(){try{muted=!muted;JSONObject p=new JSONObject();p.put("mute",muted);req("ssap://audio/setMute",p);}catch(Exception e){state("No se pudo activar silencio");}}
  @Override protected void onDestroy(){if(control!=null)control.close(1000,"bye");if(pointer!=null)pointer.close(1000,"bye");super.onDestroy();}
}