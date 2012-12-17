package org.netkernelroc.gradle.apposite;

public enum DependencyType {
  DEPENDS(1, "depends"),
  SUGGESTS(2, "suggests"),
  RECOMMENDS(3, "recommends"),
  CONFLICTS(4, "conflicts"),
  REPLACES(5, "replaces");

  private final int id;
  private final String value;

  private DependencyType(int id, String value) {
    this.id = id;
    this.value = value;
  }

  public static DependencyType typeForValue(String value) {
    for (DependencyType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unrecognised dependency type '" + value + "'");
  }

  public int getId() {
    return id;
  }
}
