package fr.azrotho;

public class Main extends java.awt.Container implements javax.tv.xlet.Xlet {

    private org.havi.ui.HScene scene;
    private java.awt.Font font;
    private java.awt.Image image;

    public void initXlet(javax.tv.xlet.XletContext context) {
        setSize(1280, 720);
        try {
            image = AssetLoader.loadImage("img/kekw.png");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        scene = org.havi.ui.HSceneFactory.getInstance().getDefaultHScene();
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

    public void paint(java.awt.Graphics g) {
        g.setColor(new java.awt.Color(0x000000));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (image != null) {
            int size = 400;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g.drawImage(image, x, y, size, size, this);
        }
        g.setColor(new java.awt.Color(0xffffff));
        if (font == null) {
            font = new java.awt.Font(null, java.awt.Font.PLAIN, 30);
        }
        g.setFont(font);
        g.drawString("Hello World!", 50, 50);
    }
}
