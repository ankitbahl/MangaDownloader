MangaDownloader is a Java project that downloads manga (sourced from manganelo.com). It downloads all the images and creates pdfs of the compiled volumes. 

#Dependencies

- Java
- ImageMagick 7.x
- maven to compile

#Running
```
mvn install && mvn package

# for interactive compile 
java -jar target/manga-downloader-0.1.0.jar -i

# to just search for manga 
java -jar target/manga-downloader-0.1.0.jar -s <searchTerm>

# to compile with params in command line
java -jar target/manga-downloader-0.1.0.jar -x <mangaUrl> <firstChapter>,<lastChapter> <outputPath>
```

Large volumes will not work on computers with low memory
