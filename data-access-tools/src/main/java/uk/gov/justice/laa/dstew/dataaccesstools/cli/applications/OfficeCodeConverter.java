package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import picocli.CommandLine;

public final class OfficeCodeConverter implements CommandLine.ITypeConverter<String> {
  static final String PATTERN = "[0-9][A-Z][0-9]{3}[A-Z]";

  @Override
  public String convert(String value) {
    if (!value.matches(PATTERN)) {
      throw new CommandLine.TypeConversionException("must match " + PATTERN);
    }
    return value;
  }
}
