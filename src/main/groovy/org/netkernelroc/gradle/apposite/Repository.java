package org.netkernelroc.gradle.apposite;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Repository {
  private final long id;
  private final String name;
  private final String urlBase;
  private final boolean hasPublicKey;
  private final boolean trusted;
  private List<RepositoryCollection> collections = new ArrayList<RepositoryCollection>();

  public Repository(long id, String name, String urlBase, boolean hasPublicKey, boolean trusted) {
    this.id = id;
    this.name = name;
    this.urlBase = urlBase;
    this.hasPublicKey = hasPublicKey;
    this.trusted = trusted;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUrlBase() {
    return urlBase;
  }

  public boolean isHasPublicKey() {
    return hasPublicKey;
  }

  public boolean isTrusted() {
    return trusted;
  }

  public List<RepositoryCollection> getCollections() {
    return Collections.unmodifiableList(collections);
  }

  public boolean addCollection(RepositoryCollection collection) {
    return collections.add(collection);
  }

  @Override
  public String toString() {
    return name + " [#" + id + ", urlBase: " + urlBase + ", trusted: " + trusted + ", hasPublicKey: " + hasPublicKey + "]";
  }
}
