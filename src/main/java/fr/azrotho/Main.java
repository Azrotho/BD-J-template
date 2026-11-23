package fr.azrotho;

import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;

import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;
import java.awt.*;

public class Main extends Container implements Xlet {

    private HScene scene;
    private Font font;
    private Image image;

    public void initXlet(XletContext context) {
        setSize(1280, 720);
        try {
            image = AssetLoader.loadImage("img/kekw.png");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        scene = HSceneFactory.getInstance().getDefaultHScene();
        scene.add(this);
        scene.validate();
    }

    public void startXlet() {
        setVisible(true);
        scene.setVisible(true);
        repaint();
    }

    public void pauseXlet() {
        setVisible(false);
    }

    public void destroyXlet(boolean unconditional) {
        AssetLoader.clearImageCache();
        image = null;
        if (scene != null) {
            scene.remove(this);
            scene = null;
        }
    }

    public void paint(Graphics g) {
        g.setColor(new Color(0x000000));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (image != null) {
            int size = 400;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g.drawImage(image, x, y, size, size, this);
        }
        g.setColor(new Color(0xffffff));
        if (font == null) {
            font = new Font(null, Font.PLAIN, 30);
        }
        g.setFont(font);
        g.drawString("Hello World!!!! :333", 50, 50);
    }
}
