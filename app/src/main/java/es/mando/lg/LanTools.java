package es.mando.lg;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Small, platform-independent helpers for Wake-on-LAN.
 * Testable without an actual television or Android device.
 */
public final class LanTools {
  private LanTools(){}

  public static String normalizeMAC(String raw) {
    if(raw==null)return "";
    String text=raw.trim().replaceAll("[^0-9A-Fa-f]","").toUpperCase(Locale.US);
    if(text.length()!=12 || text.equals("000000000000") || text.equals("FFFFFFFFFFFF"))
      return "";
    try {
      int first=Integer.parseInt(text.substring(0,2),16);
      if((first & 1)!=0)return ""; // reject multicast MAC
      StringBuilder normalized=new StringBuilder();
      for(int i=0;i<12;i+=2){
        if(i>0)normalized.append(':');
        normalized.append(text,i,i+2);
      }
      return normalized.toString();
    } catch(NumberFormatException ignored) { return ""; }
  }

  public static byte[] magicPacket(String mac) {
    String normalized=normalizeMAC(mac);
    if(normalized.isEmpty())throw new IllegalArgumentException("Invalid unicast MAC");
    String compact=normalized.replace(":","");
    byte[] bytes=new byte[6];
    for(int i=0;i<6;i++)bytes[i]=(byte)Integer.parseInt(compact.substring(i*2,i*2+2),16);
    byte[] result=new byte[102];
    for(int i=0;i<6;i++)result[i]=(byte)0xff;
    for(int i=6;i<result.length;i++)result[i]=bytes[(i-6)%6];
    return result;
  }

  public static String directedBroadcast(Inet4Address address,int prefixLength) {
    if(prefixLength<0 || prefixLength>32)throw new IllegalArgumentException("Invalid IPv4 prefix");
    byte[] bytes=address.getAddress();
    long ip=0;
    for(byte b:bytes)ip=(ip<<8)|(b&255L);
    long mask=prefixLength==0?0:(0xffffffffL << (32-prefixLength)) & 0xffffffffL;
    long broadcast=(ip & mask) | (~mask & 0xffffffffL);
    return ((broadcast>>>24)&255)+"."+((broadcast>>>16)&255)+"."+
        ((broadcast>>>8)&255)+"."+(broadcast&255);
  }

  public static boolean sameSubnet(Inet4Address a,Inet4Address b,int prefix) {
    if(prefix<0||prefix>32)return false;
    byte[] x=a.getAddress(),y=b.getAddress();
    int whole=prefix/8,remaining=prefix%8;
    for(int i=0;i<whole;i++)if(x[i]!=y[i])return false;
    if(remaining>0){
      int mask=(0xff << (8-remaining))&255;
      if(((x[whole]&255)&mask)!=((y[whole]&255)&mask))return false;
    }
    return true;
  }
}
