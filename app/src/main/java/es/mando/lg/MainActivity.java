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
import android.net.wifi.WifiManager;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
  private static final int CREAM=Color.rgb(246,247,244), DARK=Color.rgb(35,41,42), MINT=Color.rgb(215,233,222);
  private final HashMap<String,String> appIds=new HashMap<>();
  private SharedPreferences pref;
  private TextView status;
  private TextView macSummary;
  private TextView diagnostics;
  private EditText ipField;
  private EditText macField;
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
    ipField.setText(pref.getString("ip","192.168.0.11"));
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
    LinearLayout wakeBar=new LinearLayout(this);
    wakeBar.setOrientation(LinearLayout.HORIZONTAL);
    macField=new EditText(this);
    macField.setSingleLine(true);
    macField.setHint("MAC de la LG (detectada al conectar)");
    macField.setTextSize(12);
    macField.setText(pref.getString("mac:"+ipField.getText().toString().trim(),"14:7F:67:CE:D9:44"));
    macField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    macField.setPadding(dp(10),dp(6),dp(8),dp(6));
    macField.setBackground(background(Color.WHITE,12));
    LinearLayout.LayoutParams macParams=new LinearLayout.LayoutParams(0,dp(46),1);
    macParams.setMargins(0,dp(7),dp(8),dp(2));
    wakeBar.addView(macField,macParams);
    Button wake=new Button(this);
    wake.setText("⏻ Encender");wake.setAllCaps(false);wake.setTextSize(12);
    wake.setTextColor(Color.WHITE);
    wake.setBackground(background(Color.rgb(172,60,78),12));
    LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(106),dp(46));
    wp.setMargins(0,dp(7),0,0);
    wakeBar.addView(wake,wp);
    wake.setOnClickListener(v->sendWake());
    root.addView(wakeBar);
    macSummary=new TextView(this);
    macSummary.setText("MAC de la v0.6 (tipo sin confirmar). Conecta para verificarla.");
    macSummary.setTextColor(Color.rgb(91,101,100));macSummary.setTextSize(11);
    macSummary.setPadding(dp(6),dp(4),dp(6),dp(4));
    root.addView(macSummary);
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
    diagnostics=new TextView(this);
    diagnostics.setText("Diagnóstico: sin intentos de encendido.");
    diagnostics.setTextColor(Color.rgb(36,76,68));diagnostics.setTextSize(11);
    diagnostics.setPadding(dp(12),dp(9),dp(12),dp(10));
    diagnostics.setBackground(background(Color.rgb(231,239,235),12));
    LinearLayout.LayoutParams diagParams=new LinearLayout.LayoutParams(-1,-2);
    diagParams.setMargins(0,dp(6),0,dp(6));
    root.addView(diagnostics,diagParams);

    // Control panel is built with standard Android buttons; always visible.
    LinearLayout remote=createRemote();
    LinearLayout.LayoutParams remoteParams=new LinearLayout.LayoutParams(-1,-2);
    remoteParams.setMargins(0,dp(7),0,dp(10));
    root.addView(remote,remoteParams);

    TextView foot=new TextView(this);
    foot.setText("LG 50UT80006LA · Activa en la TV Ajustes > General > Dispositivos externos > Encendido con móvil > Encender mediante Wi-Fi. La MAC se intenta guardar al conectar. Voz/Alexa no disponibles.");
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
  private void diagnostic(String s){runOnUiThread(()->{if(diagnostics!=null)diagnostics.setText("Diagnóstico: "+s);});}
  private void macInfo(String s){runOnUiThread(()->{if(macSummary!=null)macSummary.setText(s);});}
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
    if(macField!=null)macField.setText(pref.getString("mac:"+ip,"14:7F:67:CE:D9:44"));
    WifiRoute wifi=wifiRoute();
    if(wifi==null){state("El teléfono no tiene una red Wi-Fi IPv4 activa.");
      diagnostic("Conecta el móvil al Wi-Fi doméstico.");return;}
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
        .socketFactory(wifi.network.getSocketFactory())
        .sslSocketFactory(ssl.getSocketFactory(),trust)
        .hostnameVerifier((host,session)->host.equals(ip))
        .connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
      legacy=new OkHttpClient.Builder()
        .socketFactory(wifi.network.getSocketFactory())
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
        else {
          state("TV sin conexión. Si está apagada, pulsa Encender.");
          diagnostic("WebSocket: "+error.getClass().getSimpleName()+". Puede deberse al apagado.");
        }
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
        req("ssap://com.webos.service.connectionmanager/getinfo",null);
        macInfo("LG conectada. Leyendo direcciones MAC Wi-Fi y Ethernet…");
      }else if(p!=null&&(p.has("wifiInfo")||p.has("wiredInfo")||p.has("wifi")||
                              p.has("wired")||p.has("wifiMacAddress")||p.has("macAddress"))){
        String wifiMac=firstValidMAC(
          readMAC(p.optJSONObject("wifiInfo")),readMAC(p.optJSONObject("wifi")),
          p.optString("wifiMacAddress",""));
        String wiredMac=firstValidMAC(
          readMAC(p.optJSONObject("wiredInfo")),readMAC(p.optJSONObject("wired")),
          p.optString("wiredMacAddress",""),p.optString("macAddress",""));
        if(!wifiMac.isEmpty()||!wiredMac.isEmpty()){
          SharedPreferences.Editor ed=pref.edit();
          if(!wifiMac.isEmpty())ed.putString("wifi_mac:"+ip,wifiMac);
          if(!wiredMac.isEmpty())ed.putString("wired_mac:"+ip,wiredMac);
          String candidate=!wifiMac.isEmpty()?wifiMac:wiredMac;
          ed.putString("mac:"+ip,candidate).apply();
          final String label="MAC Wi-Fi: "+(wifiMac.isEmpty()?"no disponible":wifiMac)+
            " · Ethernet: "+(wiredMac.isEmpty()?"no disponible":wiredMac);
          runOnUiThread(()->{
            if(macField!=null)macField.setText(candidate);
            macInfo(label);
          });
        }else if(rid.startsWith("mac_info_")){
          req("ssap://com.webos.service.connectionmanager/getStatus",null);
        }
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
      }else if("response".equals(type)&&rid.startsWith("mac_info_")){
        req("ssap://com.webos.service.connectionmanager/getStatus",null);
      }else if("error".equals(type)){
        if(rid.startsWith("apps_")){gotApps=true;}
        else if(rid.startsWith("mac_info_")){
          req("ssap://com.webos.service.connectionmanager/getStatus",null);
        }else if(rid.startsWith("mac_status_")){
          macInfo("MAC automática no disponible. Compruébala en el menú de la TV.");
        }
        else state("La TV ha rechazado una orden: "+m.optString("error","sin detalle"));
      }
    }catch(Exception e){state("Respuesta de la LG no reconocida.");}
  }

  private synchronized void req(String uri,JSONObject payload){
    if(!connected||control==null){state("Primero conecta la LG.");return;}
    try{
      JSONObject m=new JSONObject();
      m.put("type","request");
      m.put("id",(uri.contains("listLaunchPoints")?"apps_":
          uri.contains("connectionmanager/getinfo")?"mac_info_":
          uri.contains("connectionmanager/getStatus")?"mac_status_":"req_")+(++requestId));
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
    state("Buscando tu LG en la red Wi-Fi local…");
    new Thread(()->{
      final WifiRoute wifi=wifiRoute();
      LinkedHashSet<String> found=new LinkedHashSet<>();
      if(wifi==null){
        searching=false;
        state("Wi-Fi no disponible. Conecta el móvil a la red de la televisión.");
        diagnostic("No se detectó una interfaz Wi-Fi IPv4 en Android.");
        return;
      }
      String saved=ipField.getText().toString().trim();
      if(localAddress(saved)&&isControlReachable(wifi.network,saved))found.add(saved);
      WifiManager.MulticastLock lock=null;
      if(found.isEmpty()){
        try{
          WifiManager wm=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
          if(wm!=null){
            lock=wm.createMulticastLock("lg-magic-remote-discovery");
            lock.setReferenceCounted(false);lock.acquire();
          }
        }catch(Exception e){diagnostic("Multicast: "+e.getClass().getSimpleName());}
        try{
          DatagramSocket socket=new DatagramSocket();
          try{
            wifi.network.bindSocket(socket);
            socket.setSoTimeout(300);
            String[] targets={
              "urn:lge-com:service:webos-second-screen:1",
              "urn:schemas-upnp-org:device:MediaRenderer:1",
              "ssdp:all"
            };
            for(String target:targets){
              String request="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"+
                "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+target+"\r\n\r\n";
              byte[] data=request.getBytes(StandardCharsets.UTF_8);
              DatagramPacket message=new DatagramPacket(data,data.length,
                InetAddress.getByName("239.255.255.250"),1900);
              socket.send(message);
            }
            long deadline=System.currentTimeMillis()+3200;
            while(System.currentTimeMillis()<deadline){
              try{
                byte[] buf=new byte[8192];
                DatagramPacket reply=new DatagramPacket(buf,buf.length);
                socket.receive(reply);
                String text=new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8)
                  .toLowerCase(Locale.ROOT);
                String host=reply.getAddress().getHostAddress();
                if(localAddress(host)&&(text.contains("webos")||text.contains("lge")||
                  text.contains("lg smart tv")))found.add(host);
              }catch(SocketTimeoutException ignored){}
            }
          }finally{socket.close();}
        }catch(Exception ex){diagnostic("SSDP no disponible: "+ex.getClass().getSimpleName());}
        finally{if(lock!=null&&lock.isHeld())lock.release();}
      }
      if(found.isEmpty()){
        state("SSDP sin respuesta. Comprobando el segmento Wi-Fi…");
        // Scan a maximum /23 network, no arbitrary /24 assumption or huge /16 sweep.
        if(wifi.prefix>=23&&wifi.prefix<=30){
          byte[] octets=wifi.address.getAddress();
          long ipValue=0;
          for(byte b:octets)ipValue=(ipValue<<8)|(b&255L);
          long mask=(0xffffffffL<<(32-wifi.prefix))&0xffffffffL;
          long first=ipValue&mask,last=first|(~mask&0xffffffffL);
          Set<String> discovered=Collections.synchronizedSet(new LinkedHashSet<>());
          ExecutorService pool=Executors.newFixedThreadPool(24);
          for(long number=first+1;number<last;number++){
            String candidate=((number>>>24)&255)+"."+((number>>>16)&255)+"."+
              ((number>>>8)&255)+"."+(number&255);
            if(!localAddress(candidate)||candidate.equals(wifi.address.getHostAddress()))continue;
            pool.execute(()->{if(isControlReachable(wifi.network,candidate))discovered.add(candidate);});
          }
          pool.shutdown();
          try{pool.awaitTermination(16,TimeUnit.SECONDS);}
          catch(InterruptedException ex){Thread.currentThread().interrupt();}
          found.addAll(discovered);
        }
      }
      ArrayList<String> results=new ArrayList<>(found);
      Collections.sort(results);
      searching=false;
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        if(results.isEmpty()){
          state("No veo tu LG encendida en esta red.");
          diagnostic("Wi-Fi "+wifi.address.getHostAddress()+"/"+wifi.prefix+
            " · difusión "+wifi.broadcast+
            ". Si la LG está apagada, usa Encender, no Buscar.");
          new AlertDialog.Builder(this)
            .setTitle("No se encontró la LG")
            .setMessage("La búsqueda solo funciona con la TV encendida. Si está apagada, usa Encender. Comprueba que el móvil sigue en el mismo Wi-Fi.")
            .setPositiveButton("Entendido",null).show();
        }else if(results.size()==1){
          ipField.setText(results.get(0));
          connect();
        }else{
          String[] choices=results.toArray(new String[0]);
          state("Selecciona la IP de tu LG. Detectados: "+choices.length);
          new AlertDialog.Builder(this)
            .setTitle("Dispositivos encontrados")
            .setItems(choices,(dialog,index)->{
              ipField.setText(choices[index]);connect();
            })
            .setNegativeButton("Cancelar",null).show();
        }
      });
    },"lg-ssdp-discovery").start();
  }

  private static final class WifiRoute {
    final Network network;
    final Inet4Address address;
    final int prefix;
    final String broadcast;
    WifiRoute(Network net,Inet4Address ipv4,int length){
      network=net;address=ipv4;prefix=length;
      broadcast=LanTools.directedBroadcast(ipv4,length);
    }
  }

  private WifiRoute wifiRoute(){
    try{
      ConnectivityManager manager=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
      if(manager==null)return null;
      for(Network network:manager.getAllNetworks()){
        NetworkCapabilities cap=manager.getNetworkCapabilities(network);
        if(cap==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
        LinkProperties props=manager.getLinkProperties(network);
        if(props==null)continue;
        for(LinkAddress link:props.getLinkAddresses()){
          if(link.getAddress() instanceof Inet4Address){
            Inet4Address addr=(Inet4Address)link.getAddress();
            if(localAddress(addr.getHostAddress()))
              return new WifiRoute(network,addr,link.getPrefixLength());
          }
        }
      }
    }catch(Exception ex){diagnostic("Fallo consultando la red Wi-Fi: "+ex.getClass().getSimpleName());}
    return null;
  }

  private String normalizeMAC(String raw){return LanTools.normalizeMAC(raw);}
  private String firstValidMAC(String... options){
    for(String option:options){
      String mac=normalizeMAC(option);
      if(!mac.isEmpty())return mac;
    }
    return "";
  }
  private String readMAC(JSONObject value){
    if(value==null)return "";
    return firstValidMAC(value.optString("macAddress",""),value.optString("wifiMacAddress",""));
  }
  private boolean isControlReachable(Network network,String host){
    for(int port:new int[]{3001,3000}){
      try(Socket test=network.getSocketFactory().createSocket()){
        test.connect(new InetSocketAddress(host,port),500);
        return true;
      }catch(Exception ignored){}
    }
    return false;
  }

  private void sendWake(){
    final String host=ipField.getText().toString().trim();
    if(!localAddress(host)){state("Primero indica la IP de la LG.");return;}
    final WifiRoute wifi=wifiRoute();
    if(wifi==null){
      state("No hay Wi-Fi activo para encender la televisión.");
      diagnostic("El móvil debe estar en el Wi-Fi doméstico; no basta con datos móviles.");
      return;
    }
    String raw=macField==null?"":macField.getText().toString();
    final String typed=normalizeMAC(raw);
    if(!raw.trim().isEmpty()&&typed.isEmpty()){
      state("La dirección MAC introducida no es válida.");
      diagnostic("Usa seis parejas hexadecimales, p. ej. 14:7F:67:CE:D9:44.");
      return;
    }
    final LinkedHashSet<String> macs=new LinkedHashSet<>();
    if(!typed.isEmpty())macs.add(typed);
    String wifiMac=normalizeMAC(pref.getString("wifi_mac:"+host,""));
    String wiredMac=normalizeMAC(pref.getString("wired_mac:"+host,""));
    if(!wifiMac.isEmpty())macs.add(wifiMac);
    if(!wiredMac.isEmpty())macs.add(wiredMac);
    String saved=normalizeMAC(pref.getString("mac:"+host,""));
    if(!saved.isEmpty())macs.add(saved);
    if(macs.isEmpty()){
      state("Falta una MAC válida de la LG.");
      diagnostic("Conecta con la LG encendida para obtener la MAC, o introdúcela.");
      return;
    }
    if(!typed.isEmpty())pref.edit().putString("mac:"+host,typed).apply();

    boolean inSubnet=false;
    try{
      inSubnet=LanTools.sameSubnet((Inet4Address)InetAddress.getByName(host),wifi.address,wifi.prefix);
    }catch(Exception ignored){}
    final boolean localNet=inSubnet;
    state("Enviando encendido por la interfaz Wi-Fi del móvil…");
    diagnostic("Móvil "+wifi.address.getHostAddress()+"/"+wifi.prefix+" · difusión "+
      wifi.broadcast+" · MAC: "+macs.size()+
      (localNet?"":" · TV fuera de la misma subred"));

    new Thread(()->{
      int count=0,failed=0;
      String lastError="";
      DatagramSocket socket=null;
      try{
        socket=new DatagramSocket();
        wifi.network.bindSocket(socket);  // Android Network API: select Wi-Fi explicitly
        socket.setBroadcast(true);
        LinkedHashSet<String> destinations=new LinkedHashSet<>();
        if(wifi.prefix<=30)destinations.add(wifi.broadcast);
        destinations.add("255.255.255.255");
        for(String mac:macs){
          byte[] magic=LanTools.magicPacket(mac);
          for(String address:destinations){
            InetAddress target=InetAddress.getByName(address);
            for(int port:new int[]{9,7}){
              for(int attempt=0;attempt<3;attempt++){
                try{
                  socket.send(new DatagramPacket(magic,magic.length,target,port));
                  count++;
                }catch(Exception e){failed++;lastError=e.getClass().getSimpleName();}
              }
            }
          }
        }
      }catch(Exception ex){failed++;lastError=ex.getClass().getSimpleName();}
      finally{if(socket!=null)socket.close();}
      final int sent=count,errors=failed;
      if(count==0){
        state("Error: no se enviaron paquetes Wake-on-LAN.");
        diagnostic("Interfaz Wi-Fi "+wifi.address.getHostAddress()+" · error "+lastError);
        return;
      }
      state("Señal enviada. Comprobando si la TV vuelve a responder…");
      diagnostic(sent+" datagramas UDP enviados; "+errors+" errores. "+
        "No existe confirmación de recepción. MAC: "+String.join(", ",macs)+
        ". Broadcast: "+wifi.broadcast);
      for(int i=0;i<15;i++){
        try{Thread.sleep(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        if(isControlReachable(wifi.network,host)){
          state("La TV vuelve a responder en red. Pulsa Conectar.");
          diagnostic("La IP "+host+" responde en un puerto webOS tras el envío. "+
            "Verifica que la pantalla realmente se haya encendido.");
          return;
        }
      }
      state("No se detecta la televisión después de 30 segundos.");
      diagnostic("Enviados: "+sent+" · fallos: "+errors+" · Wi-Fi "+
        wifi.address.getHostAddress()+"/"+wifi.prefix+
        " · difusión "+wifi.broadcast+
        " · MAC: "+String.join(", ",macs)+
        ". El envío no garantiza que la TV lo reciba.");
    },"lg-wake-on-lan").start();
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
        if(!connected){sendWake();return;}
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


  // Native Android widgets rather than a custom Canvas. Each button is an actual
  // interactive View, so it remains visible with device-specific renderers.
  private LinearLayout createRemote(){
    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(13),dp(15),dp(13),dp(22));
    GradientDrawable shell=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{Color.rgb(57,59,63),Color.rgb(17,19,21)});
    shell.setCornerRadius(dp(43));
    shell.setStroke(dp(2),Color.rgb(90,91,94));
    panel.setBackground(shell);

    TextView heading=new TextView(this);
    heading.setText("LG  ·  MAGIC REMOTE");
    heading.setTextColor(Color.rgb(178,182,183));
    heading.setTextSize(11);
    heading.setLetterSpacing(0.10f);
    heading.setGravity(Gravity.CENTER);
    heading.setPadding(0,dp(4),0,dp(15));
    panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(42)));

    remoteRow(panel,new String[]{"⏻","LG","🎙"},new String[]{"POWER","","MIC"},
      new int[]{0,0,0});
    addRemoteSection(panel,"TECLADO NUMÉRICO");
    remoteRow(panel,new String[]{"1","2","3"},new String[]{"1","2","3"},null);
    remoteRow(panel,new String[]{"4","5","6"},new String[]{"4","5","6"},null);
    remoteRow(panel,new String[]{"7","8","9"},new String[]{"7","8","9"},null);
    remoteRow(panel,new String[]{"GUIDE","0","•••"},new String[]{"GUIDE","0","MORE"},null);

    addRemoteSection(panel,"VOLUMEN Y CANALES");
    remoteRow(panel,new String[]{"VOL +","✕","CH ▲"},new String[]{"VOLUP","MUTE","CHUP"},null);
    remoteRow(panel,new String[]{"VOL −","ⓘ","CH ▼"},new String[]{"VOLDOWN","INFO","CHDOWN"},null);
    remoteRow(panel,new String[]{"⌂ INICIO","OK","▣ INPUT"},new String[]{"HOME","ENTER","INPUT"},null);

    addRemoteSection(panel,"RUEDA DE NAVEGACIÓN");
    remoteRow(panel,new String[]{"","▲",""},new String[]{"","UP",""},null);
    remoteRow(panel,new String[]{"◀","OK","▶"},new String[]{"LEFT","ENTER","RIGHT"},
      new int[]{0,Color.rgb(67,104,96),0});
    remoteRow(panel,new String[]{"","▼",""},new String[]{"","DOWN",""},null);
    remoteRow(panel,new String[]{"↶ ATRÁS","⚙ AJUSTES"},new String[]{"BACK","SETTINGS"},null);

    addRemoteSection(panel,"BOTONES DE COLOR");
    remoteRow(panel,new String[]{"●","●","●","●"},new String[]{"RED","GREEN","YELLOW","BLUE"},
      new int[]{Color.rgb(204,64,83),Color.rgb(48,150,108),
        Color.rgb(207,159,43),Color.rgb(58,116,183)});

    addRemoteSection(panel,"ACCESOS DIRECTOS");
    remoteRow(panel,new String[]{"NETFLIX","prime video"},new String[]{"NETFLIX","PRIME"},
      new int[]{Color.rgb(72,42,47),Color.rgb(42,65,78)});
    remoteRow(panel,new String[]{"Disney+","Rakuten TV"},new String[]{"DISNEY","RAKUTEN"},null);
    remoteRow(panel,new String[]{"LG Channels","alexa"},new String[]{"LGCHANNELS","ALEXA"},null);

    TextView footer=new TextView(this);
    footer.setText("Mando experimental · LG webOS");
    footer.setTextColor(Color.rgb(155,158,163));
    footer.setTextSize(11);
    footer.setGravity(Gravity.CENTER);
    footer.setPadding(0,dp(21),0,0);
    panel.addView(footer,new LinearLayout.LayoutParams(-1,dp(47)));
    return panel;
  }

  private void addRemoteSection(LinearLayout parent,String label){
    TextView text=new TextView(this);
    text.setText(label);
    text.setTextSize(10);
    text.setLetterSpacing(0.06f);
    text.setTextColor(Color.rgb(160,165,167));
    text.setGravity(Gravity.CENTER);
    text.setPadding(0,dp(11),0,0);
    parent.addView(text,new LinearLayout.LayoutParams(-1,dp(35)));
  }

  private void remoteRow(LinearLayout parent,String[] labels,String[] actions,int[] fills){
    LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,dp(62));
    parent.addView(row,rowParams);
    for(int i=0;i<labels.length;i++){
      String text=labels[i],cmd=actions[i];
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(53),1f);
      p.setMargins(dp(3),dp(3),dp(3),dp(3));
      if(cmd.isEmpty()){
        TextView decorative=new TextView(this);
        decorative.setText(text);
        decorative.setTextColor(Color.rgb(174,180,182));
        decorative.setTextSize(19);
        decorative.setTypeface(null,1);
        decorative.setGravity(Gravity.CENTER);
        row.addView(decorative,p);
        continue;
      }
      Button button=new Button(this);
      button.setAllCaps(false);
      button.setText(text);
      button.setTextSize(text.length()>9?11:(text.length()>5?12:18));
      button.setTypeface(null,1);
      button.setTextColor(cmd.equals("POWER")?Color.rgb(255,91,107):Color.rgb(244,247,247));
      button.setPadding(0,0,0,0);
      button.setMinWidth(0);
      button.setMinHeight(0);
      int backgroundColor=(fills!=null && i<fills.length && fills[i]!=0)
         ? fills[i] : Color.rgb(64,67,71);
      GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[]{backgroundColor,dimColor(backgroundColor)});
      background.setCornerRadius(dp(23));
      background.setStroke(dp(1),Color.rgb(92,96,99));
      button.setBackground(background);
      button.setContentDescription(cmd.equals("MIC")?"Micrófono no disponible":text);
      button.setOnClickListener(v->{
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        action(cmd);
      });
      row.addView(button,p);
    }
  }
  private int dimColor(int c){
    return Color.rgb((int)(Color.red(c)*.65f),(int)(Color.green(c)*.65f),(int)(Color.blue(c)*.65f));
  }

}
