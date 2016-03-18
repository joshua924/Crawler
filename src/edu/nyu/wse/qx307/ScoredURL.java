package edu.nyu.wse.qx307;

import java.net.URL;
import java.util.Date;

public class ScoredURL implements Comparable<ScoredURL> {
  private URL url;
  private int score;
  private Date date;
  
  public ScoredURL(URL url, int score) {
    this.url = url;
    this.score = score;
    date = new Date(System.currentTimeMillis());
  }
  
  @Override
  public int compareTo(ScoredURL o) {
    if (score != o.score) {
      return score - o.score;
    }
    return o.date.compareTo(date);
  }
  
  @Override
  public String toString() {
    return url.toString() + ". Score = " + score;
  }
  
  public URL getURL() {
    return url;
  }
  
  public int getScore() {
    return score;
  }
  
  public void incrementScore(int score) {
    this.score += score;
  }
}
