package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.Arrays;
import java.util.Locale;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory.OpponentType;

public final class OpponentTypeConverter implements CommandLine.ITypeConverter<OpponentType> {
  @Override
  public OpponentType convert(String value) {
    try {
      return OpponentType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new CommandLine.TypeConversionException(
          "must be one of " + Arrays.toString(OpponentType.values()));
    }
  }
}
