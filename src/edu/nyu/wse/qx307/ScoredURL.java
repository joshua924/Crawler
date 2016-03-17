package edu.nyu.wse.qx307;

import java.net.URL;

public class ScoredURL implements Comparable<ScoredURL> {
  private URL url;
  private int score;
  
  public ScoredURL(URL url, int score) {
    this.url = url;
    this.score = score;
  }
  
  @Override
  public int compareTo(ScoredURL o) {
    return score - o.score;
  }
  
  @Override
  public String toString() {
    return url.toString() + ". Score = " + score;
    
  }
  
  public URL getURL() {
    return url;
  }
}
