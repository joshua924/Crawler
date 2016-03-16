package edu.nyu.wse.qx307;

// A minimal Web Crawler written in Java
// Usage: From command line 
//     java WebCrawler <URL> [N]
//  where URL is the url to start the crawl, and N (optional)
//  is the maximum number of pages to download.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class WebCrawler {
  public static final int SEARCH_LIMIT = 20; // Absolute max pages
  public static final boolean DEBUG = false;
  public static final String DISALLOW = "Disallow:";
  public static final int MAXSIZE = 20000; // Max size of file

  // URLs to be searched
  LinkedList<URL> newURLs;
  // Known URLs
  Map<URL, Integer> knownURLs;
  // max number of pages to download
  int maxPages;
  // queries
  Set<String> queries;

  // initializes data structures. argv is the command line arguments.
  public void initialize(String[] argv) {
    URL url;
    knownURLs = new HashMap<URL, Integer>();
    newURLs = new LinkedList<URL>();
    try {
      url = new URL(argv[0]);
    } catch (MalformedURLException e) {
      System.out.println("Invalid starting URL " + argv[0]);
      return;
    }
    knownURLs.put(url, 1);
    newURLs.add(url);
    System.out.println("Starting search: Initial URL " + url);
    maxPages = SEARCH_LIMIT;
    if (argv.length > 1) {
      int iPages = Integer.parseInt(argv[1]);
      if (iPages < maxPages)
        maxPages = iPages;
    }
    System.out.println("Maximum number of pages:" + maxPages);

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
      BufferedReader urlReader = new BufferedReader(
          new InputStreamReader(urlRobot.openStream()));
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
    for(String each : disallows) {
      if (each.trim().indexOf(strURL) == 0) {
        return false;
      }
    }
    return true;
  }
  
  public int score(URL url, String pageContent, String anchor, Set<String> queries) {
    if (queries == null || queries.isEmpty()) {
      return 0;
    }
    int score = 0;
    for (String query : queries) {
      if(anchor.indexOf(query) >= 0) {
        score += 50;
      }
    }
    if (any word in Query is a substring of M.URL)                /* 2 */ 
      return 40;
    U = set of different words in Query that occurs in P within five
        words of M (not counting HTML tags)
    V = set of different words in Query that occur in P;
    return 4*|U| + |V-U|
  }

  // adds new URL to the queue. Accept only new URL's that end in
  // htm or html. oldURL is the context, newURLString is the link
  // (either an absolute or a relative URL).

  public void addNewURL(URL oldURL, String newUrlString, String anchor) {
    System.out.println("URL String " + newUrlString + " with anchor " + anchor);
    URL url;
    try {
      url = new URL(oldURL, newUrlString);
      if (!knownURLs.containsKey(url)) {
        String filename = url.getFile();
        if (filename.endsWith("htm") || filename.endsWith("html")) {
          knownURLs.put(url, 1);
          
          newURLs.add(url);
          System.out.println("Found new URL " + url.toString());
        }
      }
    } catch (MalformedURLException e) {
      return;
    }
  }

  // Download contents of URL
  public String getPage(URL url) {
    try {
      URLConnection urlConnection = url.openConnection();
      System.out.println("Downloading " + url.toString());
      urlConnection.setAllowUserInteraction(false);

      BufferedReader urlReader = new BufferedReader(
          new InputStreamReader(url.openStream()));
      String content = "";
      String line = "";
      while((line = urlReader.readLine()) != null 
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
  public void processPage(URL url, String pageContent) {
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
            String anchor = pageContent.substring(iEndAngle + 1, 
                pageContent.indexOf("<", iEndAngle));
            addNewURL(url, newUrlString, anchor);
          }
        }
      }
      index = iEndAngle;
    }
  }

  // Top-level procedure. Keep popping a url off newURLs, download
  // it, and accumulate new URLs
  public void run(String[] argv) {
    initialize(argv);
    for (int i = 0; i < maxPages; i++) {
      URL url = newURLs.removeFirst();
      if (robotSafe(url)) {
        String page = getPage(url);
        if (page.length() != 0)
          processPage(url, page);
        if (newURLs.isEmpty())
          break;
      }
    }
    System.out.println("Search complete.");
  }

  public static void main(String[] argv) {
    WebCrawler wc = new WebCrawler();
    wc.run(argv);
    System.out.println(wc.knownURLs.size());
  }
}