package agentTools;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import tools.Fs;

/// The desk on a GNOME wayland session: a python helper holds a mutter remote desktop session (pointer and keyboard) and its screen cast (screenshots) open on the session bus. It unlocks the screen and inhibits the screensaver for as long as the helper process runs; closing the desk ends the process, dropping the inhibit and letting the screensaver lock again normally.
final class Mutter implements Desk{
  private static final String helper= """
    import gi,sys,subprocess
    from gi.repository import Gio,GLib
    bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)
    def call(name,path,iface,method,args=None): return bus.call_sync(name,path,iface,method,args,None,Gio.DBusCallFlags.NONE,-1,None)
    RD='org.gnome.Mutter.RemoteDesktop';SC='org.gnome.Mutter.ScreenCast';DC='org.gnome.Mutter.DisplayConfig'
    SS='org.gnome.ScreenSaver';FS='org.freedesktop.ScreenSaver'
    call(SS,'/org/gnome/ScreenSaver',SS,'SetActive',GLib.Variant('(b)',(False,)))
    call(FS,'/org/freedesktop/ScreenSaver',FS,'Inhibit',GLib.Variant('(ss)',('agentTools','driving the desktop')))
    monitor=call(DC,'/org/gnome/Mutter/DisplayConfig',DC,'GetCurrentState')[1][0][0][0]
    s=call(RD,'/org/gnome/Mutter/RemoteDesktop',RD,'CreateSession')[0]
    sid=call(RD,s,'org.freedesktop.DBus.Properties','Get',GLib.Variant('(ss)',(RD+'.Session','SessionId')))[0]
    sc=call(SC,'/org/gnome/Mutter/ScreenCast',SC,'CreateSession',GLib.Variant('(a{sv})',({'remote-desktop-session-id':GLib.Variant('s',sid)},)))[0]
    stream=call(SC,sc,SC+'.Session','RecordMonitor',GLib.Variant('(sa{sv})',(monitor,{'cursor-mode':GLib.Variant('u',0)})))[0]
    loop=GLib.MainLoop();node=[]
    bus.signal_subscribe(None,SC+'.Stream','PipeWireStreamAdded',stream,None,Gio.DBusSignalFlags.NONE,lambda *a:(node.append(a[5][0]),loop.quit()))
    GLib.timeout_add(5000,loop.quit)
    call(RD,s,RD+'.Session','Start');loop.run()
    def rd(m,args): call(RD,s,RD+'.Session',m,args)
    for line in sys.stdin:
      p=line.split()
      if p[0]=='shot': subprocess.run(['gst-launch-1.0','-q','pipewiresrc','path=%d'%node[0],'num-buffers=1','!','videoconvert','!','video/x-raw,format=RGB','!','pngenc','!','filesink','location='+p[1]],check=True)
      elif p[0]=='move': rd('NotifyPointerMotionAbsolute',GLib.Variant('(sdd)',(stream,float(p[1]),float(p[2]))))
      elif p[0]=='button': rd('NotifyPointerButton',GLib.Variant('(ib)',(int(p[1]),p[2]=='1')))
      elif p[0]=='key': rd('NotifyKeyboardKeysym',GLib.Variant('(ub)',(int(p[1]),p[2]=='1')))
      print('ok',flush=True)
    """;
  private final Process p= Fs.of(()->new ProcessBuilder("python3","-c",helper).redirectError(ProcessBuilder.Redirect.INHERIT).start());
  private final BufferedWriter in= p.outputWriter();
  private final BufferedReader out= p.inputReader();
  private void send(String cmd){
    Fs.ofV(()->{ in.write(cmd); in.newLine(); in.flush(); });
    var reply= Fs.of(out::readLine);
    assert reply.equals("ok");
  }
  @Override public void move(int x, int y){ send("move "+x+" "+y); }
  @Override public void button(Button b, boolean down){ send("button "+b.evdev+" "+(down?1:0)); }
  @Override public void key(Key k, boolean down){ send("key "+k.keysym+" "+(down?1:0)); }
  @Override public BufferedImage shot(){
    var file= Fs.of(()->Files.createTempFile("shot",".png"));
    send("shot "+file);
    var res= Fs.of(()->ImageIO.read(file.toFile()));
    Fs.ofV(()->Files.delete(file));
    return res;
  }
  @Override public void close(){
    Fs.ofV(in::close);
    try{ p.waitFor(); }
    catch(InterruptedException e){ throw new RuntimeException(e); }
  }
}
