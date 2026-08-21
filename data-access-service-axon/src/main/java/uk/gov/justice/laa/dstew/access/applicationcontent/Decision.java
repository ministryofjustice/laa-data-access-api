package uk.gov.justice.laa.dstew.access.applicationcontent;

public enum Decision {
  GRANTED("GRANTED"),
  REFUSED("REFUSED");

  private String value;

  Decision(String granted) {
    this.value = granted;
  }

  String getValue() {
    return value;
  }
}
