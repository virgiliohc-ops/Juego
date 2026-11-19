package es.mando.lg;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.Inet4Address;
import java.net.InetAddress;

public class LanToolsTest {
  private Inet4Address ip(String s)throws Exception{return (Inet4Address)InetAddress.getByName(s);}

  @Test public void testMACNormalization() {
    assertEquals("02:11:22:33:44:55",LanTools.normalizeMAC("02-11-22-33-44-55"));
    assertEquals("02:11:22:33:44:55",LanTools.normalizeMAC("021122334455"));
    assertEquals("",LanTools.normalizeMAC("invalid"));
    assertEquals("",LanTools.normalizeMAC("FF:FF:FF:FF:FF:FF"));
    assertEquals("",LanTools.normalizeMAC("01:00:5E:00:00:01"));
  }

  @Test public void testMagicPacket() {
    byte[] p=LanTools.magicPacket("02:11:22:33:44:55");
    assertEquals(102,p.length);
    for(int i=0;i<6;i++)assertEquals(255,p[i]&255);
    int[] mac={0x02,0x11,0x22,0x33,0x44,0x55};
    for(int i=6;i<p.length;i++)assertEquals(mac[(i-6)%6],p[i]&255);
  }

  @Test public void testIPv4Broadcast()throws Exception{
    assertEquals("192.168.0.255",LanTools.directedBroadcast(ip("192.168.0.19"),24));
    assertEquals("192.168.1.255",LanTools.directedBroadcast(ip("192.168.0.19"),23));
    assertEquals("192.168.0.127",LanTools.directedBroadcast(ip("192.168.0.19"),25));
    assertEquals("192.168.255.255",LanTools.directedBroadcast(ip("192.168.0.19"),16));
    assertEquals("10.255.255.255",LanTools.directedBroadcast(ip("10.1.2.3"),8));
  }

  @Test public void testSubnet()throws Exception{
    assertTrue(LanTools.sameSubnet(ip("192.168.0.11"),ip("192.168.1.10"),23));
    assertFalse(LanTools.sameSubnet(ip("192.168.0.11"),ip("192.168.1.10"),24));
    assertTrue(LanTools.sameSubnet(ip("10.0.1.33"),ip("10.0.1.99"),25));
    assertFalse(LanTools.sameSubnet(ip("10.0.1.33"),ip("10.0.1.200"),25));
  }
}
