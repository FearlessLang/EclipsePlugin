package gui;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

import javax.swing.SwingUtilities;

/// No tray support (some desktops), or a failure while adding the icon, is not an error.
/// The icon lives for the whole process life and is removed by process death.
public final class Tray{
  private Tray(){}
  public static void install(Window window, Runnable onQuit){ SwingUtilities.invokeLater(()->tryInstall(window,onQuit)); }
  private static void tryInstall(Window window, Runnable onQuit){
    if (!SystemTray.isSupported()){ return; }
    var show= new MenuItem("Show manager");
    show.addActionListener(_->window.show());
    var quit= new MenuItem("Quit manager");
    quit.addActionListener(_->onQuit.run());
    var menu= new PopupMenu();
    menu.add(show);
    menu.addSeparator();
    menu.add(quit);
    var icon= new TrayIcon(Icons.app(),"Fearless Manager",menu);
    icon.setImageAutoSize(true);
    icon.addActionListener(_->window.show());
    try{ SystemTray.getSystemTray().add(icon); }
    catch(AWTException|UnsupportedOperationException|SecurityException e){}
  }
}