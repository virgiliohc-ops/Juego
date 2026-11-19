package es.mando.lg;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import okhttp3.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import android.net.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
  private static final int CREAM=Color.rgb(246,247,244), DARK=Color.rgb(35,41,42), MINT=Color.rgb(215,233,222);
  private final HashMap<String,String> appIds=new HashMap<>();
  private SharedPreferences pref;
  private TextView status;
  private EditText ipField;
  private WebSocket control,pointer;
  private OkHttpClient secure,legacy;
  private String ip="", fingerprint="", queuedApp=null;
  private volatile boolean connected=false,pointerReady=false,muted=false;
  private int requestId=0,connectGeneration=0;
  private boolean gotApps=false;
  private volatile boolean searching=false;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    pref=getSharedPreferences("remote",MODE_PRIVATE);
    getWindow().setStatusBarColor(CREAM);
    getWindow().setNavigationBarColor(CREAM);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);scroll.setBackgroundColor(CREAM);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(12),dp(14),dp(18));
    scroll.addView(root);
    setContentView(scroll);

    TextView title=new TextView(this);
    title.setText("Mi mando LG");title.setTextColor(DARK);
    title.setTextSize(25);title.setTypeface(null,1);
    root.addView(title);
    TextView subtitle=new TextView(this);
    subtitle.setText("Edición Magic Remote  ·  webOS");
    subtitle.setTextSize(12);subtitle.setTextColor(Color.rgb(92,105,102));
    subtitle.setPadding(0,0,0,dp(9));root.addView(subtitle);

    LinearLayout connectBar=new LinearLayout(this);connectBar.setOrientation(LinearLayout.HORIZONTAL);
    ipField=new EditText(this);
    ipField.setSingleLine(true);ipField.setTextSize(14);
    ipField.setInputType(InputType.TYPE_CLASS_PHONE);
    ipField.setHint("IP de tu LG");
    ipField.setText(pref.getString("ip",""));
    ipField.setPadding(dp(13),dp(8),dp(8),dp(8));
    ipField.setBackground(background(Color.WHITE,14));
    LinearLayout.LayoutParams fieldParams=new LinearLayout.LayoutParams(0,dp(50),1);
    fieldParams.setMargins(0,0,dp(8),0);
    connectBar.addView(ipField,fieldParams);

    Button connect=new Button(this);
    connect.setText("Conectar");connect.setAllCaps(false);
    connect.setTextColor(Color.rgb(255,255,255));connect.setTextSize(14);
    connect.setBackground(background(Color.rgb(47,99,83),14));
    connect.setOnClickListener(v->connect());
    connectBar.addView(connect,new LinearLayout.LayoutParams(dp(106),dp(50)));
    root.addView(connectBar);
    Button autoFind=new Button(this);
    autoFind.setText("⌕  Buscar mi LG automáticamente");
    autoFind.setAllCaps(false);
    autoFind.setTextSize(16);
    autoFind.setTextColor(Color.rgb(33,78,63));
    autoFind.setBackground(background(MINT,15));
    LinearLayout.LayoutParams autoParams=new LinearLayout.LayoutParams(-1,dp(54));
    autoParams.setMargins(0,dp(8),0,dp(5));
    root.addView(autoFind,autoParams);
    autoFind.setOnClickListener(v->discoverTV());

    LinearLayout stateRow=new LinearLayout(this);stateRow.setOrientation(LinearLayout.HORIZONTAL);
    stateRow.setGravity(Gravity.CENTER_VERTICAL);
    status=new TextView(this);status.setText("Pulsa Buscar mi LG. No necesitas conocer su IP.");
    status.setTextColor(Color.rgb(76,95,90));status.setTextSize(12);
    status.setMaxLines(3);
    stateRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));
    TextView forget=new TextView(this);
    forget.setText("Olvidar");forget.setTextColor(Color.rgb(48,105,86));forget.setTextSize(12);
    forget.setPadding(dp(8),dp(10),dp(4),dp(10));
    forget.setOnClickListener(v->forgetPairing());
    stateRow.addView(forget);
    root.addView(stateRow);

    RemoteView remote=new RemoteView(this);
    root.addView(remote,new LinearLayout.LayoutParams(-1,-2));

    TextView foot=new TextView(this);
    foot.setText("La LG debe estar encendida y conectada a la misma red local que el móvil. Si no aparece en la búsqueda, puedes introducir su IP manualmente. Voz y Alexa no disponibles; encendido desde apagado aún no implementado.");
    foot.setTextSize(12);foot.setTextColor(Color.rgb(100,108,104));
    foot.setPadding(dp(8),dp(10),dp(8),dp(12));root.addView(foot);
  }

  private GradientDrawable background(int color,int radius){
    GradientDrawable g=new GradientDrawable();
    g.setColor(color);g.setCornerRadius(dp(radius));return g;
  }
  private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+0.5f);}
  private void state(String s){runOnUiThread(()->{if(status!=null)status.setText(s);});}
  private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
  private static boolean localAddress(String s){
    try{
      String[] parts=s.split("\\.");
      if(parts.length!=4)return false;
      int[] n=new int[4];
      for(int i=0;i<4;i++){if(parts[i].length()>3||parts[i].isEmpty())return false;n[i]=Integer.parseInt(parts[i]);if(n[i]<0||n[i]>255)return false;}
      return n[0]==10 || n[0]==172&&n[1]>=16&&n[1]<=31 || n[0]==192&&n[1]==168 || n[0]==169&&n[1]==254;
    }catch(Exception e){return false;}
  }

  private synchronized void connect(){
    String entered=ipField.getText().toString().trim();
    if(!localAddress(entered)){state("Introduce una IP privada válida de la LG.");return;}
    int generation=++connectGeneration;
    if(control!=null)control.cancel();
    if(pointer!=null)pointer.cancel();
    connected=false;pointerReady=false;gotApps=false;appIds.clear();queuedApp=null;
    ip=entered;fingerprint="";
    pref.edit().putString("ip",ip).apply();
    try{
      final X509TrustManager trust=new X509TrustManager(){
        public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
        public void checkClientTrusted(X509Certificate[] x,String a){}
        public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
          if(chain==null||chain.length==0)throw new CertificateException("Certificado ausente");
          try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
            StringBuilder sb=new StringBuilder();
            for(byte by:digest)sb.append(String.format(java.util.Locale.US,"%02x",by&255));
            String hash=sb.toString();
            String pinned=pref.getString("pin:"+ip,"");
            if(!pinned.isEmpty()&&!pinned.equals(hash))
              throw new CertificateException("Ha cambiado el certificado del televisor");
            fingerprint=hash;
          }catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException(e);}
        }
      };
      SSLContext ssl=SSLContext.getInstance("TLS");
      ssl.init(null,new TrustManager[]{trust},new SecureRandom());
      secure=new OkHttpClient.Builder()
        .sslSocketFactory(ssl.getSocketFactory(),trust)
        .hostnameVerifier((host,session)->host.equals(ip))
        .connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
      legacy=new OkHttpClient.Builder()
        .connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
      openSocket(generation,false);
    }catch(Exception e){state("Error de conexión: "+e.getMessage());}
  }

  private void openSocket(final int generation,final boolean old){
    if(generation!=connectGeneration)return;
    state(old?"Probando protocolo LG antiguo…":"Conectando con LG webOS…");
    OkHttpClient client=old?legacy:secure;
    String address=(old?"ws://":"wss://")+ip+(old?":3000":":3001");
    control=client.newWebSocket(new Request.Builder().url(address).build(),new WebSocketListener(){
      @Override public void onOpen(WebSocket webSocket,Response response){
        if(generation!=connectGeneration){webSocket.cancel();return;}
        state("Acepta la conexión en la pantalla de la LG.");
        try{
          JSONObject p=new JSONObject();
          p.put("pairingType","PROMPT");p.put("forcePairing",false);
          p.put("manifest",new JSONObject(readManifest()));
          String clientKey=pref.getString("key:"+ip,"");
          if(!clientKey.isEmpty())p.put("client-key",clientKey);
          JSONObject m=new JSONObject();m.put("type","register");m.put("id","register");
          m.put("payload",p);webSocket.send(m.toString());
        }catch(Exception e){state("No se pudo solicitar autorización.");}
      }
      @Override public void onMessage(WebSocket webSocket,String body){
        if(generation==connectGeneration)handle(body);
      }
      @Override public void onFailure(WebSocket webSocket,Throwable error,Response response){
        if(generation!=connectGeneration)return;
        connected=false;pointerReady=false;
        if(!old&&pref.getString("pin:"+ip,"").isEmpty()){openSocket(generation,true);}
        else state("Sin conexión. Revisa la IP y los permisos de la LG.");
      }
      @Override public void onClosed(WebSocket w,int code,String reason){
        if(generation==connectGeneration){connected=false;pointerReady=false;state("Televisor desconectado.");}
      }
    });
  }

  private String readManifest() throws Exception {
    java.io.InputStream in=getAssets().open("manifest_signed.json");
    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
    byte[] buf=new byte[4096];int n;
    while((n=in.read(buf))!=-1)out.write(buf,0,n);
    in.close();return out.toString("UTF-8");
  }
  private void handle(String raw) {
    try{
      JSONObject m=new JSONObject(raw),p=m.optJSONObject("payload");
      String type=m.optString("type"),rid=m.optString("id");
      if("registered".equals(type)){
        connected=true;
        if(p!=null){
          String key=p.optString("client-key","");
          if(!key.isEmpty())pref.edit().putString("key:"+ip,key).apply();
        }
        if(!fingerprint.isEmpty())pref.edit().putString("pin:"+ip,fingerprint).apply();
        state("LG conectada. Ya puedes utilizar el mando.");
        req("ssap://com.webos.service.networkinput/getPointerInputSocket",null);
        req("ssap://com.webos.applicationManager/listLaunchPoints",null);
      }else if(p!=null&&p.has("socketPath")){
        String socket=p.optString("socketPath","");
        OkHttpClient client=socket.startsWith("wss://")?secure:legacy;
        pointer=client.newWebSocket(new Request.Builder().url(socket).build(),new WebSocketListener(){
          @Override public void onOpen(WebSocket webSocket,Response r){
            pointerReady=true;state("LG conectada · Mando listo.");
          }
          @Override public void onFailure(WebSocket webSocket,Throwable e,Response r){
            pointerReady=false;state("Conectada, pero la navegación está limitada.");
          }
          @Override public void onClosed(WebSocket w,int code,String why){pointerReady=false;}
        });
      }else if(p!=null&&(p.has("launchPoints")||p.has("apps"))){
        JSONArray apps=p.optJSONArray("launchPoints");
        if(apps==null)apps=p.optJSONArray("apps");
        if(apps!=null){
          for(int i=0;i<apps.length();i++){
            JSONObject a=apps.optJSONObject(i);
            if(a==null)continue;
            String title=a.optString("title","").toLowerCase(java.util.Locale.ROOT);
            String name=a.optString("id","");
            if(!name.isEmpty())appIds.put(title,name);
          }
          gotApps=true;
          if(queuedApp!=null){String queued=queuedApp;queuedApp=null;launch(queued);}
        }
      }else if("error".equals(type)){
        if(rid.startsWith("apps_")){gotApps=true;}
        else state("La TV ha rechazado una orden: "+m.optString("error","sin detalle"));
      }
    }catch(Exception e){state("Respuesta de la LG no reconocida.");}
  }

  private synchronized void req(String uri,JSONObject payload){
    if(!connected||control==null){state("Primero conecta la LG.");return;}
    try{
      JSONObject m=new JSONObject();
      m.put("type","request");
      m.put("id",(uri.contains("listLaunchPoints")?"apps_":"req_")+(++requestId));
      m.put("uri",uri);
      if(payload!=null)m.put("payload",payload);
      control.send(m.toString());
    }catch(Exception e){state("No se pudo enviar la orden.");}
  }
  private void key(String key){
    if(!pointerReady||pointer==null){state("Conecta el mando y autoriza la navegación.");return;}
    pointer.send("type:button\nname:"+key+"\n\n");
  }
  private void launch(String app){
    if(!connected){state("Primero conecta la LG.");return;}
    if(!gotApps){queuedApp=app;req("ssap://com.webos.applicationManager/listLaunchPoints",null);state("Buscando aplicaciones de tu LG…");return;}
    String needle;
    if("prime".equals(app))needle="prime";
    else if("lgchannels".equals(app))needle="lg channels";
    else needle=app;
    String target=null;
    for(Map.Entry<String,String> item:appIds.entrySet()){
      String title=item.getKey();
      if(title.contains(needle)||(app.equals("prime")&&title.contains("amazon"))
          ||(app.equals("lgchannels")&&title.contains("lg channel"))){
        target=item.getValue();break;
      }
    }
    if(target==null){
      if(app.equals("netflix"))target="netflix";
      else if(app.equals("prime"))target="amazon";
      else if(app.equals("disney"))target="com.disney.disneyplus-prod";
    }
    if(target==null){toast("No se encontró esa aplicación en la televisión.");return;}
    try{JSONObject p=new JSONObject();p.put("id",target);req("ssap://system.launcher/launch",p);}
    catch(Exception e){state("No se pudo abrir la aplicación.");}
  }


  private void discoverTV(){
    if(searching){toast("La búsqueda ya está en curso.");return;}
    searching=true;
    state("Buscando televisores LG en tu Wi-Fi…");
    new Thread(()->{
      LinkedHashSet<String> candidates=new LinkedHashSet<>();
      try{
        DatagramSocket socket=new DatagramSocket();
        try{
          socket.setSoTimeout(250);
          String[] targets={
            "urn:lge-com:service:webos-second-screen:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
          };
          for(String target:targets){
            String request="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+target+"\r\n\r\n";
            byte[] data=request.getBytes(StandardCharsets.UTF_8);
            DatagramPacket out=new DatagramPacket(data,data.length,InetAddress.getByName("239.255.255.250"),1900);
            socket.send(out);
            socket.send(out);
          }
          long deadline=System.currentTimeMillis()+3300;
          while(System.currentTimeMillis()<deadline){
            try{
              byte[] buf=new byte[8192];
              DatagramPacket response=new DatagramPacket(buf,buf.length);
              socket.receive(response);
              String headers=new String(response.getData(),0,response.getLength(),StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
              String address=response.getAddress().getHostAddress();
              if(localAddress(address) &&
                (headers.contains("lge")||headers.contains("webos")||headers.contains("lg smart tv")))
                candidates.add(address);
            }catch(SocketTimeoutException timeout){}
          }
        }finally{socket.close();}
      }catch(Exception e){}
      if(candidates.isEmpty()){
        runOnUiThread(()->state("No respondió por búsqueda directa; comprobando los televisores de la red…"));
        String prefix=wifiIPv4Prefix();
        if(prefix!=null){
          Set<String> scanned=Collections.synchronizedSet(new HashSet<>());
          ExecutorService pool=Executors.newFixedThreadPool(24);
          for(int n=1;n<=254;n++){
            final String candidate=prefix+n;
            pool.execute(()->{
              for(int port:new int[]{3001,3000}){
                try(Socket test=new Socket()){
                  test.connect(new InetSocketAddress(candidate,port),450);
                  scanned.add(candidate);return;
                }catch(Exception e){}
              }
            });
          }
          pool.shutdown();
          try{pool.awaitTermination(12,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
          candidates.addAll(scanned);
        }
      }
      ArrayList<String> found=new ArrayList<>(candidates);
      Collections.sort(found);
      searching=false;
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        if(found.isEmpty()){
          state("No encontré tu LG. Comprueba que esté encendida y en el mismo Wi-Fi.");
          new AlertDialog.Builder(this)
            .setTitle("No se encontró ninguna LG")
            .setMessage("Enciende el televisor y conecta el móvil a la misma red local. Evita redes de invitados o VPN. También puedes introducir la IP manualmente.")
            .setPositiveButton("Entendido",null).show();
        }else if(found.size()==1){
          ipField.setText(found.get(0));
          state("Televisor encontrado: "+found.get(0)+". Solicitando autorización…");
          connect();
        }else{
          String[] choices=found.toArray(new String[0]);
          state("Encontré "+found.size()+" dispositivos compatibles. Selecciona tu LG.");
          new AlertDialog.Builder(this)
            .setTitle("Elige tu televisor LG")
            .setItems(choices,(dialog,which)->{
              ipField.setText(choices[which]);
              connect();
            })
            .setNegativeButton("Cancelar",null).show();
        }
      });
    },"lg-wifi-discovery").start();
  }

  private String wifiIPv4Prefix(){
    try{
      ConnectivityManager manager=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
      if(manager==null)return null;
      for(Network network:manager.getAllNetworks()){
        NetworkCapabilities caps=manager.getNetworkCapabilities(network);
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
        LinkProperties props=manager.getLinkProperties(network);
        if(props==null)continue;
        for(LinkAddress a:props.getLinkAddresses()){
          InetAddress address=a.getAddress();
          if(address instanceof Inet4Address&&localAddress(address.getHostAddress())){
            String[] pieces=address.getHostAddress().split("\\.");
            return pieces[0]+"."+pieces[1]+"."+pieces[2]+".";
          }
        }
      }
    }catch(Exception e){}
    return null;
  }

  private void forgetPairing(){
    int generation=++connectGeneration;
    if(control!=null)control.cancel();
    if(pointer!=null)pointer.cancel();
    connected=false;pointerReady=false;gotApps=false;appIds.clear();
    String host=ipField.getText().toString().trim();
    pref.edit().remove("key:"+host).remove("pin:"+host).apply();
    state("Vínculo olvidado. Pulsa Conectar para autorizar de nuevo.");
  }
  private void action(String command){
    switch(command){
      case "MIC":case "ALEXA":
        toast("La voz no está disponible en este prototipo.");return;
      case "POWER":
        if(!connected){toast("La TV debe estar encendida para establecer conexión.");return;}
        req("ssap://system/turnOff",null);return;
      case "VOLUP":req("ssap://audio/volumeUp",null);return;
      case "VOLDOWN":req("ssap://audio/volumeDown",null);return;
      case "CHUP":req("ssap://tv/channelUp",null);return;
      case "CHDOWN":req("ssap://tv/channelDown",null);return;
      case "MUTE":
        try{JSONObject p=new JSONObject();muted=!muted;p.put("mute",muted);req("ssap://audio/setMute",p);}catch(Exception ignored){}
        return;
      case "NETFLIX":launch("netflix");return;
      case "PRIME":launch("prime");return;
      case "DISNEY":launch("disney");return;
      case "RAKUTEN":launch("rakuten");return;
      case "LGCHANNELS":launch("lgchannels");return;
      case "GUIDE":key("GUIDE");return;
      case "MORE":key("MENU");return;
      case "SETTINGS":key("QMENU");return;
      case "INPUT":key("INPUT");return;
      default:key(command);
    }
  }
  @Override protected void onDestroy(){
    ++connectGeneration;
    if(control!=null)control.close(1000,"bye");
    if(pointer!=null)pointer.close(1000,"bye");
    if(secure!=null)secure.dispatcher().executorService().shutdown();
    if(legacy!=null)legacy.dispatcher().executorService().shutdown();
    super.onDestroy();
  }

  private final class RemoteView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Hit> hits=new ArrayList<>();
    private final float W=320f,H=1194f;
    private String pressed="";
    private float startX,startY;
    private class Hit{
      final RectF box;final String key;
      Hit(String key,float left,float top,float right,float bottom){
        this.key=key;this.box=new RectF(left,top,right,bottom);
      }
    }
    RemoteView(Context c){
      super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
      setContentDescription("Mando LG con teclas y rueda central");
      installHits();
    }
    @Override protected void onMeasure(int w,int h){
      int width=MeasureSpec.getSize(w);
      setMeasuredDimension(width,(int)(width*(H/W)));
    }
    private void hit(String k,float x,float y,float r){hits.add(new Hit(k,x-r,y-r,x+r,y+r));}
    private void box(String k,float l,float t,float r,float b){hits.add(new Hit(k,l,t,r,b));}
    private void installHits(){
      hit("POWER",90,73,27);hit("MIC",234,73,26);
      for(int n=1;n<=9;n++){int col=(n-1)%3,row=(n-1)/3;hit(""+n,86+74*col,147+63*row,27);}
      hit("GUIDE",86,336,27);hit("0",160,336,27);hit("MORE",234,336,27);
      box("VOLUP",66,382,117,458);box("VOLDOWN",66,458,117,538);
      box("CHUP",203,382,254,458);box("CHDOWN",203,458,254,538);
      hit("MUTE",160,448,30);hit("INFO",160,565,26);
      hit("HOME",86,601,26);hit("INPUT",234,601,26);
      hit("BACK",88,899,26);hit("SETTINGS",232,899,26);
      box("RED",66,947,112,974);box("GREEN",113,947,158,974);box("YELLOW",162,947,207,974);box("BLUE",208,947,254,974);
      box("NETFLIX",62,992,160,1032);box("PRIME",160,992,258,1032);
      box("DISNEY",62,1034,160,1074);box("RAKUTEN",160,1034,258,1074);
      box("LGCHANNELS",62,1076,160,1116);box("ALEXA",160,1076,258,1116);
    }
    private void basePaint(int color){paint.reset();paint.setAntiAlias(true);paint.setColor(color);}
    private int rgb(String c){return Color.parseColor(c);}
    private void gradient(Canvas c,RectF r,float radius,int top,int bottom){
      basePaint(Color.WHITE);
      paint.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,top,bottom,Shader.TileMode.CLAMP));
      c.drawRoundRect(r,radius,radius,paint);paint.setShader(null);
    }
    private void outline(Canvas c,RectF r,float radius,int color,float stroke){
      basePaint(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);
      c.drawRoundRect(r,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void centered(Canvas c,String t,float x,float y,float size,int color,boolean bold){
      basePaint(color);
      paint.setTextSize(size);paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
      Paint.FontMetrics fm=paint.getFontMetrics();
      c.drawText(t,x,y-(fm.ascent+fm.descent)/2,paint);
    }
    private void disc(Canvas c,float x,float y,float rad,String label,float size,int color,String command){
      RectF r=new RectF(x-rad,y-rad,x+rad,y+rad);
      basePaint(rgb("#08090b"));paint.setShadowLayer(5,0,3,rgb("#aa000000"));c.drawOval(r,paint);paint.clearShadowLayer();
      gradient(c,new RectF(x-rad+2,y-rad+2,x+rad-2,y+rad-2),rad,rgb("#4d4e51"),rgb("#26282a"));
      outline(c,new RectF(x-rad+2,y-rad+2,x+rad-2,y+rad-2),rad,rgb("#606164"),0.85f);
      if(command.equals(pressed)){
        basePaint(0x4483b69f);c.drawCircle(x,y,rad-2,paint);
      }
      centered(c,label,x,y,size,color,false);
    }
    private void oval(Canvas c,float l,float t,float r,float b,int colorTop,int colorBottom){
      RectF rc=new RectF(l,t,r,b);
      gradient(c,rc,(r-l)/2,colorTop,colorBottom);
      outline(c,rc,(r-l)/2,rgb("#55565a"),0.7f);
    }
    private void logo(Canvas c,String word,float l,float t,float r,float b,int color,int size,boolean bold){
      RectF bounds=new RectF(l,t,r,b);
      gradient(c,bounds,4,rgb("#343436"),rgb("#17181a"));
      outline(c,bounds,4,rgb("#55565a"),.6f);
      centered(c,word,(l+r)/2,(t+b)/2,size,color,bold);
    }
    private void rocker(Canvas c,float x,String top,String bottom,String upper,String lower){
      oval(c,x-23,380,x+23,537,rgb("#404247"),rgb("#242629"));
      basePaint(rgb("#141517"));paint.setStrokeWidth(2);
      c.drawLine(x-23,459,x+23,459,paint);
      centered(c,top,x,415,30,rgb("#bfc2c5"),false);
      centered(c,bottom,x,503,30,rgb("#bfc2c5"),false);
      if(pressed.equals(upper)||pressed.equals(lower)){
        basePaint(0x338bd8b1);
        RectF o=new RectF(x-21,pressed.equals(upper)?382:460,x+21,pressed.equals(upper)?458:534);
        c.drawRoundRect(o,16,16,paint);
      }
    }
    @Override protected void onDraw(Canvas c){
      super.onDraw(c);
      c.save();c.scale(getWidth()/W,getWidth()/W);
      RectF shadow=new RectF(29,17,291,1175);
      basePaint(0x55000000);paint.setShadowLayer(14,0,8,0x44000000);c.drawRoundRect(shadow,72,72,paint);paint.clearShadowLayer();
      RectF body=new RectF(26,9,294,1174);
      gradient(c,body,69,rgb("#36383b"),rgb("#101114"));
      outline(c,body,69,rgb("#777b7e"),2f);
      basePaint(0x1bffffff);c.drawRoundRect(new RectF(33,24,43,1165),9,9,paint);
      disc(c,90,73,25,"⏻",32,rgb("#f4556d"),"POWER");
      // The physical LG remote has a microphone key; it is visual-only here.
      disc(c,234,73,21,"♩",24,rgb("#c2c4c7"),"MIC");
      for(int n=1;n<=9;n++){
        int col=(n-1)%3,row=(n-1)/3;
        disc(c,86+col*74,147+row*63,26,""+n,25,rgb("#e0e2e4"),""+n);
      }
      disc(c,86,336,26,"≡",28,rgb("#e2e3e5"),"GUIDE");
      centered(c,"GUIDE",86,368,9,rgb("#8e9196"),true);
      disc(c,160,336,26,"0",25,rgb("#e0e2e4"),"0");
      disc(c,234,336,26,"•••",20,rgb("#e0e2e4"),"MORE");
      rocker(c,91,"+","−","VOLUP","VOLDOWN");
      rocker(c,229,"⌃","⌄","CHUP","CHDOWN");
      centered(c,"VOL",91,373,9,rgb("#aeb0b4"),true);
      centered(c,"CH",229,373,9,rgb("#aeb0b4"),true);
      disc(c,160,448,27,"×",31,rgb("#d7dadd"),"MUTE");
      centered(c,"MUTE",160,487,9,rgb("#9b9ea0"),true);
      disc(c,160,565,24,"i",23,rgb("#d4d6d8"),"INFO");
      disc(c,86,601,25,"⌂",29,rgb("#e0e2e4"),"HOME");
      disc(c,234,601,25,"▣",25,rgb("#e0e2e4"),"INPUT");
      // Magic wheel: the four surrounding sectors are tappable arrow keys.
      RectF ring=new RectF(53,660,267,875);
      basePaint(rgb("#0b0d0f"));paint.setShadowLayer(7,0,4,0x99000000);c.drawOval(ring,paint);paint.clearShadowLayer();
      basePaint(rgb("#424448"));
      paint.setShader(new RadialGradient(148,750,112,new int[]{rgb("#3b3f41"),rgb("#191b1f"),rgb("#090a0d")},null,Shader.TileMode.CLAMP));
      c.drawCircle(160,768,101,paint);paint.setShader(null);
      basePaint(rgb("#575b5d"));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.1f);c.drawCircle(160,768,103,paint);paint.setStyle(Paint.Style.FILL);
      centered(c,"⌃",160,693,22,rgb("#7e8487"),true);
      centered(c,"⌄",160,848,22,rgb("#7e8487"),true);
      centered(c,"‹",79,768,24,rgb("#737b7c"),true);
      centered(c,"›",241,768,24,rgb("#737b7c"),true);
      RectF wheel=new RectF(132,740,188,796);
      gradient(c,wheel,28,rgb("#585a5d"),rgb("#25272a"));
      outline(c,wheel,28,rgb("#686c70"),1.2f);
      for(int i=0;i<8;i++){basePaint(rgb("#202225"));paint.setStrokeWidth(1f);c.drawLine(143,752+i*4,177,752+i*4,paint);}
      if(pressed.equals("ENTER")){basePaint(0x5568c7a7);c.drawCircle(160,768,29,paint);}
      centered(c,"OK",160,805,10,rgb("#b7bec0"),true);
      disc(c,88,899,24,"↶",29,rgb("#cbd0d2"),"BACK");
      disc(c,232,899,24,"⚙",28,rgb("#cbd0d2"),"SETTINGS");
      int[] colors={rgb("#d84b66"),rgb("#42ba90"),rgb("#f5c75b"),rgb("#4b9bce")};
      String[] colorNames={"RED","GREEN","YELLOW","BLUE"};
      for(int i=0;i<4;i++){
        float l=66+i*47;RectF pill=new RectF(l,947,l+44,974);
        gradient(c,pill,14,colors[i],colors[i]);
        if(pressed.equals(colorNames[i]))outline(c,pill,14,Color.WHITE,3);
        centered(c,"•••",l+22,960,11,0xe7ffffff,false);
      }
      logo(c,"NETFLIX",62,992,160,1032,rgb("#ef374b"),14,true);
      logo(c,"prime video",160,992,258,1032,rgb("#e8eff2"),13,true);
      logo(c,"Disney+",62,1034,160,1074,rgb("#ecf0f6"),15,true);
      logo(c,"Rakuten TV",160,1034,258,1074,rgb("#edf0f2"),13,true);
      logo(c,"LG Channels",62,1076,160,1116,rgb("#d6d8da"),12,true);
      logo(c,"alexa",160,1076,258,1116,rgb("#e2e7e8"),16,false);
      centered(c,"LG",160,1149,17,rgb("#767b7f"),true);
      c.restore();
    }
    private String find(float x,float y){
      float dx=x-160,dy=y-768;
      float d=(float)Math.sqrt(dx*dx+dy*dy);
      if(d<104){
        if(d<36)return "ENTER";
        if(Math.abs(dy)>=Math.abs(dx))return dy<0?"UP":"DOWN";
        return dx<0?"LEFT":"RIGHT";
      }
      for(Hit hit:hits)if(hit.box.contains(x,y))return hit.key;
      return "";
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
      float x=e.getX()*W/getWidth(),y=e.getY()*W/getWidth();
      switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:
          startX=x;startY=y;pressed=find(x,y);
          if(pressed.isEmpty())return false;
          getParent().requestDisallowInterceptTouchEvent(true);
          invalidate();return true;
        case MotionEvent.ACTION_UP:
          String selected=find(x,y),active=pressed;pressed="";invalidate();
          if(!active.isEmpty()&&active.equals(selected)){
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            action(active);
          }
          return true;
        case MotionEvent.ACTION_CANCEL:pressed="";invalidate();return true;
      }
      return true;
    }
  }
}
