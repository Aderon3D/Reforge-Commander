package forge.util;

import forge.localinstance.properties.ForgeConstants;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SwingImageFetcher extends ImageFetcher {

    // REFORGE COMMANDER EXTENSION — Scryfall image pacing, kept local on purpose.
    // Upstream moved its Scryfall throttling into forge.util.ScryfallRateLimiter,
    // which by design only covers api.scryfall.com and never the CDN image URLs we
    // download from. Inheriting that limiter would silently stop pacing image
    // fetches, so the pacing lives here instead of in the upstream-owned
    // ImageFetcher, which upstream refactors freely.
    private static final long SCRYFALL_IMAGE_MIN_INTERVAL_MS = 100;
    private static final long SCRYFALL_IMAGE_COOLDOWN_MINUTES = 5;
    private static final Object scryfallImagePacing = new Object();
    private static volatile Date scryfallImageCooldownTime = null;
    private static long lastScryfallImageRequest = 0;

    private static boolean isScryfallImage(final String url) {
        return url != null && url.startsWith(ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD);
    }

    /** Whether we are still backing off after Scryfall rate limited us. Clears an expired cooldown. */
    private static boolean scryfallImageCoolingDown() {
        final Date cooldown = scryfallImageCooldownTime;
        if (cooldown == null) {
            return false;
        }
        if (cooldown.after(new Date())) {
            return true;
        }
        scryfallImageCooldownTime = null;
        return false;
    }

    /** Whether this download should be skipped because we are backing off Scryfall. */
    private static boolean inScryfallImageCooldown(final String url) {
        if (!isScryfallImage(url) || !scryfallImageCoolingDown()) {
            return false;
        }
        System.err.println("Currently in cooldown period for scryfall downloads. Skipping download attempt for: " + url);
        return true;
    }

    /** Record that Scryfall returned 429, so we stop asking for a while. */
    private static void noteScryfallImageRateLimited() {
        scryfallImageCooldownTime = new Date(System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(SCRYFALL_IMAGE_COOLDOWN_MINUTES));
    }

    /**
     * Space Scryfall requests out. Downloads run on a work stealing pool, so without this a screen
     * full of missing images asks for all of them at once. Called from the download task, never the
     * EDT.
     */
    private static void paceScryfallImage(final String url) {
        if (!isScryfallImage(url)) {
            return;
        }
        synchronized (scryfallImagePacing) {
            final long wait = lastScryfallImageRequest + SCRYFALL_IMAGE_MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastScryfallImageRequest = System.currentTimeMillis();
        }
    }

    @Override
    protected Runnable getDownloadTask(String[] downloadUrls, String destPath, Runnable notifyObservers) {
        return new SwingDownloadTask(downloadUrls, destPath, notifyObservers);
    }

    private static class SwingDownloadTask implements Runnable {
        private static final AtomicInteger TMP_COUNTER = new AtomicInteger();
        private final String[] downloadUrls;
        private final String destPath;
        private final Runnable notifyObservers;

        public SwingDownloadTask(String[] downloadUrls, String destPath, Runnable notifyObservers) {
            this.downloadUrls = downloadUrls;
            this.destPath = destPath;
            this.notifyObservers = notifyObservers;
        }

        private boolean doFetch(String urlToDownload) throws IOException {
            if (disableHostedDownload && urlToDownload.startsWith(ForgeConstants.URL_CARDFORGE)) {
                return false;
            }

            if (inScryfallImageCooldown(urlToDownload)) {
                return false;
            }

            String newdespath = urlToDownload.contains(".fullborder.jpg") || isScryfallImage(urlToDownload) ?
                    TextUtil.fastReplace(destPath, ".full.jpg", ".fullborder.jpg") : destPath;
            if (!newdespath.contains(".full") && !newdespath.contains(".artcrop") && isScryfallImage(urlToDownload) && !destPath.startsWith(ForgeConstants.CACHE_TOKEN_PICS_DIR))
                newdespath = newdespath.replace(".jpg", ".fullborder.jpg");
            URL url = new URL(urlToDownload);
            System.out.println("Attempting to fetch: " + url);
            File targetFile = new File(newdespath);
            if (targetFile.exists() && targetFile.length() > 0) {
                System.out.println("Image already cached: " + newdespath);
                SwingUtilities.invokeLater(notifyObservers);
                return true;
            }

            paceScryfallImage(urlToDownload);

            // Read through a connection rather than ImageIO.read(URL), which discards the response
            // code - without it a 429 is indistinguishable from any other failure and we keep asking.
            final URLConnection connection = url.openConnection();
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("User-Agent", BuildInfo.getUserAgent());
            if (connection instanceof HttpURLConnection httpConnection) {
                final int responseCode = httpConnection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    System.err.println("Failed to fetch image. HTTP code: " + responseCode
                            + " (" + httpConnection.getResponseMessage() + ") for URL: " + urlToDownload);
                    if (responseCode == 429 && isScryfallImage(urlToDownload)) {
                        System.err.println("Rate limited by scryfall. Pausing image downloads.");
                        noteScryfallImageRateLimited();
                    }
                    httpConnection.disconnect();
                    return false;
                }
            }

            BufferedImage image;
            try (InputStream is = connection.getInputStream()) {
                image = ImageIO.read(is);
            }
            if (image == null) {
                System.err.println("ImageIO returned null for " + url);
                return false;
            }

            File destFile = new File(newdespath + ".tmp" + TMP_COUNTER.incrementAndGet());
            destFile.getParentFile().mkdirs();

            if (writeJpeg(image, destFile, 0.65f)) {
                if (moveWithRetry(destFile, new File(newdespath))) {
                    System.out.println("Saved image to " + newdespath);
                    SwingUtilities.invokeLater(notifyObservers);
                } else {
                    System.err.println("Failed to move image to " + newdespath);
                    return false;
                }
            } else {
                System.err.println("Failed to save image from " + url + " as jpeg");
                if (writePng(image, destFile)) {
                    String newPath = newdespath.replace(".jpg", ".png");
                    if (moveWithRetry(destFile, new File(newPath))) {
                        System.out.println("Saved image to " + newPath);
                        SwingUtilities.invokeLater(notifyObservers);
                    } else {
                        System.err.println("Failed to move image to " + newPath);
                    }
                } else {
                    System.err.println("Failed to save image from " + url + " as png");
                }
                return false;
            }

            return true;
        }

        private static boolean moveWithRetry(File source, File target) {
            IOException lastError = null;
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } catch (IOException e) {
                    lastError = e;
                    try {
                        Thread.sleep(100L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.deleteIfExists(source.toPath());
                } catch (IOException ignored) {
                }
                return true;
            } catch (IOException e) {
                lastError = e;
            }
            System.err.println("moveWithRetry failed for " + target + ": " + (lastError != null ? lastError.getMessage() : "unknown"));
            return false;
        }

        private boolean writeJpeg(BufferedImage image, File file, float quality) {
            try {
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                if (!writers.hasNext()) return ImageIO.write(image, "jpg", file);
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(new FileOutputStream(file))) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, null), param);
                }
                writer.dispose();
                return true;
            } catch (IOException e) {
                System.err.println("JPEG write failed: " + e.getMessage());
                return false;
            }
        }

        private boolean writePng(BufferedImage image, File file) {
            try {
                return ImageIO.write(image, "png", file);
            } catch (IOException e) {
                System.err.println("PNG write failed: " + e.getMessage());
                return false;
            }
        }

        private String tofullBorder(String imageurl) {
            if (!imageurl.contains(".full.jpg"))
                return imageurl;
            try {
                URL url = new URL(imageurl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                //connection.setConnectTimeout(1000 * 5); //wait 5 seconds the most
                //connection.setReadTimeout(1000 * 5);
                conn.setRequestProperty("User-Agent", BuildInfo.getUserAgent());
                if(conn.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
                    imageurl = TextUtil.fastReplace(imageurl, ".full.jpg", ".fullborder.jpg");
                conn.disconnect();
                return imageurl;
            } catch (IOException ex) {
                return imageurl;
            }
        }

        public void run() {
            boolean success = false;
            for (String urlToDownload : downloadUrls) {
                try {
                    if (doFetch(urlToDownload)) {
                        success = true;
                        break;
                    }
                } catch (IOException e) {
                    System.err.println("Failed to download card [" + destPath + "] image: " + e.getMessage());
                    if (urlToDownload.contains("tokens")) {
                        int setIndex = urlToDownload.lastIndexOf('_');
                        int typeIndex = urlToDownload.lastIndexOf('.');
                        String setlessFilename = urlToDownload.substring(0, setIndex);
                        String extension = urlToDownload.substring(typeIndex);
                        urlToDownload = setlessFilename+extension;
                        try {
                            if (doFetch(urlToDownload)) {
                                success = true;
                                break;
                            }
                        } catch (IOException t) {
                            System.err.println("Failed to download setless token [" + destPath + "]: " + e.getMessage());
                        }
                    }
                }
            }
            // If all downloads fail, mark this image as unfetchable so we don't try again.
        }
    }

}
