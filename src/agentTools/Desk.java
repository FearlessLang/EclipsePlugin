package agentTools;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/// The screen with its pointer and keyboard, as a person would see and use them.
public interface Desk extends AutoCloseable{
  enum Button{
    left(0x110,InputEvent.BUTTON1_DOWN_MASK), middle(0x112,InputEvent.BUTTON2_DOWN_MASK), right(0x111,InputEvent.BUTTON3_DOWN_MASK);
    final int evdev; final int mask;
    Button(int evdev, int mask){ this.evdev= evdev; this.mask= mask; }
  }
  enum Key{
    a(0x61,KeyEvent.VK_A), control(0xffe3,KeyEvent.VK_CONTROL), alt(0xffe9,KeyEvent.VK_ALT), f4(0xffc1,KeyEvent.VK_F4);
    final int keysym; final int code;
    Key(int keysym, int code){ this.keysym= keysym; this.code= code; }
  }
  void move(int x, int y);
  void button(Button b, boolean down);
  void key(Key k, boolean down);
  BufferedImage shot();
  @Override void close();
  static Desk open(){ return System.getenv("WAYLAND_DISPLAY")==null ? new Awt() : new Mutter(); }
}
