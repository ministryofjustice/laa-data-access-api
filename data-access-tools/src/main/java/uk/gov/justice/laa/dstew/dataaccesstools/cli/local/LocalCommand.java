package uk.gov.justice.laa.dstew.dataaccesstools.cli.local;

import picocli.CommandLine;

@CommandLine.Command(
    name = "local",
    mixinStandardHelpOptions = true,
    description = "Inspect the local Axon event store. Event payloads may contain sensitive data.",
    subcommands = {ApplicationEventsCommand.class, PriorAuthorityEventsCommand.class})
public final class LocalCommand {}
