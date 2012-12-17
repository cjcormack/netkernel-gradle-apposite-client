package org.netkernelroc.gradle.apposite;

public enum DependencyEquality {
  LESS_THAN(1, "lt"),
  LESS_THAN_OR_EQUAL(2, "lte"),
  EQUAL(3, "e"),
  GREATER_THAN_OR_EQUAL(4, "gte"),
  GREATER_THAN(5, "gt");

  private final int id;
  private final String value;

  private DependencyEquality(int id, String value) {
    this.id = id;
    this.value = value;
  }

  public static DependencyEquality equalityForValue(String value) {
    for (DependencyEquality type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unrecognised dependency equality '" + value + "'");
  }

  public int getId() {
    return id;
  }
}
