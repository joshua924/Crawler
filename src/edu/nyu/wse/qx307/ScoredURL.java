package edu.nyu.wse.qx307;

import java.net.URL;

public class ScoredURL implements Comparable<ScoredURL> {
  private URL url;
  private double score;
  
  public ScoredURL(URL url, double score) {
    this.url = url;
    this.score = score;
  }
  
  @Override
  public int compareTo(ScoredURL o) {
    return score < o.score ? -1 : score > o.score ? 1 : 0;
  }
  
  public URL getURL() {
    return url;
  }
}
