package org.netkernelroc.gradle.apposite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepositoryCollection {
  private Repository repository;
  private long id;
  private String distPath;
  private List<RepositorySet> sets = new ArrayList<RepositorySet>();

  public RepositoryCollection(Repository repository, long id, String distPath) {
    this.repository = repository;
    this.id = id;
    this.distPath = distPath;
  }

  public Repository getRepository() {
    return repository;
  }

  public long getId() {
    return id;
  }

  public String getDistPath() {
    return distPath;
  }

  public boolean addSet(RepositorySet set) {
    return sets.add(set);
  }

  public List<RepositorySet> getSets() {
    return Collections.unmodifiableList(sets);
  }

  @Override
  public String toString() {
    String setString = "[";
    String nextSep = "";

    for (RepositorySet set : sets) {
      setString += nextSep + set;
      nextSep = ",";
    }

    setString += "]";

    return distPath + "(#" +id + ") " + setString;
  }

  public String getBasePath() {
    return repository.getUrlBase() + "netkernel/" + distPath;
  }
}
