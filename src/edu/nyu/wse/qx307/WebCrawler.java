package edu.nyu.wse.qx307;

// A extended Web Crawler written in Java
// Usage: From command line 
//     java WebCrawler -u <URL> -q <queries> -docs <directory>
//     -m <MaxNumberOfPages> [-t if enable trace]
//  where URL is the url to start the crawling.

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;

public class WebCrawler {
  public static final int SEARCH_LIMIT = 20;
  public static final String DISALLOW = "Disallow:";
  public static final int MAXSIZE = 20000;

  // URLs to be searched
  PriorityQueue<ScoredURL> newURLs;
  Map<URL, Integer> knownURLs;
  Map<URL, ScoredURL> urlsInQueue;
  URL initialURL;
  Set<String> queries;
  File directory;
  int maxPages;
  boolean trace;

  private void parseArgs(String opt, String val) {
    if (opt.equals("-m")) {
      maxPages = Math.min(SEARCH_LIMIT, Integer.valueOf(val));
    } else if (opt.equals("-u")) {
      try {
        initialURL = new URL(val);
        knownURLs.put(initialURL, 1);
        ScoredURL e = new ScoredURL(initialURL, 0);
        newURLs.add(e);
        urlsInQueue.put(initialURL, e);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("URL invalid");
      }
    } else if (opt.equals("-docs")) {
      directory = new File(val);
    } else if (opt.equals("-t")) {
      trace = true;
    } else if (opt.equals("-q")) {
      queries = new HashSet<String>(Arrays.asList(val.split(" ")));
    } else {
      throw new IllegalArgumentException("Option invalid");
    }
  }

  // initializes data structures. argv is the command line arguments.
  public void initialize(String[] argv) {
    newURLs = new PriorityQueue<>(SEARCH_LIMIT, Collections.reverseOrder());
    knownURLs = new HashMap<URL, Integer>();
    urlsInQueue = new HashMap<URL, ScoredURL>();
    int i = 0;
    while (i < argv.length) {
      if (argv[i].equals("-q")) {
        String val = "";
        while (!argv[++i].startsWith("-")) {
          val += argv[i] + " ";
        }
        parseArgs("-q", val.trim());
      } else if (i == argv.length - 1) {
        parseArgs(argv[i], "");
        i += 2;
      } else {
        parseArgs(argv[i], argv[i + 1]);
        i += 2;
      }
    }
    if (trace) {
      System.out.println("Crawling for " + maxPages + " pages relevant to "
          + queries + " starting from " + initialURL + "\n");
    }

    /*
     * Behind a firewall set your proxy and port here!
     */
    Properties props = new Properties(System.getProperties());
    props.put("http.proxySet", "true");
    props.put("http.proxyHost", "webcache-cup");
    props.put("http.proxyPort", "8080");

    Properties newprops = new Properties(props);
    System.setProperties(newprops);
  }

  // Check that the robot exclusion protocol does not disallow
  // downloading url.
  public boolean robotSafe(URL url) {
    String strHost = url.getHost();

    // form URL of the robots.txt file
    String strRobot = "http://" + strHost + "/robots.txt";
    URL urlRobot;
    try {
      urlRobot = new URL(strRobot);
    } catch (MalformedURLException e) {
      // something weird is happening, so don't trust it
      return false;
    }

    String strCommands = "";
    try {
      BufferedReader urlReader = new BufferedReader(new InputStreamReader(
          urlRobot.openStream()));
      // read in entire file
      String line = "";
      while ((line = urlReader.readLine()) != null) {
        strCommands += line;
      }
      urlReader.close();
    } catch (IOException e) {
      return true;
    }

    // assume that this robots.txt refers to us and
    // search for "Disallow:" commands.
    String strURL = url.getFile();
    String[] disallows = strCommands.split(DISALLOW);
    for (String each : disallows) {
      if (each.trim().indexOf(strURL) == 0) {
        return false;
      }
    }
    return true;
  }

  private List<String> removeTagItems(List<String> words) {
    List<String> res = new ArrayList<String>();
    for (String word : words) {
      if (word.startsWith(">")) {
        word = word.substring(1);
      } else if (word.length() > 1 && word.endsWith("<")) {
        word = word.substring(0, word.length() - 1);
      }
      if (word.matches("[A-Za-z0-9.]*") && !word.equals("href")) {
        res.add(word);
      }
    }
    return res;
  }

  public int score(String newUrlString, String lcPage, String anchor,
      Set<String> queries) {
    if (queries == null || queries.isEmpty()) {
      return 0;
    }
    Set<String> fourPointSet = new HashSet<String>();
    int score = 0;
    for (String query : queries) {
      if (anchor.indexOf(query) >= 0) {
        score += 50;
        // add it to the four point set to avoid counting again
        fourPointSet.add(query);
      }
      if (newUrlString.toLowerCase().indexOf(query) >= 0) {
        score += 40;
        fourPointSet.add(query);
      }
    }

    // if any of the conditions above is met, just return
    if (score != 0) {
      return score;
    }

    // remove html tags
    List<String> words = Arrays.asList(lcPage.replaceAll("[/=\",()]", " ")
        .replaceAll(" +", " ").split(" "));
    words = removeTagItems(words);
    // find any query within 5 words from the link
    int iURL = words.indexOf(newUrlString.toLowerCase());
    for (int i = Math.max(0, iURL - 5); i < Math.min(iURL + 6, words.size()); i++) {
      String word = words.get(i);
      if (queries.contains(word) && !fourPointSet.contains(word)) {
        fourPointSet.add(word);
        score += 4;
      }
    }

    // any query in the content
    Set<String> onePointSet = new HashSet<String>(words);
    onePointSet.retainAll(queries);
    onePointSet.removeAll(fourPointSet);
    return score + onePointSet.size();
  }

  // adds new URL to the queue. Accept only new URL's that end in
  // htm or html. oldURL is the context, newURLString is the link
  // (either an absolute or a relative URL).
  public void addNewURL(URL oldURL, String newUrlString, String anchor,
      int score) {
    URL url;
    try {
      url = new URL(oldURL, newUrlString);
      if (!knownURLs.containsKey(url)) {
        String filename = url.getFile();
        if (filename.endsWith("htm") || filename.endsWith("html")) {
          knownURLs.put(url, 1);
          ScoredURL scoredURL = new ScoredURL(url, score);
          newURLs.add(scoredURL);
          urlsInQueue.put(url, scoredURL);
          if (trace) {
            System.out.println("Adding to queue: " + scoredURL);
          }
        }
      } else {
        if (urlsInQueue.containsKey(url) && score > 0) {
          ScoredURL originalURL = urlsInQueue.get(url);
          newURLs.remove(originalURL);
          originalURL.incrementScore(score);
          newURLs.add(originalURL);
          if (trace) {
            System.out.println("Adding " + score + " to score of " + url);
          }
        }
      }
    } catch (MalformedURLException e) {
      return;
    }
  }

  // Download contents of URL
  public String getPage(ScoredURL scoredURL) {
    URL url = scoredURL.getURL();
    try {
      URLConnection urlConnection = url.openConnection();
      if (trace) {
        System.out.println("Downloading " + scoredURL);
      }
      urlConnection.setAllowUserInteraction(false);

      BufferedReader urlReader = new BufferedReader(new InputStreamReader(
          url.openStream(), Charset.forName("UTF-8")));
      String content = "";
      String line = "";
      while ((line = urlReader.readLine()) != null
          && content.length() + line.length() <= MAXSIZE) {
        content += line;
      }
      return content;
    } catch (IOException e) {
      System.out.println("ERROR: couldn't open URL ");
      return "";
    }
  }

  // Go through page finding links to URLs. A link is signalled
  // by <a href=" ... It ends with a close angle bracket, preceded
  // by a close quote, possibly preceded by a hatch mark (marking a
  // fragment, an internal page marker)
  public void storeAndProcessPage(URL url, String pageContent, int i) {
    if (!directory.isDirectory()) {
      directory.mkdir();
    }
    try {
      String urlStr = url.toString();
      String fileName = urlStr.substring(urlStr.lastIndexOf("/") + 1);
      File file = new File(directory, fileName);
      if (!file.exists()) {
        file.createNewFile();
      }
      PrintWriter pw = new PrintWriter(file);
      pw.write(pageContent);
      pw.flush();
      pw.close();
      if (trace) {
        System.out.println("Received " + url);
      }
    } catch (IOException e) {
      System.out.println("ERROR: couldn't store URL ");
    }
    if (i == maxPages - 1) {
      return;
    }
    String lcPage = pageContent.toLowerCase(); // Page in lower case
    int index = 0; // position in page
    int iEndAngle, ihref, iURL, iCloseQuote, iHatchMark, iEnd;
    while ((index = lcPage.indexOf("<a", index)) != -1) {
      iEndAngle = lcPage.indexOf(">", index);
      ihref = lcPage.indexOf("href", index);
      if (ihref != -1) {
        iURL = lcPage.indexOf("\"", ihref) + 1;
        if ((iURL != -1) && (iEndAngle != -1) && (iURL < iEndAngle)) {
          iCloseQuote = lcPage.indexOf("\"", iURL);
          iHatchMark = lcPage.indexOf("#", iURL);
          if ((iCloseQuote != -1) && (iCloseQuote < iEndAngle)) {
            iEnd = iCloseQuote;
            if ((iHatchMark != -1) && (iHatchMark < iCloseQuote))
              iEnd = iHatchMark;
            String newUrlString = pageContent.substring(iURL, iEnd);
            String anchor = lcPage.substring(iEndAngle + 1,
                lcPage.indexOf("<", iEndAngle));
            int score = score(newUrlString, lcPage, anchor, queries);
            addNewURL(url, newUrlString, anchor, score);
          }
        }
      }
      index = iEndAngle;
    }
    System.out.println();
  }

  // Top-level procedure. Keep popping a url off newURLs, download
  // it, and accumulate new URLs
  public void crawl() {
    for (int i = 0; i < maxPages; i++) {
      URL url = newURLs.poll().getURL();
      ScoredURL removed = urlsInQueue.remove(url);
      if (robotSafe(url)) {
        String pageContent = getPage(removed);
        if (pageContent.length() != 0) {
          storeAndProcessPage(url, pageContent, i);
        }
        if (newURLs.isEmpty()) {
          break;
        }
      }
    }
    System.out.println("\nSearch complete.");
  }

  public static void main(String[] argv) {
    WebCrawler wc = new WebCrawler();
    wc.initialize(argv);
    wc.crawl();
  }
}