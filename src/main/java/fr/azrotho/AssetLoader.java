package fr.azrotho;

import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import javax.media.Manager;
import javax.media.NoPlayerException;
import javax.media.Player;
import org.dvb.ui.FontFactory;
import org.havi.ui.HSound;

/**
 * Chargeur d'assets stockes sur le disque Blu-ray dans le dossier {@code /assets/}
 * (hors de {@code 00000.jar} afin de ne pas consommer les ~4 Mo de l'AppCache BD-J).
 *
 * <p>Exemples d'utilisation :
 * <pre>
 * Image logo    = AssetLoader.loadImage("logo.png");
 * String texte  = AssetLoader.loadText("dialogues/intro.txt");
 * HSound bip    = AssetLoader.loadSound("sfx/beep.wav");
 * bip.play();
 * </pre>
 */
public final class AssetLoader {

    private static final String ASSETS_DIR_NAME = "assets";
    private static final Hashtable IMAGE_CACHE = new Hashtable();
    private static int imageCacheGeneration = 0;
    private static final long IMAGE_LOAD_TIMEOUT_MS = 30000L;
    private static final char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private AssetLoader() {
    }

    // -------------------------------------------------------------------------
    // Resolution de chemins sur le VFS Blu-ray
    // -------------------------------------------------------------------------

    /**
     * Retourne la racine du systeme de fichiers virtuel du Blu-ray ({@code bluray.vfs.root}).
     */
    public static String getDiscRoot() {
        String root = System.getProperty("bluray.vfs.root");
        if (root == null || root.length() == 0) {
            return ".";
        }
        return root;
    }

    /**
     * Resout un chemin relatif (ex. {@code "images/hero.png"} ou {@code "assets/images/hero.png"})
     * vers son {@link File} sur le disque Blu-ray.
     */
    public static File getFile(String path) {
        String rel = normalizeRelativePath(path);
        String vfsRoot = System.getProperty("bluray.vfs.root");

        if (vfsRoot != null && vfsRoot.length() > 0) {
            File primary = resolveWithVfsCacheTouch(new File(vfsRoot), ASSETS_DIR_NAME + "/" + rel);
            if (primary.exists()) {
                return primary;
            }
            // Fallback : BDMV/JAR/00000/<rel>
            File jarDirFallback = resolveWithVfsCacheTouch(new File(vfsRoot), "BDMV/JAR/00000/" + rel);
            if (jarDirFallback.exists()) {
                return jarDirFallback;
            }
            return primary;
        }

        return new File(ASSETS_DIR_NAME, rel);
    }

    /**
     * Verifie si un asset existe dans {@code assets/}.
     */
    public static boolean exists(String path) {
        return getFile(path).exists();
    }

    /**
     * Ouvre un {@link InputStream} bufferise vers un asset du dossier {@code assets/}.
     */
    public static InputStream openStream(String path) throws IOException {
        File file = getFile(path);
        if (!file.exists()) {
            throw new FileNotFoundException("Asset introuvable : " + file.getAbsolutePath());
        }
        return new BufferedInputStream(new FileInputStream(file));
    }

    /**
     * Lit un asset binaire complet en memoire.
     */
    public static byte[] loadBytes(String path) throws IOException {
        InputStream in = openStream(path);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------
    // Textes & fichiers de configuration
    // -------------------------------------------------------------------------

    /**
     * Charge un fichier texte en UTF-8 depuis {@code assets/}.
     */
    public static String loadText(String path) throws IOException {
        return loadText(path, "UTF-8");
    }

    /**
     * Charge un fichier texte avec l'encodage indique depuis {@code assets/}.
     */
    public static String loadText(String path, String encoding) throws IOException {
        InputStream in = openStream(path);
        try {
            InputStreamReader reader = new InputStreamReader(in, encoding);
            StringBuffer sb = new StringBuffer();
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Charge les lignes d'un fichier texte UTF-8 sous forme de tableau {@code String[]}.
     */
    public static String[] loadLines(String path) throws IOException {
        InputStream in = openStream(path);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            Vector lines = new Vector();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.addElement(line);
            }
            String[] result = new String[lines.size()];
            lines.copyInto(result);
            return result;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Charge un fichier {@code .properties} depuis {@code assets/}.
     */
    public static Properties loadProperties(String path) throws IOException {
        InputStream in = openStream(path);
        try {
            Properties props = new Properties();
            props.load(in);
            return props;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------
    // Images (PNG, JPG, GIF)
    // -------------------------------------------------------------------------

    /**
     * Charge et decode immediatement une image depuis {@code assets/} (avec mise en cache).
     */
    public static Image loadImage(String path) throws IOException {
        return loadImage(path, true);
    }

    /**
     * Charge et decode immediatement une image depuis {@code assets/}.
     *
     * @param path  chemin relatif dans {@code assets/} (ex. {@code "sprites/player.png"})
     * @param cache {@code true} pour garder l'image en cache memoire
     */
    public static Image loadImage(String path, boolean cache) throws IOException {
        String key = normalizeRelativePath(path);
        int cacheGen = 0;
        if (cache) {
            synchronized (IMAGE_CACHE) {
                Image cached = (Image) IMAGE_CACHE.get(key);
                if (cached != null) {
                    return cached;
                }
                cacheGen = imageCacheGeneration;
            }
        }

        byte[] data = loadBytes(key);
        Image img = Toolkit.getDefaultToolkit().createImage(data, 0, data.length);
        waitForImage(img);

        if (cache && img != null) {
            synchronized (IMAGE_CACHE) {
                if (cacheGen == imageCacheGeneration) {
                    Image existing = (Image) IMAGE_CACHE.get(key);
                    if (existing != null) {
                        img.flush();
                        return existing;
                    }
                    IMAGE_CACHE.put(key, img);
                }
            }
        }
        return img;
    }

    /**
     * Vide le cache d'images et libere les ressources graphiques associees.
     */
    public static void clearImageCache() {
        synchronized (IMAGE_CACHE) {
            imageCacheGeneration++;
            Enumeration e = IMAGE_CACHE.elements();
            while (e.hasMoreElements()) {
                Image img = (Image) e.nextElement();
                if (img != null) {
                    img.flush();
                }
            }
            IMAGE_CACHE.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Sons & Audio (HAVi HSound / JMF Player)
    // -------------------------------------------------------------------------

    /**
     * Charge un effet sonore (ex. {@code .wav}) dans un {@link HSound} pret a jouer
     * avec {@code sound.play()} ou {@code sound.loop()}.
     */
    public static HSound loadSound(String path) throws IOException {
        File file = getFile(path);
        if (!file.exists()) {
            throw new FileNotFoundException("Son introuvable : " + file.getAbsolutePath());
        }
        HSound sound = new HSound();
        sound.load(toFileUrl(file));
        return sound;
    }

    /**
     * Charge et joue immediatement un effet sonore depuis {@code assets/}.
     */
    public static HSound playSound(String path) throws IOException {
        HSound sound = loadSound(path);
        sound.play();
        return sound;
    }

    /**
     * Cree un {@link Player} JMF pour lire une piste audio depuis {@code assets/}.
     */
    public static Player loadPlayer(String path) throws IOException {
        File file = getFile(path);
        if (!file.exists()) {
            throw new FileNotFoundException("Audio introuvable : " + file.getAbsolutePath());
        }
        try {
            URL url = toFileUrl(file);
            return Manager.createPlayer(url);
        } catch (NoPlayerException e) {
            throw new IOException("Aucun lecteur JMF disponible pour " + file.getAbsolutePath() + " : " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Polices (TTF / OTF via DVB FontFactory)
    // -------------------------------------------------------------------------

    /**
     * Charge une police {@code .otf}/{@code .ttf} depuis {@code assets/}.
     */
    public static Font loadFont(String path, String fontName, int style, int size) throws Exception {
        if (fontName == null) {
            throw new IllegalArgumentException("Nom de police null");
        }
        File file = getFile(path);
        if (!file.exists()) {
            throw new FileNotFoundException("Police introuvable : " + file.getAbsolutePath());
        }
        FontFactory factory = new FontFactory(toFileUrl(file));
        return factory.createFont(fontName, style, size);
    }

    // -------------------------------------------------------------------------
    // Utilitaires internes
    // -------------------------------------------------------------------------

    private static void removeConsumerQuietly(Image image) {
        if (image instanceof ImageConsumer) {
            ImageProducer producer = image.getSource();
            if (producer != null) {
                producer.removeConsumer((ImageConsumer) image);
            }
        }
    }

    private static boolean hasImageError(Image image) {
        Object err = image.getProperty("error", null);
        if (err != null && err != Image.UndefinedProperty) {
            return true;
        }
        return image.getWidth(null) <= 0 || image.getHeight(null) <= 0;
    }

    private static URL toFileUrl(File file) throws IOException {
        String absPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        if (!absPath.startsWith("/")) {
            absPath = "/" + absPath;
        }
        byte[] utf8;
        try {
            utf8 = absPath.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            utf8 = absPath.getBytes();
        }
        StringBuffer encoded = new StringBuffer(utf8.length + 16);
        for (int i = 0; i < utf8.length; i++) {
            int b = utf8[i] & 0xFF;
            boolean unreserved = (b >= 'A' && b <= 'Z')
                || (b >= 'a' && b <= 'z')
                || (b >= '0' && b <= '9')
                || b == '/' || b == '-' || b == '_' || b == '.' || b == '~';
            if (unreserved || (b == ':' && i == 2 && utf8.length > 3 && utf8[3] == '/')) {
                encoded.append((char) b);
            } else {
                encoded.append('%');
                encoded.append(HEX_DIGITS[(b >> 4) & 0x0F]);
                encoded.append(HEX_DIGITS[b & 0x0F]);
            }
        }
        return new URL("file", "", encoded.toString());
    }

    private static void waitForImage(final Image img) throws IOException {
        if (img == null) {
            throw new IOException("Impossible de creer l'image");
        }
        final Object lock = new Object();
        final int[] state = new int[1]; // 0 = en cours, 1 = OK, -1 = erreur, -2 = timeout

        ImageObserver observer = new ImageObserver() {
            public boolean imageUpdate(Image image, int infoflags, int x, int y, int width, int height) {
                if ((infoflags & ImageObserver.ALLBITS) != 0) {
                    // Contournement d'un bug de libbluray : BDImageConsumer.imageComplete(STATICIMAGEDONE)
                    // ne se desinscrit pas de l'ImageProducer, ce qui pousse InputStreamImageSource.doFetch()
                    // a envoyer IMAGEERROR dans son bloc finally juste apres un decodage reussi.
                    // En cas d'erreur de decodage (ex. PNGImageDecoder), le decodeur peut aussi definir
                    // la propriete "error" avant STATICIMAGEDONE sans jamais envoyer IMAGEERROR.
                    removeConsumerQuietly(image);
                    boolean failed = hasImageError(image);
                    synchronized (lock) {
                        if (failed) {
                            if (state[0] == 0) {
                                state[0] = -1;
                            }
                        } else {
                            state[0] = 1;
                        }
                        lock.notifyAll();
                    }
                    return false;
                }
                if ((infoflags & (ImageObserver.ERROR | ImageObserver.ABORT)) != 0) {
                    removeConsumerQuietly(image);
                    synchronized (lock) {
                        if (state[0] == 0) {
                            state[0] = -1;
                        }
                        lock.notifyAll();
                    }
                    return false;
                }
                return true;
            }
        };

        if (Toolkit.getDefaultToolkit().prepareImage(img, -1, -1, observer)) {
            removeConsumerQuietly(img);
            if (hasImageError(img)) {
                throw new IOException("Erreur lors du decodage de l'image");
            }
            return;
        }

        long deadline = System.currentTimeMillis() + IMAGE_LOAD_TIMEOUT_MS;
        synchronized (lock) {
            while (state[0] == 0) {
                int flags = Toolkit.getDefaultToolkit().checkImage(img, -1, -1, null);
                if ((flags & ImageObserver.ALLBITS) != 0) {
                    removeConsumerQuietly(img);
                    state[0] = hasImageError(img) ? -1 : 1;
                    break;
                }
                if ((flags & (ImageObserver.ERROR | ImageObserver.ABORT)) != 0) {
                    removeConsumerQuietly(img);
                    state[0] = -1;
                    break;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    removeConsumerQuietly(img);
                    state[0] = -2;
                    break;
                }
                long waitMs = remaining < 50L ? remaining : 50L;
                try {
                    lock.wait(waitMs);
                } catch (InterruptedException e) {
                    removeConsumerQuietly(img);
                    Thread.currentThread().interrupt();
                    throw new IOException("Chargement de l'image interrompu");
                }
            }
        }

        if (state[0] == -2) {
            throw new IOException("Delai d'attente depasse lors du decodage de l'image");
        }
        if (state[0] < 0 || hasImageError(img)) {
            removeConsumerQuietly(img);
            throw new IOException("Erreur lors du decodage de l'image");
        }
    }

    private static String normalizeRelativePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Chemin d'asset null");
        }
        String p = path.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith("assets/")) {
            p = p.substring("assets/".length());
            while (p.startsWith("/")) {
                p = p.substring(1);
            }
        }
        return p;
    }

    /**
     * Parcourt chaque dossier parent pour forcer le VFSCache de libbluray (en mode ISO UDF)
     * a creer les repertoires intermediaires avant l'acces au fichier.
     */
    private static File resolveWithVfsCacheTouch(File root, String relPath) {
        File current = root;
        int start = 0;
        int slash;
        while ((slash = relPath.indexOf('/', start)) != -1) {
            String part = relPath.substring(start, slash);
            if (part.length() > 0) {
                current = new File(current, part);
                current.exists();
            }
            start = slash + 1;
        }
        String last = relPath.substring(start);
        if (last.length() > 0) {
            current = new File(current, last);
        }
        return current;
    }
}
