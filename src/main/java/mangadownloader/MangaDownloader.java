package mangadownloader;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class MangaDownloader {
    static class MangaUrl {
        String title;
        String href;
        MangaUrl(String title, String href) {
            this.title = title;
            this.href = "https://chapmanganato.com/" + href;
        }

        @Override
        public String toString() {
            return title + ";" + href;
        }
    }
    public static void main(String[] args) {
        handleArgs(args);
    }

    private static void handleArgs(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Needs argument. try -i");
        }

        switch (args[0]) {
            // interactive
            case "-i":
                interactiveMangaDownload();

                break;

            // search
            case "-s":
                if (args.length < 2) {
                    throw new RuntimeException("-s needs value.");
                }
                String searchTerm = args[1];
                List<MangaUrl> out = search(searchTerm);
                System.out.println(String.join("\n", out.stream().map(MangaUrl::toString).toArray(String[]::new)));

                break;

            // execute
            case "-x":
                if (args.length < 4) {
                    throw new RuntimeException("usage is -x <url> <startChapter>,<endChapter> <outputPath>");
                }
                String url = args[1];
                String[] chapters = args[2].split(",");
                int startChapter = Integer.parseInt(chapters[0]);
                int endChapter = Integer.parseInt(chapters[1]);
                String outputPath = args[3];

                if (!outputPath.contains(".pdf")) {
                    throw new RuntimeException("output path must end in .pdf");
                }

                String[] fileParts = outputPath.split("/");
                String outputName = fileParts[fileParts.length - 1].split("\\.")[0];
                makeOut();
                downloadAllChapterImages(new MangaUrl(outputName, url), startChapter, endChapter);
                compileImagesIntoPDF(outputName, startChapter, endChapter, outputPath);
                cleanup(outputName);
                break;
        }
    }

    private static void makeOut() {
        File out = new File("./out");
        if (!out.exists()) {
            out.mkdir();
        }
    }

    private static void interactiveMangaDownload() {
        makeOut();
        Console console = System.console();
        System.out.println("Enter search term: ");
        String searchTerm = console.readLine();
        List<MangaUrl> mangaUrls = search(searchTerm);
        for(int i = 0; i < mangaUrls.size() && i < 10; i++) {
            System.out.println(i + ":" + mangaUrls.get(i).title);
        }
        System.out.println("Enter the number of the manga you want: ");
        int selected = Integer.parseInt(console.readLine());
        System.out.println("Enter the first and last chapter separated by a comma: ");
        String[] chapters = console.readLine().split(",");
        MangaUrl selectedManga = mangaUrls.get(selected);
        downloadAllChapterImages(selectedManga, Integer.parseInt(chapters[0]), Integer.parseInt(chapters[1]));
        long time = System.currentTimeMillis();
        String outputPath = "./out/" + selectedManga.title + ".pdf";
        compileImagesIntoPDF(selectedManga.title, Integer.parseInt(chapters[0]), Integer.parseInt(chapters[1]), outputPath);
        System.out.println((System.currentTimeMillis() - time) + "ms");
        cleanup(selectedManga.title);
    }

    private static List<MangaUrl> search(String searchTerm) {
        String escapedSearchTerm = searchTerm.replace(" ", "_");
        Document doc = getDocument(String.format("https://manganelo.com/search/story/%s", escapedSearchTerm));
        List<MangaUrl> mangaUrls= new ArrayList<>();
        if (doc != null) {
            Elements searchStoryItems = doc.select(".search-story-item");
            int i = 0;
            for (Element storyItem : searchStoryItems) {
                Elements titleElement = storyItem.select(".item-title");
                String[] hrefParts = titleElement.get(0).attr("href").split("/");
                MangaUrl mangaUrl = new MangaUrl(titleElement.text(), hrefParts[hrefParts.length - 1]);
                mangaUrls.add(mangaUrl);
                i++;
            }
        }

        return mangaUrls;
    }

    private static void httpGet(String url) {
      CloseableHttpClient httpClient = HttpClients.createDefault();
      HttpGet httpGet = new HttpGet(url);
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
          System.out.println(response.getStatusLine());
          HttpEntity entity = response.getEntity();
          if (entity != null) {
              String result = EntityUtils.toString(entity);
              System.out.println(result);
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
    }
    private static Document getDocument(String url) {
      try {
          return Jsoup.connect(url).get();
      } catch (IOException e) {
          e.printStackTrace();
      }
      return null;
    }

    private static void downloadFile(CloseableHttpAsyncClient client, CountDownLatch successCounter, String url, String filePath, AtomicInteger downloadedCounter) {
        final HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Referer", "https://chapmanganato.com/");

        client.execute(httpGet, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                HttpEntity entity = httpResponse.getEntity();
                try {
                    InputStream is = entity.getContent();
                    FileOutputStream os = new FileOutputStream(filePath);
                    int inByte;
                    while ((inByte = is.read()) != -1) {
                        os.write(inByte);
                    }
                    is.close();
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                successCounter.countDown();
                downloadedCounter.incrementAndGet();
            }

            @Override
            public void failed(Exception e) {
                e.printStackTrace();
                // retry??
                successCounter.countDown();
            }

            @Override
            public void cancelled() {
                // retry??
                successCounter.countDown();
            }
        });
    }

    private static void downloadAllChapterImages(MangaUrl mangaUrl, int firstChapter, int lastChapter) {
        new File("./out/" + mangaUrl.title).mkdir();
        int numChapters = lastChapter - firstChapter + 1;
        CountDownLatch chapterCounter = new CountDownLatch(numChapters);
        AtomicInteger downloadedImages = new AtomicInteger(0);
        AtomicInteger totalImages = new AtomicInteger(0);
        new Thread(() -> {
            while (totalImages.get() == 0) {
                // wait
            }
            int progress = 0;
            System.out.println("Download progress:");
            System.out.print("0%");
            while(progress < 100) {
                progress = 100 * downloadedImages.get() / totalImages.get();
                int numDigits = Integer.toString(progress).length() + 1;
                String backspaces = "\b\b";
                if (numDigits > 2) {
                    backspaces = "\b\b\b";
                }

                System.out.print(backspaces + progress + "%");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("\nDone downloading");
        }).start();
        for (int i = firstChapter; i <= lastChapter; i++) {
            int finalI = i;
            new Thread(() -> {
                downloadChapterImages(mangaUrl, finalI, downloadedImages, totalImages);
                chapterCounter.countDown();
            }).start();
        }

        try {
            chapterCounter.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void downloadChapterImages(MangaUrl mangaUrl, int chapter, AtomicInteger downloadedImages, AtomicInteger totalImages) {
        new File("./out/" + mangaUrl.title + "/chapter-" + chapter).mkdir();
        Document page = getDocument(mangaUrl.href + "/chapter-" + chapter);
        if (page != null) {
            Element[] images = page.getElementsByTag("img").stream().filter(element -> !Arrays.asList("https://chapmanganato.com/themes/hm/images/gohome.png", "https://chapmanganato.com/themes/hm/images/logo-chap.png").contains(element.attr("src"))).toArray(Element[]::new);
            CountDownLatch pageCounter = new CountDownLatch(images.length);
            totalImages.addAndGet(images.length);
            int i = 0;
            CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();
            httpClient.start();
            for (Element image : images) {
                String url = image.attr("src");
                String[] urlParts = url.split("\\.");
                downloadFile(httpClient, pageCounter, url, String.format("out/%s/chapter-%s/%s.%s", mangaUrl.title, chapter, i, urlParts[urlParts.length - 1]), downloadedImages);
                i++;
            }
            try {
                pageCounter.await();
                httpClient.close();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void compileImagesIntoPDF(String title, int startChapter, int endChapter, String outputPath) {
        ConvertCmd command = new ConvertCmd();
        IMOperation op = new IMOperation();
        String root = "./out/" + title;
        for (int i = startChapter; i <= endChapter; i++) {
            File[] images = new File(root + "/chapter-" + i).listFiles();
            if (images != null) {
                Arrays.sort(images, Comparator.comparingInt(o -> Integer.parseInt(o.getName().split("\\.")[0])));
                for (File image : images) {
                    op.addImage(image.getPath());
                }
            }
        }
        op.addImage(outputPath);
        try {
            command.run(op);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IM4JavaException e) {
            e.printStackTrace();
        }
    }

    private static void cleanup(String title) {
        try {
            FileUtils.deleteDirectory(new File("./out/" + title));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
